from pydantic import BaseModel, Field


class SentimentStats(BaseModel):
    positive: float = 0.0
    negative: float = 0.0
    neutral: float = 0.0


class TimeSeriesItem(BaseModel):
    date: str
    count: int


class ReportData(BaseModel):
    event_id: int
    title: str = ""
    keywords: str = ""
    lifecycle: str = ""
    start_time: str = ""
    end_time: str = ""
    article_count: int = 0
    hotness: float = 0.0
    sentiment: SentimentStats = Field(default_factory=SentimentStats)
    timeline: list[TimeSeriesItem] = []
    article_titles: list[str] = []


class QaRequest(BaseModel):
    question: str
    report: ReportData | None = None


class QaResponse(BaseModel):
    answer: str
    source: str = Field(default="keyword", description="keyword / llm")
