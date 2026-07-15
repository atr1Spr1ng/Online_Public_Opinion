"""Site-specific search adapter — builds search URLs and extracts article links from search result pages.

For JS-rendered search engines (Sina, ThePaper) and AJAX-paginated ones (ChinaNews),
uses Playwright to click through result pages.
"""

import json as _json
import re
import subprocess
import sys
from dataclasses import dataclass, field
from urllib.parse import quote, urljoin, urlparse, urlunparse

import httpx
from bs4 import BeautifulSoup

from src.config.settings import CrawlerSettings, settings
from src.exception.crawler_exception import CrawlerException
from src.model.news_discover_result import DiscoveredNewsLink


@dataclass(frozen=True)
class SiteSearchProfile:
    name: str
    source_type: str
    search_template: str
    domains: tuple[str, ...]
    article_patterns: tuple[re.Pattern[str], ...]
    search_method: str = field(default="GET")
    post_data_template: str | None = field(default=None)
    # ── Pagination ──
    """'static': URL/POST param works. 'playwright': needs JS click-through. 'feed': use site API."""
    pagination_type: str = field(default="static")
    """URL/POST parameter name for static pagination."""
    page_param_name: str = field(default="page")
    """First page number."""
    page_start: int = field(default=1)
    """Safety cap on pages."""
    max_pages: int = field(default=10)
    """CSS selector for 'next page' button (playwright sources only)."""
    next_selector: str = field(default=".next, a:has-text('下一页'), a:has-text('>'), [rel='next']")
    # ── Form-based search (for sites without keyword-in-URL) ──
    """'url': keyword is in the URL template. 'form': keyword must be typed into a search box."""
    search_interaction: str = field(default="url")
    """CSS selector for search input box (form interaction only)."""
    search_input_selector: str = field(default="")
    """CSS selector for search submit button (form interaction only)."""
    search_button_selector: str = field(default="")
    """Wait time in ms after form submission before extracting results."""
    search_submit_wait_ms: int = field(default=4000)


