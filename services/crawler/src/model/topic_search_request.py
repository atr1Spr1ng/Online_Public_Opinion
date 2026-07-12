from pydantic import BaseModel, Field


class TopicSearchRequest(BaseModel):
    keyword: str = Field(min_length=1, max_length=100)
    source_url: str = Field(validation_alias="sourceUrl")
    limit: int = Field(default=5, ge=1, le=20)

    model_config = {"populate_by_name": True}
