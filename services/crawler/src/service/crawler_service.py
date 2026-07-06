from adapter.news_please_adapter import NewsPleaseAdapter
from model.crawl_result import CrawlResult


class CrawlerService:
    def __init__(self, news_adapter: NewsPleaseAdapter | None = None) -> None:
        self._news_adapter = news_adapter or NewsPleaseAdapter()

    def crawl_news(self, url: str) -> CrawlResult:
        return self._news_adapter.crawl(url)
