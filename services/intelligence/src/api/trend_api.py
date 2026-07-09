from fastapi import APIRouter

from src.model.trend_model import ForecastRequest, ForecastResponse
from src.service.trend_forecaster import TrendForecaster

router = APIRouter(prefix="/internal/trend", tags=["trend"])

forecaster = TrendForecaster()


@router.post("/forecast", response_model=ForecastResponse)
def forecast_trend(request: ForecastRequest):
    daily_counts = [{"date": d.date, "count": d.count} for d in request.daily_counts]
    result = forecaster.forecast(daily_counts, request.periods)
    return ForecastResponse(**result)
