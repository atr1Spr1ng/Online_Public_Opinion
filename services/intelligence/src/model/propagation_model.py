from pydantic import BaseModel


class PropagationArticle(BaseModel):
    id: int
    title: str = ""
    content: str = ""
    source_name: str = ""
    published_at: str = ""


class PropagationRequest(BaseModel):
    event_id: int
    event_title: str = ""
    articles: list[PropagationArticle] = []


class PropagationNode(BaseModel):
    id: int
    title: str = ""
    source_name: str = ""
    published_at: str = ""
    node_type: str = "commercial"  # official | commercial | social
    is_source: bool = False
    is_influencer: bool = False
    depth: int = 0


class PropagationEdge(BaseModel):
    source: int
    target: int
    similarity: float = 0.0


class PropagationResponse(BaseModel):
    spread_depth: int = 0
    total_nodes: int = 0
    duration_hours: float = 0.0
    spread_speed: float = 0.0
    nodes: list[PropagationNode] = []
    edges: list[PropagationEdge] = []
    method: str = "llm"  # llm | fallback
