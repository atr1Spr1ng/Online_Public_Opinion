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
    status: str = "CLEANED"  # CLEANED | NOISY（正文<20个中文字符标记为噪音）
