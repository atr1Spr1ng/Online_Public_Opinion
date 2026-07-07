from adapter.news_link_discoverer import NewsLinkDiscoverer
from adapter.news_please_adapter import NewsPleaseAdapter
from model.crawl_result import CrawlResult
from model.news_discover_result import NewsDiscoverResult


class CrawlerService:
    def __init__(
        self,
        news_adapter: NewsPleaseAdapter | None = None,
        link_discoverer: NewsLinkDiscoverer | None = None,
    ) -> None:
        self._news_adapter = news_adapter or NewsPleaseAdapter()
        self._link_discoverer = link_discoverer or NewsLinkDiscoverer()

    def crawl_news(self, url: str) -> CrawlResult:
        return self._news_adapter.crawl(url)

    def discover_news_links(self, url: str, limit: int) -> NewsDiscoverResult:
        return self._link_discoverer.discover(url, limit)
