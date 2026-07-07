from pydantic import BaseModel, Field


class SentimentRequest(BaseModel):
    title: str = ""
    content: str = ""
    language: str = "zh"


class SentimentWord(BaseModel):
    word: str
    sentiment: str = Field(description="POSITIVE or NEGATIVE")
    weight: float = 1.0


class SentimentResponse(BaseModel):
    sentiment: str = Field(description="POSITIVE / NEGATIVE / NEUTRAL")
    positive_score: float = 0.0
    negative_score: float = 0.0
    confidence: float = 0.0
    positive_words: list[SentimentWord] = []
    negative_words: list[SentimentWord] = []
    details: str = ""
