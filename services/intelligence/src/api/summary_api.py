from fastapi import APIRouter

from src.model.summary_model import EventSummaryRequest, EventSummaryResponse
from src.service.summary_service import SummaryService

router = APIRouter(prefix="/internal/event", tags=["event-summary"])

summary_service = SummaryService()


@router.post("/summary", response_model=EventSummaryResponse)
def get_event_summary(request: EventSummaryRequest):
    return summary_service.summarize(request)
