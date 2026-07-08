import uvicorn
from fastapi import FastAPI

app = FastAPI(title="Intelligence Analysis Service", version="0.1.0")

from src.api.analysis_api import router as analysis_router
app.include_router(analysis_router)


@app.get("/internal/health")
def health():
    return {"status": "ok"}


if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=8003)
