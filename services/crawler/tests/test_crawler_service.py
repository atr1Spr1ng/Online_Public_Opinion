from datetime import datetime, timezone
from pathlib import Path
import sys

SOURCE_ROOT = Path(__file__).resolve().parents[1] / "src"
sys.path.insert(0, str(SOURCE_ROOT))

from exception.crawler_exception import CrawlerException
from model.crawl_result import CrawlResult
from model.news_discover_result import DiscoveredNewsLink, NewsDiscoverResult
from service.crawler_service import CrawlerService


class FakeDiscoverer:
    def discover(self, url: str, limit: int) -> NewsDiscoverResult:
        return NewsDiscoverResult(
            source_url=url,
            final_url=url,
            status_code=200,
            source_name="测试新闻源",
            source_type="test",
            total_found=2,
            links=[
                DiscoveredNewsLink(title="成功新闻", url="https://example.com/success.html"),
                DiscoveredNewsLink(title="失败新闻", url="https://example.com/fail.html"),
            ],
        )


class FakeNewsAdapter:
    def crawl(self, url: str) -> CrawlResult:
        if url.endswith("fail.html"):
            raise CrawlerException("测试抓取失败")
        return CrawlResult(
            engine="fake",
            original_url=url,
            final_url=url,
            status_code=200,
            title="成功新闻",
            authors=[],
            published_at=None,
            content="这是一条成功抓取的测试新闻正文。",
            content_length=16,
            extract_status="SUCCESS",
            message="新闻正文抽取成功",
            main_image=None,
            language="zh",
            fetched_at=datetime.now(timezone.utc),
        )


def test_collect_news_counts_success_and_failures() -> None:
    service = CrawlerService(
        news_adapter=FakeNewsAdapter(),
        link_discoverer=FakeDiscoverer(),
    )

    result = service.collect_news("https://example.com/", 2)

    assert result.total_discovered == 2
    assert result.total_success == 1
    assert result.total_failed == 1
    assert result.articles[0].title == "成功新闻"
    assert result.failures[0].reason == "测试抓取失败"
