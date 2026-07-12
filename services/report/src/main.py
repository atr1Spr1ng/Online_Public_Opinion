from dotenv import load_dotenv
load_dotenv()

import uvicorn
from fastapi import FastAPI

app = FastAPI(title="Report & QA Service", version="0.1.0")

from src.api.report_api import router as report_router
app.include_router(report_router)


@app.get("/internal/health")
def health():
    return {"status": "ok"}


if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=8004)
