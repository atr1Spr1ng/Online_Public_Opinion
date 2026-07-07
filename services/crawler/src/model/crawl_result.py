from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class CrawlResult(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    engine: str
    original_url: str = Field(alias="originalUrl")
    final_url: str = Field(alias="finalUrl")
    status_code: int = Field(alias="statusCode")
    title: str | None = None
    authors: list[str] = Field(default_factory=list)
    published_at: datetime | None = Field(default=None, alias="publishedAt")
    content: str | None = None
    content_length: int = Field(alias="contentLength")
    extract_status: str = Field(alias="extractStatus")
    message: str
    main_image: str | None = Field(default=None, alias="mainImage")
    language: str | None = None
    fetched_at: datetime = Field(alias="fetchedAt")
