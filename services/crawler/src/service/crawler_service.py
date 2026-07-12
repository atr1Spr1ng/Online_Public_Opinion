from adapter.news_link_discoverer import NewsLinkDiscoverer
from adapter.news_please_adapter import NewsPleaseAdapter
from adapter.search_adapter import TopicSearchAdapter
from exception.crawler_exception import CrawlerException
from model.crawl_result import CrawlResult
from model.news_collect_result import FailedNewsCrawl, NewsCollectResult
from model.news_discover_result import NewsDiscoverResult
from model.topic_search_result import TopicSearchResult


class CrawlerService:
    def __init__(
        self,
        news_adapter: NewsPleaseAdapter | None = None,
        link_discoverer: NewsLinkDiscoverer | None = None,
        search_adapter: TopicSearchAdapter | None = None,
    ) -> None:
        self._news_adapter = news_adapter or NewsPleaseAdapter()
        self._link_discoverer = link_discoverer or NewsLinkDiscoverer()
        self._search_adapter = search_adapter or TopicSearchAdapter()

    def crawl_news(self, url: str) -> CrawlResult:
        return self._news_adapter.crawl(url)

    def discover_news_links(self, url: str, limit: int) -> NewsDiscoverResult:
        return self._link_discoverer.discover(url, limit)

    def collect_news(self, url: str, limit: int) -> NewsCollectResult:
        discovered = self.discover_news_links(url, limit)
        articles: list[CrawlResult] = []
        failures: list[FailedNewsCrawl] = []

        for link in discovered.links:
            try:
                article = self.crawl_news(link.url)
            except CrawlerException as exc:
                failures.append(
                    FailedNewsCrawl(
                        url=link.url,
                        title=link.title,
                        reason=str(exc),
                    )
                )
                continue

            if article.extract_status == "SUCCESS":
                articles.append(article)
            else:
                failures.append(
                    FailedNewsCrawl(
                        url=link.url,
                        title=link.title,
                        reason=article.message,
                    )
                )

        return NewsCollectResult(
            source_url=discovered.source_url,
            final_url=discovered.final_url,
            source_name=discovered.source_name,
            source_type=discovered.source_type,
            total_discovered=discovered.total_found,
            total_success=len(articles),
            total_failed=len(failures),
            articles=articles,
            failures=failures,
        )

    def search_by_topic(self, keyword: str, source_url: str, limit: int) -> TopicSearchResult:
        profile = self._search_adapter.match_profile(source_url)
        if profile is None:
            raise CrawlerException(f"当前不支持该新闻源的搜索: {source_url}")

        links = self._search_adapter.search(keyword, profile, limit)
        articles: list[CrawlResult] = []
        failures: list[FailedNewsCrawl] = []

        for link in links:
            try:
                article = self._news_adapter.crawl(link.url)
            except CrawlerException as exc:
                failures.append(FailedNewsCrawl(url=link.url, title=link.title, reason=str(exc)))
                continue

            if article.extract_status == "SUCCESS":
                articles.append(article)
            else:
                failures.append(FailedNewsCrawl(url=link.url, title=link.title, reason=article.message))

        return TopicSearchResult(
            keyword=keyword,
            source_name=profile.name,
            source_type=profile.source_type,
            source_url=source_url,
            total_found=len(links),
            total_success=len(articles),
            total_failed=len(failures),
            articles=articles,
            failures=failures,
        )
