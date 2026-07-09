from fastapi import APIRouter

from src.model.propagation_model import PropagationRequest, PropagationResponse
from src.service.propagation_service import PropagationService

router = APIRouter(prefix="/internal/event", tags=["propagation"])

service = PropagationService()


@router.post("/propagation", response_model=PropagationResponse)
def analyze_propagation(request: PropagationRequest):
    return service.analyze(request)
