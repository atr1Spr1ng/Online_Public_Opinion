from pydantic import BaseModel, ConfigDict, Field


class DiscoveredNewsLink(BaseModel):
    title: str | None = None
    url: str


class NewsDiscoverResult(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    source_url: str = Field(alias="sourceUrl")
    final_url: str = Field(alias="finalUrl")
    status_code: int = Field(alias="statusCode")
    source_name: str = Field(alias="sourceName")
    source_type: str = Field(alias="sourceType")
    total_found: int = Field(alias="totalFound")
    links: list[DiscoveredNewsLink]
