"""Site-specific search adapter — builds search URLs and extracts article links from search result pages."""

import re
from dataclasses import dataclass, field
from urllib.parse import quote, urljoin, urlparse, urlunparse

import httpx
from bs4 import BeautifulSoup

from config.settings import CrawlerSettings, settings
from exception.crawler_exception import CrawlerException
from model.news_discover_result import DiscoveredNewsLink


@dataclass(frozen=True)
class SiteSearchProfile:
    name: str
    source_type: str
    """Search URL template with {keyword} placeholder."""
    search_template: str
    domains: tuple[str, ...]
    """Regex patterns that article detail pages match."""
    article_patterns: tuple[re.Pattern[str], ...]
    """HTTP method: 'GET' or 'POST'."""
    search_method: str = field(default="GET")
    """POST body template with {keyword} placeholder (only used when search_method='POST')."""
    post_data_template: str | None = field(default=None)


SITE_SEARCH_PROFILES: tuple[SiteSearchProfile, ...] = (
    SiteSearchProfile(
        name="新浪新闻",
        source_type="portal",
        search_template="https://search.sina.com.cn/?q={keyword}&range=all&c=news",
        domains=("search.sina.com.cn", "sina.com.cn"),
        article_patterns=(
            re.compile(r"^https?://news\.sina\.com\.cn/[a-z]/\d{4}-\d{2}-\d{2}/doc-[^/?#]+\.shtml$"),
            re.compile(r"^https?://[a-z]+\.sina\.com\.cn/[a-z/]+/\d{4}-\d{2}-\d{2}/doc-[^/?#]+\.shtml$"),
            re.compile(r"^https?://k\.sina\.com\.cn/article_\d+_[a-f0-9]+\.html$"),
            re.compile(r"^https?://k\.sina\.com\.cn/article_\d+_[a-f0-9]+\.html\?.*$"),
        ),
    ),
    SiteSearchProfile(
        name="中国新闻网",
        source_type="official",
        search_template="https://sou.chinanews.com.cn/search.do?q={keyword}",
        domains=("sou.chinanews.com.cn", "chinanews.com.cn", "www.chinanews.com.cn"),
        article_patterns=(
            re.compile(r"^https?://(?:www\.)?chinanews\.com\.cn/[\w-]+/\d{4}/\d{2}-\d{2}/\d+\.shtml$"),
        ),
    ),
    SiteSearchProfile(
        name="澎湃新闻",
        source_type="original",
        search_template="https://www.thepaper.cn/search?keyword={keyword}",
        domains=("www.thepaper.cn", "thepaper.cn"),
        article_patterns=(
            re.compile(r"^https?://(?:www\.)?thepaper\.cn/newsDetail_forward_\d+$"),
        ),
    ),
    SiteSearchProfile(
        name="界面新闻",
        source_type="original",
        search_template="https://a.jiemian.com/index.php?m=search&a=index",
        domains=("www.jiemian.com", "jiemian.com", "a.jiemian.com"),
        article_patterns=(
            re.compile(r"^https?://(?:www\.)?jiemian\.com/article/\d+\.html$"),
        ),
        search_method="POST",
        post_data_template="msg={keyword}",
    ),
    # 人民网搜索入口需要验证码，暂不可用。后续可通过官方搜索 API 或 Cookie 方式接入。
    # SiteSearchProfile(
    #     name="人民网",
    #     source_type="official",
    #     search_template="http://search.people.com.cn/rmw/GB/bkzzsearch/djsearch.jsp?q={keyword}",
    #     domains=("search.people.com.cn", "people.com.cn"),
    #     article_patterns=(
    #         re.compile(r"^https?://[\w.-]*people\.com\.cn/n1/\d{4}/\d{4}/c\d+-\d+\.html$"),
    #     ),
    # ),
)


