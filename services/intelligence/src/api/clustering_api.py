import logging

from fastapi import APIRouter

from src.model.clustering_model import (
    ClusterRequest,
    ClusterResponse,
    IncrementalClusterRequest,
    SimilarEventRequest,
    SimilarEventResult,
)
from src.service.clustering_service import ClusteringService

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/internal/event", tags=["event"])

clustering_service = ClusteringService()


@router.post("/cluster", response_model=ClusterResponse)
def cluster_articles(request: ClusterRequest):
    result = clustering_service.cluster(request.articles, request.threshold, request.min_cluster_size)
    for e in result.events:
        logger.info("[API] event_id=%d start_time=%r end_time=%r", e.event_id, e.start_time, e.end_time)
    return result


@router.post("/incremental-cluster", response_model=ClusterResponse)
def incremental_cluster_articles(request: IncrementalClusterRequest):
    """增量聚类：先匹配已有事件，剩余文章再聚类"""
    result = clustering_service.incremental_cluster(
        request.articles,
        request.existing_events,
        request.threshold,
        request.match_threshold,
        request.min_cluster_size,
    )
    for e in result.events:
        logger.info("[API] event_id=%d is_existing=%s start_time=%r end_time=%r",
                    e.event_id, e.is_existing, e.start_time, e.end_time)
    return result


@router.post("/similar", response_model=list[SimilarEventResult])
def similar_events(request: SimilarEventRequest):
    return []
