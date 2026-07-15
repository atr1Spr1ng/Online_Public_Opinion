from datetime import datetime, timezone
import re
from urllib.parse import urlparse

import httpx
from bs4 import BeautifulSoup
from newsplease import NewsPlease
from readability import Document

from src.config.settings import CrawlerSettings, settings
from src.exception.crawler_exception import CrawlerException
from src.model.crawl_result import CrawlResult


MIN_NEWS_CONTENT_LENGTH = 80
MAX_ARTICLE_AGE_DAYS = 365
MAX_FUTURE_DAYS = 2
DATE_CORRECTION_TOLERANCE_DAYS = 30

NOISE_TITLES = {
    "央视网",
    "新华网",
    "人民网",
    "环球网",
    "中国新闻网",
    "中新网",
    "新浪新闻",
    "澎湃新闻",
    "界面新闻",
    "新闻中心",
    "滚动新闻",
    "专题",
    "频道首页",
    "首页",
}

NOISE_TITLE_KEYWORDS = (
    "专题",
    "频道首页",
    "滚动新闻",
    "新闻中心",
    "客户端下载",
    "网站地图",
)


class NewsPleaseAdapter:
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
        self._browser = None
        self._playwright = None

    def crawl(self, url: str) -> CrawlResult:
        html, final_url, status_code = self._fetch_with_httpx(url)
        result = self._extract(html, url, final_url, status_code)

        # JS rendering fallback: if both news-please and readability failed
        if result.content_length == 0:
            try:
                js_html = self._render_with_browser(url)
                if js_html and js_html != html:
                    result = self._extract(js_html, url, final_url, status_code)
                    result.engine = "playwright"
            except CrawlerException:
                pass

        return result

    def _fetch_with_httpx(self, url: str) -> tuple[str, str, int]:
        try:
            with self._client.stream("GET", url) as response:
                response.raise_for_status()
                html = self._read_html(response)
                final = str(response.url)
                code = response.status_code
            return html, final, code
        except httpx.TimeoutException as exc:
            raise CrawlerException("抓取超时") from exc
        except httpx.HTTPStatusError as exc:
            raise CrawlerException(
                f"目标网站返回异常状态码: {exc.response.status_code}"
            ) from exc
        except httpx.RequestError as exc:
            raise CrawlerException(f"无法访问目标网站: {exc}") from exc

    def _extract(self, html: str, original_url: str, final_url: str, status_code: int) -> CrawlResult:
        try:
            article = NewsPlease.from_html(html, url=final_url)
        except Exception:
            article = NewsPlease.from_html("", url=final_url)
            article.title = None
            article.maintext = None
            article.authors = []
            article.date_publish = None
            article.image_url = None
            article.language = None

        fallback_title, fallback_content = self._extract_fallback_fields(html)
        title = article.title or fallback_title
        content = article.maintext or fallback_content
        content_length = len(content.strip()) if content else 0
        extract_status, message = self._build_extract_status(title, content, content_length)
        published_at, date_note = self._resolve_published_at(
            article.date_publish,
            html,
            original_url,
            final_url,
        )
        if extract_status == "SUCCESS":
            date_status, date_message = self._validate_published_at(published_at)
            if date_status:
                extract_status = date_status
                message = date_message
            elif date_note:
                message = f"{message}；{date_note}"

        return CrawlResult(
            engine="news-please",
            original_url=original_url,
            final_url=final_url,
            status_code=status_code,
            title=title,
            authors=list(article.authors or []),
            published_at=published_at,
            content=content,
            content_length=content_length,
            extract_status=extract_status,
            message=message,
            main_image=article.image_url,
            language=article.language,
            fetched_at=datetime.now(timezone.utc),
        )

    def _render_with_browser(self, url: str) -> str:
        import subprocess
        import sys

        # Use subprocess to run a tiny standalone script — avoids
        # threading complications from running asyncio inside uvicorn.
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
                timeout=self._settings.request_timeout_seconds + 15,
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

    def close(self) -> None:
        self._client.close()

    def _build_extract_status(self, title: str | None, content: str | None, content_length: int) -> tuple[str, str]:
        if content_length <= 0:
            return (
                "EMPTY_CONTENT",
                "页面访问成功，但未抽取到正文。请确认 URL 是新闻详情页；首页、列表页或 JS 动态加载页面需要后续链接发现/动态渲染能力。",
            )

        normalized_title = self._normalize_title(title)
        if self._is_noise_title(normalized_title):
            return "NOISY_PAGE", f"疑似非新闻详情页：标题为站点名或栏目页 ({normalized_title or '空标题'})"

        if content_length < MIN_NEWS_CONTENT_LENGTH:
            return "NOISY_PAGE", f"疑似非新闻详情页：正文过短 ({content_length} 字符)"

        if self._looks_like_navigation_page(normalized_title, content or ""):
            return "NOISY_PAGE", "疑似非新闻详情页：页面内容更像导航/频道/专题页"

        return "SUCCESS", "新闻正文抽取成功"

    @staticmethod
    def _normalize_title(title: str | None) -> str:
        if not title:
            return ""
        text = " ".join(title.split())
        for sep in ("_央视网", "-央视网", "－央视网", "_新华网", "-新华网", "－新华网"):
            if sep in text:
                text = text.split(sep, 1)[0].strip()
        return text.strip()

    @staticmethod
    def _is_noise_title(title: str) -> bool:
        if not title:
            return True
        clean = title.strip(" -_—｜|")
        if clean in NOISE_TITLES:
            return True
        if len(clean) <= 4 and any(site in clean for site in ("央视", "新华", "人民网", "环球")):
            return True
        return any(keyword == clean or clean.endswith(keyword) for keyword in NOISE_TITLE_KEYWORDS)

    @staticmethod
    def _looks_like_navigation_page(title: str, content: str) -> bool:
        text = "\n".join(line.strip() for line in content.splitlines() if line.strip())
        if not text:
            return True

        nav_words = ("首页", "频道", "专题", "客户端", "微博", "微信", "广告", "版权", "联系我们", "网站地图")
        nav_hit_count = sum(1 for word in nav_words if word in text)
        lines = [line for line in text.splitlines() if line]
        avg_line_length = sum(len(line) for line in lines) / max(len(lines), 1)

        if title in NOISE_TITLES and nav_hit_count >= 2:
            return True
        if len(lines) >= 12 and avg_line_length < 18 and nav_hit_count >= 4:
            return True
        return (
            "相关新闻" in text
            and "更多" in text
            and nav_hit_count >= 4
        )

    @staticmethod
    def _extract_fallback_fields(html: str) -> tuple[str | None, str | None]:
        document = Document(html)
        summary = BeautifulSoup(document.summary(), "lxml")
        paragraphs = [
            paragraph.get_text(" ", strip=True)
            for paragraph in summary.find_all("p")
            if paragraph.get_text(strip=True)
        ]
        content = "\n".join(paragraphs) or summary.get_text("\n", strip=True) or None

        title = document.short_title()
        if not title:
            title_tag = BeautifulSoup(html, "lxml").title
            title = title_tag.get_text(strip=True) if title_tag else None

        return title or None, content

    def _read_html(self, response: httpx.Response) -> str:
        content_type = response.headers.get("content-type", "").lower()
        if content_type and "html" not in content_type:
            raise CrawlerException(f"目标内容不是 HTML: {content_type}")

        body = bytearray()
        for chunk in response.iter_bytes():
            body.extend(chunk)
            if len(body) > self._settings.max_response_bytes:
                raise CrawlerException("目标网页超过允许的最大响应大小")

        encoding = response.encoding or "utf-8"
        return bytes(body).decode(encoding, errors="replace")

    def _resolve_published_at(
        self,
        extracted_at: datetime | None,
        html: str,
        original_url: str,
        final_url: str,
    ) -> tuple[datetime | None, str]:
        """Prefer trustworthy publication dates.

        news-please may pick unrelated page dates on some portals. If URL/meta
        dates clearly disagree with the extracted date, use the site-specific
        evidence instead.
        """
        meta_at = self._extract_meta_published_at(html)
        url_at = self._extract_date_from_url(final_url) or self._extract_date_from_url(original_url)

        candidate = extracted_at
        note = ""

        if candidate is None and meta_at is not None:
            candidate = meta_at
            note = "发布时间来自页面 meta 信息"

        if candidate is None and url_at is not None:
            candidate = url_at
            note = "发布时间来自 URL 日期"
        elif url_at is not None and candidate is not None:
            delta = abs((self._as_date(candidate) - self._as_date(url_at)).days)
            if delta > DATE_CORRECTION_TOLERANCE_DAYS:
                candidate = url_at
                note = "发布时间已按 URL 日期纠偏"
        elif meta_at is not None and candidate is not None:
            delta = abs((self._as_date(candidate) - self._as_date(meta_at)).days)
            if delta > DATE_CORRECTION_TOLERANCE_DAYS:
                candidate = meta_at
                note = "发布时间已按页面 meta 信息纠偏"

        return candidate, note

    @staticmethod
    def _validate_published_at(published_at: datetime | None) -> tuple[str | None, str]:
        if published_at is None:
            return None, ""
        today = datetime.now(timezone.utc).date()
        published_date = NewsPleaseAdapter._as_date(published_at)
        age_days = (today - published_date).days
        if age_days > MAX_ARTICLE_AGE_DAYS:
            return "STALE_ARTICLE", f"文章发布时间超过 {MAX_ARTICLE_AGE_DAYS} 天，已跳过入库"
        if age_days < -MAX_FUTURE_DAYS:
            return "DATE_OUT_OF_RANGE", "文章发布时间明显晚于当前时间，已跳过入库"
        return None, ""

    @staticmethod
    def _extract_meta_published_at(html: str) -> datetime | None:
        soup = BeautifulSoup(html, "lxml")
        meta_names = (
            ("property", "article:published_time"),
            ("property", "og:published_time"),
            ("name", "pubdate"),
            ("name", "publishdate"),
            ("name", "publishDate"),
            ("name", "date"),
            ("name", "weibo: article:create_at"),
            ("itemprop", "datePublished"),
        )
        for attr, value in meta_names:
            tag = soup.find("meta", attrs={attr: value})
            content = tag.get("content") if tag else None
            parsed = NewsPleaseAdapter._parse_datetime(content)
            if parsed:
                return parsed

        time_tag = soup.find("time")
        if time_tag:
            parsed = NewsPleaseAdapter._parse_datetime(time_tag.get("datetime") or time_tag.get_text(" ", strip=True))
            if parsed:
                return parsed
        return None

    @staticmethod
    def _extract_date_from_url(url: str) -> datetime | None:
        path = urlparse(url).path
        patterns = (
            r"/(20\d{2})-(\d{2})-(\d{2})/",
            r"/(20\d{2})/(\d{2})-(\d{2})/",
            r"/(20\d{2})/(\d{2})/(\d{2})/",
            r"/(20\d{2})(\d{2})(\d{2})/",
        )
        for pattern in patterns:
            match = re.search(pattern, path)
            if match:
                year, month, day = map(int, match.groups())
                try:
                    return datetime(year, month, day)
                except ValueError:
                    return None
        return None

    @staticmethod
    def _parse_datetime(value: str | None) -> datetime | None:
        if not value:
            return None
        text = value.strip()
        if not text:
            return None
        normalized = text.replace("Z", "+00:00")
        try:
            return datetime.fromisoformat(normalized)
        except ValueError:
            pass
        for fmt in (
            "%Y-%m-%d %H:%M:%S",
            "%Y-%m-%d %H:%M",
            "%Y-%m-%d",
            "%Y/%m/%d %H:%M:%S",
            "%Y/%m/%d %H:%M",
            "%Y/%m/%d",
        ):
            try:
                return datetime.strptime(text[:19], fmt)
            except ValueError:
                continue
        return None

    @staticmethod
    def _as_date(value: datetime):
        return value.date()
