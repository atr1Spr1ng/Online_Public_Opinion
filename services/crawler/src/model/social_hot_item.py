from datetime import datetime
from typing import Optional

from pydantic import BaseModel, ConfigDict, Field


class SocialHotItem(BaseModel):
    rank: int
    title: str
    hot_score: int | str | None = Field(default=None, alias="hotScore")
    url: str | None = None
    summary: str | None = None


class SocialHotResult(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    platform: str
    items: list[SocialHotItem]
    fetched_at: datetime = Field(default_factory=datetime.now, alias="fetchedAt")
