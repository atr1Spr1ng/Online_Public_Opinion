from pydantic import BaseModel, Field


class EventItem(BaseModel):
    event_id: int
    title: str = ""
    keywords: list[str] = []
    article_count: int = 0
    hotness: float = 0.0


class TopicClassifyRequest(BaseModel):
    events: list[EventItem]


class TopicClassifyResponse(BaseModel):
    events: list[dict]
