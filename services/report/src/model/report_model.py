import json
from typing import Any
from pydantic import BaseModel, Field


class SentimentStats(BaseModel):
    positive: float = 0.0
    negative: float = 0.0
    neutral: float = 0.0


class TimeSeriesItem(BaseModel):
    date: str
    count: int


class SummaryData(BaseModel):
    summary: str = ""
    time: str = ""
    location: str = ""
    cause: str = ""
    persons: str = ""
    key_steps: str = ""
    important_info: str = ""


class ReportData(BaseModel):
    event_id: int = 0
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
    summary: SummaryData = Field(default_factory=SummaryData)

    @classmethod
    def parse(cls, data: Any) -> "ReportData | None":
        """从 Java 端传来的 JSON 字符串或 dict 解析"""
        if data is None:
            return None
        if isinstance(data, str):
            try:
                data = json.loads(data)
            except (json.JSONDecodeError, TypeError):
                return None
        if isinstance(data, dict):
            try:
                sentiment_data = data.get("sentiment", {})
                sentiment = SentimentStats(
                    positive=float(sentiment_data.get("positive", 0)),
                    negative=float(sentiment_data.get("negative", 0)),
                    neutral=float(sentiment_data.get("neutral", 0)),
                )
                timeline_data = data.get("timeline", [])
                timeline = [TimeSeriesItem(date=str(t.get("date", "")), count=int(t.get("count", 0))) for t in timeline_data]
                articles_data = data.get("articles", data.get("article_titles", []))
                article_titles = [str(a) for a in articles_data]

                summary_data = data.get("summary", {}) or {}
                summary = SummaryData(
                    summary=str(summary_data.get("summary", "")),
                    time=str(summary_data.get("time", "")),
                    location=str(summary_data.get("location", "")),
                    cause=str(summary_data.get("cause", "")),
                    persons=str(summary_data.get("persons", "")),
                    key_steps=str(summary_data.get("key_steps", "")),
                    important_info=str(summary_data.get("important_info", "")),
                )

                return cls(
                    event_id=int(data.get("eventId", data.get("event_id", 0))),
                    title=str(data.get("title", "")),
                    keywords=str(data.get("keywords", "")),
                    lifecycle=str(data.get("lifecycle", "")),
                    start_time=str(data.get("startTime", data.get("start_time", ""))),
                    end_time=str(data.get("endTime", data.get("end_time", ""))),
                    article_count=int(data.get("articleCount", data.get("article_count", 0))),
                    hotness=float(data.get("hotness", 0)),
                    sentiment=sentiment,
                    timeline=timeline,
                    article_titles=article_titles,
                    summary=summary,
                )
            except (TypeError, ValueError):
                return None
        return None


class QaRequest(BaseModel):
    question: str
    report: Any = None


class QaResponse(BaseModel):
    answer: str
    source: str = Field(default="keyword", description="keyword / llm")
