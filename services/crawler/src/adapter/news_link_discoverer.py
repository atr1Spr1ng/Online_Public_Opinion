from dataclasses import dataclass
import re
from urllib.parse import urljoin, urlparse, urlunparse

import httpx
from bs4 import BeautifulSoup

from src.config.settings import CrawlerSettings, settings
from src.exception.crawler_exception import CrawlerException
from src.model.news_discover_result import DiscoveredNewsLink, NewsDiscoverResult


SINA_FEED_API = "https://feed.mix.sina.com.cn/api/roll/get"
SINA_FEED_PARAMS = "pageid=153&lid=2509&num=50"


@dataclass(frozen=True)
class SourceProfile:
    name: str
    source_type: str
    domains: tuple[str, ...]
    patterns: tuple[re.Pattern[str], ...]


SOURCE_PROFILES: tuple[SourceProfile, ...] = (
    SourceProfile(
        name="新浪新闻",
        source_type="portal",
        domains=("sina.com.cn", "sina.cn"),
        patterns=(
            re.compile(r"^https?://news\.sina\.com\.cn/[a-z]/\d{4}-\d{2}-\d{2}/doc-[^/?#]+\.shtml$"),
            re.compile(r"^https?://[\w.-]*sina\.com\.cn/[a-z/]+/\d{4}-\d{2}-\d{2}/doc-[^/?#]+\.shtml$"),
            re.compile(r"^https?://k\.sina\.com\.cn/article_\d+_[a-f0-9]+\.html(?:\?.*)?$"),
        ),
    ),
    SourceProfile(
        name="中国新闻网",
        source_type="official",
        domains=("www.chinanews.com.cn", "chinanews.com.cn"),
        patterns=(
            re.compile(r"^https?://(?:www\.)?chinanews\.com\.cn/(?!shipin/).*\d{4}/\d{2}-\d{2}/.*\.shtml$"),
        ),
    ),
    SourceProfile(
        name="澎湃新闻",
        source_type="original",
        domains=("www.thepaper.cn", "thepaper.cn"),
        patterns=(
            re.compile(r"^https?://(?:www\.)?thepaper\.cn/newsDetail(_forward)?_\d+$"),
        ),
    ),
    SourceProfile(
        name="界面新闻",
        source_type="original",
        domains=("www.jiemian.com", "jiemian.com"),
        patterns=(
            re.compile(r"^https?://(?:www\.)?jiemian\.com/article/\d+\.html$"),
        ),
    ),
    SourceProfile(
        name="新华网",
        source_type="official",
        domains=("www.news.cn", "news.cn", "xinhuanet.com", "www.xinhuanet.com"),
        patterns=(
            re.compile(r"^https?://(?:www\.)?(?:news\.cn|xinhuanet\.com)/(?:[\w-]+/)?\d{8}/[0-9a-f]{32}/c\.html$"),
        ),
    ),
    SourceProfile(
        name="人民网",
        source_type="official",
        domains=("people.com.cn", "www.people.com.cn"),
        patterns=(
            re.compile(r"^https?://[\w.-]*people\.com\.cn/n1/\d{4}/\d{4}/c\d+-\d+\.html$"),
        ),
    ),
    SourceProfile(
        name="央视新闻",
        source_type="official",
        domains=("news.cctv.com", "cctv.com", "www.cctv.com"),
        patterns=(
            re.compile(r"^https?://[\w.-]*cctv\.com/\d{4}/\d{2}/\d{2}/ARTI\w+\.shtml$"),
        ),
    ),
    SourceProfile(
        name="环球网",
        source_type="portal",
        domains=("huanqiu.com", "www.huanqiu.com"),
        patterns=(
            re.compile(r"^https?://[\w.-]*huanqiu\.com/(?:article|world|china|mil|finance|tech|ent|sports)/.*"),
        ),
    ),
    SourceProfile(
        name="中国青年网",
        source_type="official",
        domains=("youth.cn", "www.youth.cn", "news.youth.cn"),
        patterns=(
            re.compile(r"^https?://[\w.-]*youth\.cn/.*\.(?:htm|html|shtml)$"),
        ),
    ),
)


