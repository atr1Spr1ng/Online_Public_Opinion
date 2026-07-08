from fastapi import APIRouter
from src.model.clean_model import CleanRequest, CleanResponse, VectorizeRequest, VectorizeResponse
from src.service.clean_service import CleanService

router = APIRouter(prefix="/internal/content", tags=["content"])

clean_service = CleanService()


@router.post("/clean", response_model=CleanResponse)
def clean_article(request: CleanRequest):
    return clean_service.clean(request)


@router.post("/vectorize", response_model=VectorizeResponse)
def vectorize_texts(request: VectorizeRequest):
    vectors, vocab_size = clean_service.vectorize(request.texts)
    return VectorizeResponse(vectors=vectors, vocab_size=vocab_size)
