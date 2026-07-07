from fastapi import APIRouter

from src.model.clustering_model import (
    ClusterRequest,
    ClusterResponse,
    SimilarEventRequest,
    SimilarEventResult,
)
from src.service.clustering_service import ClusteringService

router = APIRouter(prefix="/internal/event", tags=["event"])

clustering_service = ClusteringService()


@router.post("/cluster", response_model=ClusterResponse)
def cluster_articles(request: ClusterRequest):
    return clustering_service.cluster(request.articles, request.threshold)


@router.post("/similar", response_model=list[SimilarEventResult])
def similar_events(request: SimilarEventRequest):
    return []
