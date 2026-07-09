from fastapi import APIRouter

from src.model.topic_model import TopicClassifyRequest, TopicClassifyResponse
from src.service.topic_classifier import TopicClassifier

router = APIRouter(prefix="/internal/topic", tags=["topic"])

topic_classifier = TopicClassifier(n_topics=8)


@router.post("/classify", response_model=TopicClassifyResponse)
def classify_events(request: TopicClassifyRequest):
    events = [e.model_dump() for e in request.events]
    classified = topic_classifier.classify(events)
    return TopicClassifyResponse(events=classified)