SINA_FEED_API = "https://feed.mix.sina.com.cn/api/roll/get"
SINA_FEED_PARAMS = "pageid=153&lid=2509&num=50"

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
        pagination_type="playwright",
        max_pages=10,
        next_selector=".ant-pagination-next",
    ),
    SiteSearchProfile(
        name="中国新闻网",
        source_type="official",
        search_template="https://sou.chinanews.com.cn/search.do?q={keyword}",
        domains=("sou.chinanews.com.cn", "chinanews.com.cn", "www.chinanews.com.cn"),
        article_patterns=(
            re.compile(r"^https?://(?:www\.)?chinanews\.com\.cn/[\w-]+/\d{4}/\d{2}-\d{2}/\d+\.shtml$"),
        ),
        pagination_type="playwright",
        max_pages=20,
    ),
    SiteSearchProfile(
        name="澎湃新闻",
        source_type="original",
        search_template="https://www.thepaper.cn/search?keyword={keyword}",
        domains=("www.thepaper.cn", "thepaper.cn"),
        article_patterns=(
            re.compile(r"^https?://(?:www\.)?thepaper\.cn/newsDetail_forward_\d+$"),
        ),
        pagination_type="playwright",
        max_pages=20,
    ),
    SiteSearchProfile(
        name="界面新闻",
        source_type="original",
        search_template="https://a.jiemian.com/index.php?m=search&a=index",
        domains=("www.jiemian.com", "jiemian.com", "a.jiemian.com"),
        article_patterns=(
            re.compile(r"^https?://(?:www\.)?jiemian\.com/article/\d+\.html$"),
        ),
        pagination_type="playwright",
        max_pages=5,
        search_interaction="form",
        search_input_selector=".search-text",
        search_button_selector=".search-btn",
        next_selector=".list-pager a:has-text('►')",
    ),
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

    # ── Main search entry ──────────────────────────────────────────

    def search(
        self,
        keyword: str,
        profile: SiteSearchProfile,
        limit: int,
    ) -> list[DiscoveredNewsLink]:
        """Search a news source, using the appropriate strategy per source type."""
        if profile.pagination_type == "playwright":
            return self._search_playwright_multi_page(keyword, profile, limit)
        else:
            return self._search_static_paginated(keyword, profile, limit)

    # ── Sina: Feed API ─────────────────────────────────────────────

    def search_sina_feed_all(
        self,
        limit: int,
        max_pages: int = 10,
    ) -> list[DiscoveredNewsLink]:
        """Pull recent articles from Sina roll API without keyword filtering.

        This is a supplementary channel — it returns cross-day articles that
        the relevance filter (_is_relevant) will narrow down later.
        """
        from concurrent.futures import ThreadPoolExecutor, as_completed
        from threading import Lock

        def _fetch_page(page: int) -> list[DiscoveredNewsLink]:
            feed_url = f"{SINA_FEED_API}?{SINA_FEED_PARAMS}&page={page}"
            try:
                resp = self._client.get(feed_url)
                resp.raise_for_status()
                data = _json.loads(resp.text)
            except Exception:
                return []

            items = data.get("result", {}).get("data", [])
            links: list[DiscoveredNewsLink] = []
            for item in items:
                title = item.get("title", "")
                url = item.get("url", "")
                if not title or not url:
                    continue
                normalized = self._normalize_url(url)
                links.append(DiscoveredNewsLink(title=title, url=normalized))
            return links

        all_links: list[DiscoveredNewsLink] = []
        seen_urls: set[str] = set()
        seen_lock = Lock()

        with ThreadPoolExecutor(max_workers=4) as pool:
            futures = {pool.submit(_fetch_page, p): p for p in range(1, max_pages + 1)}
            for future in as_completed(futures):
                if len(all_links) >= limit:
                    break
                for link in future.result():
                    with seen_lock:
                        if link.url not in seen_urls:
                            seen_urls.add(link.url)
                            all_links.append(link)
                            if len(all_links) >= limit:
                                break

        return all_links[:limit]

    def search_sina_feed(
        self,
        keyword: str,
        limit: int,
        max_pages: int = 20,
    ) -> list[DiscoveredNewsLink]:
        """Pull from Sina public roll API, filter by keyword in title."""
        from concurrent.futures import ThreadPoolExecutor, as_completed
        from threading import Lock

        def _fetch_page(page: int) -> list[DiscoveredNewsLink]:
            feed_url = f"{SINA_FEED_API}?{SINA_FEED_PARAMS}&page={page}"
            try:
                resp = self._client.get(feed_url)
                resp.raise_for_status()
                data = _json.loads(resp.text)
            except Exception:
                return []

            items = data.get("result", {}).get("data", [])
            links: list[DiscoveredNewsLink] = []
            for item in items:
                title = item.get("title", "")
                url = item.get("url", "")
                if not title or not url:
                    continue
                normalized = self._normalize_url(url)
                links.append(DiscoveredNewsLink(title=title, url=normalized))
            return links

        all_links: list[DiscoveredNewsLink] = []
        seen_urls: set[str] = set()
        seen_lock = Lock()
        keyword_lower = keyword.lower()

        with ThreadPoolExecutor(max_workers=4) as pool:
            futures = {pool.submit(_fetch_page, p): p for p in range(1, max_pages + 1)}
            for future in as_completed(futures):
                if len(all_links) >= limit:
                    break
                for link in future.result():
                    if keyword_lower not in link.title.lower():
                        continue
                    with seen_lock:
                        if link.url not in seen_urls:
                            seen_urls.add(link.url)
                            all_links.append(link)
                            if len(all_links) >= limit:
                                break

        return all_links[:limit]

    # ── Static pagination (Jiemian) ─────────────────────────────────

    def _search_static_paginated(
        self,
        keyword: str,
        profile: SiteSearchProfile,
        limit: int,
    ) -> list[DiscoveredNewsLink]:
        """Loop pages via URL/POST parameter."""
        encoded_keyword = quote(keyword)
        base_url = profile.search_template.format(keyword=encoded_keyword)
        base_post_data: str | None = None
        if profile.search_method == "POST" and profile.post_data_template:
            base_post_data = profile.post_data_template.format(keyword=encoded_keyword)

        all_links: list[DiscoveredNewsLink] = []
        seen_urls: set[str] = set()

        for page in range(profile.page_start, profile.max_pages + 1):
            if len(all_links) >= limit:
                break

            page_url, page_post = self._paginate_static(base_url, base_post_data, profile, page)
            html, final_url = self._fetch_static(page_url, page_post, profile.search_method)
            links = self._extract_article_links(html, final_url, profile, limit - len(all_links))

            new_count = 0
            for link in links:
                if link.url not in seen_urls:
                    seen_urls.add(link.url)
                    all_links.append(link)
                    new_count += 1

            if new_count == 0:
                break

        return all_links[:limit]

    @staticmethod
    def _paginate_static(
        base_url: str, base_post: str | None, profile: SiteSearchProfile, page: int
    ) -> tuple[str, str | None]:
        """Append page parameter to URL or POST body."""
        page_url = f"{base_url}&{profile.page_param_name}={page}"
        page_post = base_post
        if base_post:
            page_post = f"{base_post}&{profile.page_param_name}={page}"
        return page_url, page_post

    def _fetch_static(self, url: str, post_data: str | None, method: str) -> tuple[str, str]:
        """Single static HTTP fetch. Returns (html, final_url)."""
        try:
            if method == "POST":
                resp = self._client.post(
                    url, content=post_data,
                    headers={"Content-Type": "application/x-www-form-urlencoded"},
                )
            else:
                resp = self._client.get(url)
            resp.raise_for_status()
            return resp.text, str(resp.url)
        except Exception:
            return "", url

    # ── Playwright multi-page ──────────────────────────────────────

    def _search_playwright_multi_page(
        self,
        keyword: str,
        profile: SiteSearchProfile,
        limit: int,
    ) -> list[DiscoveredNewsLink]:
        """Render search results via Playwright, clicking through pages."""
        encoded_keyword = quote(keyword)
        search_url = profile.search_template.format(keyword=encoded_keyword)
        html_pages = self._render_multi_page(
            search_url, profile.max_pages, profile.next_selector,
            interaction=profile.search_interaction,
            keyword=keyword,
            input_selector=profile.search_input_selector,
            button_selector=profile.search_button_selector,
            submit_wait_ms=profile.search_submit_wait_ms,
        )

        all_links: list[DiscoveredNewsLink] = []
        seen_urls: set[str] = set()

        for html in html_pages:
            if len(all_links) >= limit:
                break
            links = self._extract_article_links(html, search_url, profile, limit - len(all_links))
            for link in links:
                if link.url not in seen_urls:
                    seen_urls.add(link.url)
                    all_links.append(link)

        return all_links[:limit]

    @staticmethod
    def _render_multi_page(
        url: str,
        max_pages: int,
        next_selector: str,
        interaction: str = "url",
        keyword: str = "",
        input_selector: str = "",
        button_selector: str = "",
        submit_wait_ms: int = 4000,
    ) -> list[str]:
        """Launch Playwright, navigate search results, click 'next', return HTML per page."""
        script = r"""
import sys, json
from playwright.sync_api import sync_playwright

url = sys.argv[1]
max_pages = int(sys.argv[2])
next_sel = sys.argv[3]
interaction = sys.argv[4]
keyword = sys.argv[5]
input_sel = sys.argv[6]
button_sel = sys.argv[7]
submit_wait_ms = int(sys.argv[8])

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True, args=['--no-sandbox'])
    page = browser.new_page()
    all_html = []
    try:
        page.goto(url, wait_until='domcontentloaded', timeout=20000)
        page.wait_for_timeout(3000)

        # ── Form-based search ──
        if interaction == 'form':
            page.locator(input_sel).first.fill(keyword)
            page.wait_for_timeout(500)
            page.locator(button_sel).first.click()
            page.wait_for_timeout(submit_wait_ms)

        for i in range(max_pages):
            html = page.content()
            all_html.append(html)

            if i == max_pages - 1:
                break

            selectors = [s.strip() for s in next_sel.split(',') if s.strip()]
            clicked = False
            for sel in selectors:
                try:
                    el = page.locator(sel).first
                    if el and el.is_visible():
                        el.click()
                        page.wait_for_timeout(2500)
                        clicked = True
                        break
                except:
                    continue

            if not clicked:
                break
    finally:
        browser.close()

try:
    print(json.dumps({"ok": True, "htmls": all_html}))
except Exception as e:
    print(json.dumps({"ok": False, "error": str(e)}))
"""
        try:
            result = subprocess.run(
                [sys.executable, "-c", script, url, str(max_pages), next_selector,
                 interaction, keyword, input_selector, button_selector, str(submit_wait_ms)],
                capture_output=True, text=True, timeout=120,
            )
        except subprocess.TimeoutExpired:
            return []

        if result.returncode != 0:
            return []
        try:
            data = _json.loads(result.stdout.strip())
        except _json.JSONDecodeError:
            return []
        if not data.get("ok"):
            return []
        return data.get("htmls", [])

    # ── Link extraction ────────────────────────────────────────────

    @staticmethod
    def _extract_article_links(
        html: str,
        base_url: str,
        profile: SiteSearchProfile,
        limit: int,
    ) -> list[DiscoveredNewsLink]:
        soup = BeautifulSoup(html, "lxml")
        seen: dict[str, DiscoveredNewsLink] = {}
        links: list[DiscoveredNewsLink] = []

        for tag in soup.find_all("a", href=True):
            href = tag.get("href")
            if not href:
                continue
            absolute_url = urljoin(base_url, href)
            normalized = TopicSearchAdapter._normalize_url(absolute_url)
            if not any(p.match(normalized) for p in profile.article_patterns):
                continue
            # get_text() may be empty for image-only links; fall back to title attr
            title = tag.get_text(" ", strip=True) or tag.get("title") or None

            if normalized in seen:
                # Update title if the first occurrence was an image link without text
                if title and not seen[normalized].title:
                    seen[normalized].title = title
                continue

            link = DiscoveredNewsLink(title=title, url=normalized)
            seen[normalized] = link
            links.append(link)
            if len(links) >= limit:
                break

        return links

    @staticmethod
    def match_profile(source_url: str) -> SiteSearchProfile | None:
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
