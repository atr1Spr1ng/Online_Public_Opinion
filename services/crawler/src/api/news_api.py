from fastapi import APIRouter, HTTPException, status

from exception.crawler_exception import CrawlerException
from model.crawl_result import CrawlResult
from model.news_collect_result import NewsCollectResult
from model.news_discover_request import NewsDiscoverRequest
from model.news_discover_result import NewsDiscoverResult
from model.news_request import NewsCrawlRequest
from model.topic_search_request import TopicSearchRequest
from model.topic_search_result import TopicSearchResult
from service.crawler_service import CrawlerService

router = APIRouter(prefix="/internal/crawler/news", tags=["news crawler"])
service = CrawlerService()


def _crawl_news(request: NewsCrawlRequest) -> CrawlResult:
    try:
        return service.crawl_news(str(request.url))
    except CrawlerException as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=str(exc),
        ) from exc


@router.post("/crawl", response_model=CrawlResult, response_model_by_alias=True)
def crawl_news(request: NewsCrawlRequest) -> CrawlResult:
    return _crawl_news(request)


@router.post("/test", response_model=CrawlResult, response_model_by_alias=True)
def test_news_crawler(request: NewsCrawlRequest) -> CrawlResult:
    return _crawl_news(request)


@router.post("/discover", response_model=NewsDiscoverResult, response_model_by_alias=True)
def discover_news_links(request: NewsDiscoverRequest) -> NewsDiscoverResult:
    try:
        return service.discover_news_links(str(request.url), request.limit)
    except CrawlerException as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=str(exc),
        ) from exc


@router.post("/collect", response_model=NewsCollectResult, response_model_by_alias=True)
def collect_news(request: NewsDiscoverRequest) -> NewsCollectResult:
    try:
        return service.collect_news(str(request.url), request.limit)
    except CrawlerException as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=str(exc),
        ) from exc


@router.post("/search-by-topic", response_model=TopicSearchResult, response_model_by_alias=True)
def search_by_topic(request: TopicSearchRequest) -> TopicSearchResult:
    """Search a news source for articles matching a keyword, then crawl each result."""
    try:
        return service.search_by_topic(request.keyword, str(request.source_url), request.limit)
    except CrawlerException as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=str(exc),
        ) from exc
