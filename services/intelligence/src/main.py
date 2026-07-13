import uvicorn
import logging
from fastapi import FastAPI
from dotenv import load_dotenv

load_dotenv()

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(name)s] %(levelname)s: %(message)s")

app = FastAPI(title="Intelligence Analysis Service", version="0.1.0")

from src.api.analysis_api import router as analysis_router
from src.api.clustering_api import router as clustering_router
from src.api.topic_api import router as topic_router
from src.api.trend_api import router as trend_router
from src.api.summary_api import router as summary_router
from src.api.propagation_api import router as propagation_router
app.include_router(analysis_router)
app.include_router(clustering_router)
app.include_router(topic_router)
app.include_router(trend_router)
app.include_router(summary_router)
app.include_router(propagation_router)


@app.get("/internal/health")
def health():
    return {"status": "ok"}


if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=8003)
