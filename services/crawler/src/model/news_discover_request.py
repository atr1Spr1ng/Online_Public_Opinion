from pydantic import BaseModel, Field, HttpUrl


class NewsDiscoverRequest(BaseModel):
    url: HttpUrl
    limit: int = Field(default=20, ge=1, le=100)
