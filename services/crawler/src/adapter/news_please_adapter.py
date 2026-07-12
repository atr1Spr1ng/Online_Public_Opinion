from datetime import datetime, timezone

import httpx
from bs4 import BeautifulSoup
from newsplease import NewsPlease
from readability import Document

from config.settings import CrawlerSettings, settings
from exception.crawler_exception import CrawlerException
from model.crawl_result import CrawlResult


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
        extract_status, message = self._build_extract_status(content_length)

        return CrawlResult(
            engine="news-please",
            original_url=original_url,
            final_url=final_url,
            status_code=status_code,
            title=title,
            authors=list(article.authors or []),
            published_at=article.date_publish,
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

    @staticmethod
    def _build_extract_status(content_length: int) -> tuple[str, str]:
        if content_length > 0:
            return "SUCCESS", "新闻正文抽取成功"
        return (
            "EMPTY_CONTENT",
            "页面访问成功，但未抽取到正文。请确认 URL 是新闻详情页；首页、列表页或 JS 动态加载页面需要后续链接发现/动态渲染能力。",
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
