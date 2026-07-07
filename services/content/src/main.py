import uvicorn
from fastapi import FastAPI

app = FastAPI(title="Content Cleaning Service", version="0.1.0")

from src.api.clean_api import router as clean_router
app.include_router(clean_router)


@app.get("/internal/health")
def health():
    return {"status": "ok"}


if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=8002)

