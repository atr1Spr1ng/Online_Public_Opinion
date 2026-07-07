from pydantic import BaseModel, ConfigDict, Field

from model.crawl_result import CrawlResult


class FailedNewsCrawl(BaseModel):
    url: str
    title: str | None = None
    reason: str


class NewsCollectResult(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    source_url: str = Field(alias="sourceUrl")
    final_url: str = Field(alias="finalUrl")
    source_name: str = Field(alias="sourceName")
    source_type: str = Field(alias="sourceType")
    total_discovered: int = Field(alias="totalDiscovered")
    total_success: int = Field(alias="totalSuccess")
    total_failed: int = Field(alias="totalFailed")
    articles: list[CrawlResult]
    failures: list[FailedNewsCrawl]
