from fastapi import FastAPI

from api.news_api import router as news_router
from api.social_media_api import router as social_router

app = FastAPI(
    title="Crawler Gateway",
    description="Internal crawler integration service",
    version="0.1.0",
)
app.include_router(news_router)
app.include_router(social_router)


@app.get("/internal/health", tags=["system"])
def health() -> dict[str, str]:
    return {"status": "up"}
