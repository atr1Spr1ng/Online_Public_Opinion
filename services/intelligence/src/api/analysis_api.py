from fastapi import APIRouter

from src.model.sentiment_model import SentimentRequest, SentimentResponse
from src.service.sentiment_service import SentimentService

router = APIRouter(prefix="/internal/analysis", tags=["analysis"])

sentiment_service = SentimentService()


@router.post("/sentiment", response_model=SentimentResponse)
def analyze_sentiment(request: SentimentRequest):
    return sentiment_service.analyze(request)