class TopicSearchAdapter:
    def __init__(
        self,
        client: httpx.Client | None = None,
        crawler_settings: CrawlerSettings = settings,
    ) -> None:
        self._settings = crawler_settings
        self._client = httpx.Client(
            follow_redirects=True,
            timeout=crawler_settings.request_timeout_seconds,
            headers={"User-Agent": crawler_settings.user_agent},
        )

    def search(
        self,
        keyword: str,
        profile: SiteSearchProfile,
        limit: int,
    ) -> list[DiscoveredNewsLink]:
        """Search a news source for articles matching the keyword."""
        encoded_keyword = quote(keyword)
        search_url = profile.search_template.format(keyword=encoded_keyword)

        # Try httpx first
        try:
            if profile.search_method == "POST":
                post_data = profile.post_data_template.format(keyword=encoded_keyword)
                response = self._client.post(
                    search_url,
                    content=post_data,
                    headers={"Content-Type": "application/x-www-form-urlencoded"},
                )
            else:
                response = self._client.get(search_url)
            response.raise_for_status()
            html = response.text
            final_url = str(response.url)
        except httpx.TimeoutException as exc:
            raise CrawlerException("搜索请求超时") from exc
        except httpx.HTTPStatusError as exc:
            raise CrawlerException(f"搜索请求返回异常状态码: {exc.response.status_code}") from exc
        except httpx.RequestError as exc:
            raise CrawlerException(f"无法访问搜索页面: {exc}") from exc

        links = self._extract_article_links(html, final_url, profile, limit)

        # Fallback: if static fetch found nothing, try Playwright
        if not links:
            try:
                js_html = self._render_search_with_browser(
                    search_url,
                    post_data=post_data if profile.search_method == "POST" else None,
                )
                if js_html:
                    links = self._extract_article_links(js_html, search_url, profile, limit)
            except Exception:
                pass

        return links

    @staticmethod
    def _render_search_with_browser(url: str, post_data: str | None = None) -> str:
        import subprocess
        import sys
        import json as _json

        if post_data:
            script = rf"""
import sys, json
from playwright.sync_api import sync_playwright
url = sys.argv[1]
data = sys.argv[2]
with sync_playwright() as p:
    browser = p.chromium.launch(headless=True, args=['--no-sandbox'])
    page = browser.new_page()
    try:
        page.goto(url, wait_until='domcontentloaded', timeout=20000)
        page.fill('input[type="text"]', data.split('=', 1)[1])
        page.click('button[type="submit"], input[type="submit"], .search-btn, form button')
        page.wait_for_timeout(3000)
        html = page.content()
    finally:
        browser.close()
try:
    print(json.dumps({{"ok": True, "html": html}}))
except Exception as e:
    print(json.dumps({{"ok": False, "error": str(e)}}))
            """
        else:
            script = r"""
import sys, json
from playwright.sync_api import sync_playwright
url = sys.argv[1]
with sync_playwright() as p:
    browser = p.chromium.launch(headless=True, args=['--no-sandbox'])
    page = browser.new_page()
    try:
        page.goto(url, wait_until='domcontentloaded', timeout=20000)
        page.wait_for_timeout(3000)
        html = page.content()
    finally:
        browser.close()
try:
    print(json.dumps({"ok": True, "html": html}))
except Exception as e:
    print(json.dumps({"ok": False, "error": str(e)}))
            """

        cmd_args = [sys.executable, "-c", script, url]
        if post_data:
            cmd_args.append(post_data)

        try:
            result = subprocess.run(
                cmd_args,
                capture_output=True,
                text=True,
                timeout=35,
            )
        except subprocess.TimeoutExpired:
            return ""

        if result.returncode != 0:
            return ""
        data = _json.loads(result.stdout.strip())
        if not data.get("ok"):
            return ""
        return data["html"]

    @staticmethod
    def _extract_article_links(
        html: str,
        base_url: str,
        profile: SiteSearchProfile,
        limit: int,
    ) -> list[DiscoveredNewsLink]:
        soup = BeautifulSoup(html, "lxml")
        seen: set[str] = set()
        links: list[DiscoveredNewsLink] = []

        for tag in soup.find_all("a", href=True):
            href = tag.get("href")
            if not href:
                continue
            absolute_url = urljoin(base_url, href)
            normalized = TopicSearchAdapter._normalize_url(absolute_url)
            if normalized in seen:
                continue
            # Only pick links matching the site's article patterns
            if not any(p.match(normalized) for p in profile.article_patterns):
                continue
            seen.add(normalized)
            title = tag.get_text(" ", strip=True) or None
            links.append(DiscoveredNewsLink(title=title, url=normalized))
            if len(links) >= limit:
                break

        return links

    @staticmethod
    def match_profile(source_url: str) -> SiteSearchProfile | None:
        """Try to match a source URL to a known search profile."""
        host = urlparse(source_url).netloc.lower().removeprefix("www.")
        for profile in SITE_SEARCH_PROFILES:
            clean_domains = tuple(d.removeprefix("www.") for d in profile.domains)
            if host in clean_domains or any(d in host for d in clean_domains):
                return profile
        return None

    @staticmethod
    def _normalize_url(url: str) -> str:
        parsed = urlparse(url)
        return urlunparse(
            (parsed.scheme, parsed.netloc.lower(), parsed.path, "", parsed.query, "")
        )
