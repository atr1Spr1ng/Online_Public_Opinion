import logging

from fastapi import APIRouter

from src.model.clustering_model import (
    ClusterRequest,
    ClusterResponse,
    SimilarEventRequest,
    SimilarEventResult,
)
from src.service.clustering_service import ClusteringService

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/internal/event", tags=["event"])

clustering_service = ClusteringService()


@router.post("/cluster", response_model=ClusterResponse)
def cluster_articles(request: ClusterRequest):
    result = clustering_service.cluster(request.articles, request.threshold)
    for e in result.events:
        logger.info("[API] event_id=%d start_time=%r end_time=%r", e.event_id, e.start_time, e.end_time)
    return result


@router.post("/similar", response_model=list[SimilarEventResult])
def similar_events(request: SimilarEventRequest):
    return []
