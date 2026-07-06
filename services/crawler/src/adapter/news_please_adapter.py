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

    def crawl(self, url: str) -> CrawlResult:
        try:
            with self._client.stream("GET", url) as response:
                response.raise_for_status()
                html = self._read_html(response)
                final_url = str(response.url)
                status_code = response.status_code
        except httpx.TimeoutException as exc:
            raise CrawlerException("抓取超时") from exc
        except httpx.HTTPStatusError as exc:
            raise CrawlerException(
                f"目标网站返回异常状态码: {exc.response.status_code}"
            ) from exc
        except httpx.RequestError as exc:
            raise CrawlerException(f"无法访问目标网站: {exc}") from exc

        try:
            article = NewsPlease.from_html(html, url=final_url)
        except Exception as exc:
            raise CrawlerException("news-please 解析网页失败") from exc

        fallback_title, fallback_content = self._extract_fallback_fields(html)
        return CrawlResult(
            engine="news-please",
            original_url=url,
            final_url=final_url,
            status_code=status_code,
            title=article.title or fallback_title,
            authors=list(article.authors or []),
            published_at=article.date_publish,
            content=article.maintext or fallback_content,
            main_image=article.image_url,
            language=article.language,
            fetched_at=datetime.now(timezone.utc),
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
