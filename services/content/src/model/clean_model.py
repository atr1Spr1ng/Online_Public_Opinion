from pydantic import BaseModel


class CleanRequest(BaseModel):
    title: str | None = None
    content: str | None = None
    url: str | None = None
    language: str | None = None


class CleanResponse(BaseModel):
    title: str | None = None
    content: str | None = None
    keywords: str = ""
    summary: str = ""
    language: str = "zh"
    status: str = "CLEANED"  # CLEANED | NOISY


class VectorizeRequest(BaseModel):
    texts: list[str]


class VectorizeResponse(BaseModel):
    vectors: list[list[float]]
    vocab_size: int
