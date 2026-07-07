from pydantic import BaseModel, Field


class ArticleItem(BaseModel):
    id: int
    title: str = ""
    keywords: str = ""
    summary: str = ""
    published_at: str = ""


class ClusterRequest(BaseModel):
    articles: list[ArticleItem]
    threshold: float = Field(default=0.25, description="Jaccard similarity threshold")


class EventCluster(BaseModel):
    event_id: int
    title: str
    keywords: list[str]
    article_ids: list[int]
    article_count: int
    hotness: float
    lifecycle: str = Field(default="潜伏期")
    start_time: str = ""
    end_time: str = ""


class ClusterResponse(BaseModel):
    events: list[EventCluster]
    total_articles: int
    clustered_articles: int
    unclustered_articles: int


class SimilarEventRequest(BaseModel):
    keywords: str
    top_k: int = Field(default=5)


class SimilarEventResult(BaseModel):
    event_id: int
    title: str
    keywords: list[str]
    similarity: float
    article_count: int
    hotness: float
