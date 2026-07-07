from fastapi import APIRouter

from src.model.report_model import QaRequest, QaResponse, ReportData
from src.service.qa_service import QaService

router = APIRouter(prefix="/internal/report", tags=["report"])

qa_service = QaService()


@router.post("/qa", response_model=QaResponse)
def answer_question(request: QaRequest):
    return qa_service.answer(request)
