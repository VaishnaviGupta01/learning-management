"""RAG service: document ingestion, embeddings and grounded AI answers."""
import logging
import os
from contextlib import asynccontextmanager
from datetime import datetime, timezone

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from . import config, documents, embeddings, tutor

SERVICE_NAME = "rag-service"

logging.basicConfig(level=logging.INFO)


@asynccontextmanager
async def lifespan(_: FastAPI):
    if config.EMBEDDING_PRELOAD:
        embeddings.preload_in_background()
    yield


app = FastAPI(title="LMS RAG Service", version="0.1.0", lifespan=lifespan)
app.include_router(tutor.router)
app.include_router(documents.router)

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
        "embedding_model": config.EMBEDDING_MODEL,
        "embedding_model_ready": embeddings.embedder_ready(),
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }
