from pydantic import BaseModel


class ArticleItem(BaseModel):
    title: str = ""
    summary: str = ""
    published_at: str = ""
    source_name: str = ""


class EventSummaryRequest(BaseModel):
    event_title: str = ""
    event_keywords: str = ""
    articles: list[ArticleItem] = []


class EventSummaryResponse(BaseModel):
    summary: str = ""
    time: str = ""
    location: str = ""
    cause: str = ""
    persons: str = ""
    key_steps: str = ""
    important_info: str = ""
    method: str = "llm"