class NewsLinkDiscoverer:
    def __init__(
        self,
        client: httpx.Client | None = None,
        crawler_settings: CrawlerSettings = settings,
    ) -> None:
        self._settings = crawler_settings
        self._client = client or httpx.Client(
            follow_redirects=True,
            timeout=crawler_settings.request_timeout_seconds,
            headers={"User-Agent": crawler_settings.user_agent},
        )

    def discover(self, url: str, limit: int) -> NewsDiscoverResult:
        try:
            with self._client.stream("GET", url) as response:
                response.raise_for_status()
                html = self._read_text(response)
                final_url = str(response.url)
                status_code = response.status_code
        except httpx.TimeoutException as exc:
            raise CrawlerException("链接发现超时") from exc
        except httpx.HTTPStatusError as exc:
            raise CrawlerException(
                f"目标网站返回异常状态码: {exc.response.status_code}"
            ) from exc
        except httpx.RequestError as exc:
            raise CrawlerException(f"无法访问目标网站: {exc}") from exc

        profile = self._match_profile(final_url)
        if self._looks_like_feed(final_url, html):
            links = self._extract_feed_links(html, final_url, profile, limit)
        else:
            links = self._extract_links(html, final_url, profile, limit)

        if profile and profile.name == "新浪新闻" and len(links) < limit:
            links = self._merge_links(links, self._discover_sina_feed(limit - len(links)))[:limit]

        # JS rendering fallback: if static HTML yields too few links
        if not self._looks_like_feed(final_url, html) and len(links) < limit:
            try:
                js_html = self._render_with_browser(final_url)
                if js_html and js_html != html:
                    links = self._merge_links(
                        links,
                        self._extract_links(js_html, final_url, profile, limit),
                    )[:limit]
            except Exception:
                pass

        return NewsDiscoverResult(
            source_url=url,
            final_url=final_url,
            status_code=status_code,
            source_name=profile.name if profile else "未知新闻源",
            source_type=profile.source_type if profile else "unknown",
            total_found=len(links),
            links=links,
        )

    def _extract_links(
        self,
        html: str,
        base_url: str,
        profile: SourceProfile | None,
        limit: int,
    ) -> list[DiscoveredNewsLink]:
        soup = BeautifulSoup(html, "lxml")
        seen: dict[str, DiscoveredNewsLink] = {}
        links: list[DiscoveredNewsLink] = []

        for tag in soup.find_all("a", href=True):
            href = tag.get("href")
            if not href:
                continue
            normalized_url = self._normalize_url(urljoin(base_url, href))
            if not self._is_news_detail_url(normalized_url, profile):
                continue

            title = tag.get_text(" ", strip=True) or None
            if normalized_url in seen:
                if title and not seen[normalized_url].title:
                    seen[normalized_url].title = title
                continue
            if len(links) >= limit:
                continue

            link = DiscoveredNewsLink(title=title, url=normalized_url)
            seen[normalized_url] = link
            links.append(link)

        return links

    def _extract_feed_links(
        self,
        feed_text: str,
        base_url: str,
        profile: SourceProfile | None,
        limit: int,
    ) -> list[DiscoveredNewsLink]:
        soup = BeautifulSoup(feed_text, "xml")
        entries = soup.find_all(["item", "entry"])
        seen: set[str] = set()
        links: list[DiscoveredNewsLink] = []

        for entry in entries:
            title_tag = entry.find("title")
            title = title_tag.get_text(" ", strip=True) if title_tag else None
            url = self._extract_feed_entry_url(entry, base_url)
            if not url:
                continue
            normalized_url = self._normalize_url(url)
            if normalized_url in seen:
                continue
            if not self._is_news_detail_url(normalized_url, profile):
                continue
            seen.add(normalized_url)
            links.append(DiscoveredNewsLink(title=title, url=normalized_url))
            if len(links) >= limit:
                break

        return links

    @staticmethod
    def _extract_feed_entry_url(entry, base_url: str) -> str | None:
        link_tag = entry.find("link")
        if link_tag:
            href = link_tag.get("href")
            if href:
                return urljoin(base_url, href.strip())
            text = link_tag.get_text(strip=True)
            if text:
                return urljoin(base_url, text)

        guid_tag = entry.find("guid")
        if guid_tag:
            text = guid_tag.get_text(strip=True)
            if text.startswith(("http://", "https://")):
                return text

        return None

    def _discover_sina_feed(self, limit: int, max_pages: int = 10) -> list[DiscoveredNewsLink]:
        if limit <= 0:
            return []

        links: list[DiscoveredNewsLink] = []
        seen: set[str] = set()
        for page in range(1, max_pages + 1):
            if len(links) >= limit:
                break
            feed_url = f"{SINA_FEED_API}?{SINA_FEED_PARAMS}&page={page}"
            try:
                response = self._client.get(feed_url)
                response.raise_for_status()
                data = response.json()
            except Exception:
                continue
            for item in data.get("result", {}).get("data", []):
                title = item.get("title") or None
                url = item.get("url") or ""
                if not url:
                    continue
                normalized = self._normalize_url(url)
                if normalized in seen:
                    continue
                if not self._is_news_detail_url(normalized, self._match_profile(normalized)):
                    continue
                seen.add(normalized)
                links.append(DiscoveredNewsLink(title=title, url=normalized))
                if len(links) >= limit:
                    break
        return links[:limit]

    @staticmethod
    def _merge_links(
        primary: list[DiscoveredNewsLink],
        supplement: list[DiscoveredNewsLink],
    ) -> list[DiscoveredNewsLink]:
        merged: list[DiscoveredNewsLink] = []
        seen: set[str] = set()
        for link in [*primary, *supplement]:
            if link.url in seen:
                continue
            seen.add(link.url)
            merged.append(link)
        return merged

    @staticmethod
    def _match_profile(url: str) -> SourceProfile | None:
        host = urlparse(url).netloc.lower().removeprefix("www.")
        for profile in SOURCE_PROFILES:
            clean_domains = tuple(d.removeprefix("www.") for d in profile.domains)
            if any(host == d or host.endswith("." + d) for d in clean_domains):
                return profile
        return None

    @staticmethod
    def _is_news_detail_url(url: str, profile: SourceProfile | None) -> bool:
        if profile:
            return any(pattern.match(url) for pattern in profile.patterns)
        return url.endswith((".shtml", ".html")) or "/article/" in url

    @staticmethod
    def _looks_like_feed(url: str, text: str) -> bool:
        path = urlparse(url).path.lower()
        if path.endswith((".xml", ".rss", ".atom")):
            return True
        head = text.lstrip()[:300].lower()
        return head.startswith("<?xml") or "<rss" in head or "<feed" in head

    @staticmethod
    def _normalize_url(url: str) -> str:
        parsed = urlparse(url)
        return urlunparse(
            (
                parsed.scheme,
                parsed.netloc.lower(),
                parsed.path,
                "",
                parsed.query,
                "",
            )
        )

    def _render_with_browser(self, url: str) -> str:
        import subprocess
        import sys

        script = r"""
import sys, json
from playwright.sync_api import sync_playwright
url = sys.argv[1]
with sync_playwright() as p:
    browser = p.chromium.launch(headless=True, args=['--no-sandbox'])
    page = browser.new_page()
    try:
        page.goto(url, wait_until='networkidle', timeout=30000)
        html = page.content()
    finally:
        browser.close()
try:
    print(json.dumps({"ok": True, "html": html}))
except Exception as e:
    print(json.dumps({"ok": False, "error": str(e)}))
"""
        try:
            result = subprocess.run(
                [sys.executable, "-c", script, url],
                capture_output=True,
                text=True,
                timeout=45,
            )
        except subprocess.TimeoutExpired:
            raise CrawlerException("Playwright 渲染超时")

        if result.returncode != 0:
            raise CrawlerException(f"Playwright 渲染失败: {result.stderr.strip() or '未知错误'}")

        import json
        data = json.loads(result.stdout.strip())
        if not data.get("ok"):
            raise CrawlerException(f"Playwright 渲染失败: {data.get('error', '未知错误')}")
        return data["html"]

    def _read_text(self, response: httpx.Response) -> str:
        content_type = response.headers.get("content-type", "").lower()
        allowed_types = ("html", "xml", "rss", "atom", "json")
        if content_type and not any(t in content_type for t in allowed_types):
            raise CrawlerException(f"目标内容不是 HTML: {content_type}")

        body = bytearray()
        for chunk in response.iter_bytes():
            body.extend(chunk)
            if len(body) > self._settings.max_response_bytes:
                raise CrawlerException("目标网页超过允许的最大响应大小")

        encoding = response.encoding or "utf-8"
        return bytes(body).decode(encoding, errors="replace")
