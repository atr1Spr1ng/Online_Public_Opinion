from fastapi import APIRouter, HTTPException, status

from exception.crawler_exception import CrawlerException
from model.crawl_result import CrawlResult
from model.news_request import NewsCrawlRequest
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
