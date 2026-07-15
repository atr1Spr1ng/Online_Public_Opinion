from pydantic import BaseModel, Field

from src.model.crawl_result import CrawlResult
from src.model.news_collect_result import FailedNewsCrawl


class TopicSearchResult(BaseModel):
    keyword: str
    source_name: str
    source_type: str
    source_url: str
    total_found: int
    total_success: int
    total_failed: int
    articles: list[CrawlResult] = Field(default_factory=list)
    failures: list[FailedNewsCrawl] = Field(default_factory=list)
