"""RAG service: document ingestion, embeddings and grounded AI answers."""
import os
from datetime import datetime, timezone

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from . import config, tutor

SERVICE_NAME = "rag-service"

app = FastAPI(title="LMS RAG Service", version="0.1.0")
app.include_router(tutor.router)

app.add_middleware(
    CORSMiddleware,
    allow_origins=[o.strip() for o in os.getenv(
        "CORS_ALLOWED_ORIGINS", "http://localhost:5173,http://localhost:3000").split(",")],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/api/health")
def health():
    return {
        "status": "UP",
        "service": SERVICE_NAME,
        "llm_configured": config.llm_configured(),
        "llm_model": config.LLM_MODEL,
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }
