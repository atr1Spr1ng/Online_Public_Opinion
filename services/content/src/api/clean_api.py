from fastapi import APIRouter
from src.model.clean_model import CleanRequest, CleanResponse
from src.service.clean_service import CleanService

router = APIRouter(prefix="/internal/content", tags=["content"])

clean_service = CleanService()


@router.post("/clean", response_model=CleanResponse)
def clean_article(request: CleanRequest):
    return clean_service.clean(request)
