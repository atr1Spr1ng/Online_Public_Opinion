from pydantic import BaseModel, Field


class DailyCount(BaseModel):
    date: str
    count: int


class ForecastRequest(BaseModel):
    daily_counts: list[DailyCount]
    periods: int = Field(default=7, description="预测未来天数")


class ForecastPoint(BaseModel):
    date: str
    count: int
    yhat_lower: int = 0
    yhat_upper: int = 0


class ForecastResponse(BaseModel):
    method: str  # "prophet" | "moving_avg" | "none"
    historical: list[dict]
    forecast: list[ForecastPoint]
    trend: str  # "up" | "down" | "stable"
    note: str = ""
