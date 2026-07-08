from fastapi import APIRouter

from src.model.sentiment_model import SentimentRequest, SentimentResponse
from src.model.fake_detection_model import FakeDetectionRequest, FakeDetectionResponse
from src.service.sentiment_service import SentimentService
from src.service.fake_detection_service import FakeDetectionService

router = APIRouter(prefix="/internal/analysis", tags=["analysis"])

sentiment_service = SentimentService()
fake_detection_service = FakeDetectionService()


@router.post("/sentiment", response_model=SentimentResponse)
def analyze_sentiment(request: SentimentRequest):
    return sentiment_service.analyze(request)


@router.post("/fake-detection", response_model=FakeDetectionResponse)
def detect_fake(request: FakeDetectionRequest):
    return fake_detection_service.detect(request)
