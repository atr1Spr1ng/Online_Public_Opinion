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
    min_cluster_size: int = Field(default=3, ge=2, le=20, description="Minimum articles required to form an event")


class ExistingEventInfo(BaseModel):
    """现有事件信息，用于增量聚类匹配"""
    event_id: int
    title: str
    keywords: list[str]
    article_ids: list[int]    # 该事件已有的文章ID列表


class IncrementalClusterRequest(BaseModel):
    articles: list[ArticleItem]
    existing_events: list[ExistingEventInfo] = []
    threshold: float = Field(default=0.25, description="HDBSCAN cluster_selection_epsilon")
    match_threshold: float = Field(default=0.65, description="Cosine similarity threshold for matching to existing events")
    min_cluster_size: int = Field(default=3, ge=2, le=20, description="Minimum articles required to form an event")


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
    is_existing: bool = False   # True if this event already exists in DB (update it)


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
