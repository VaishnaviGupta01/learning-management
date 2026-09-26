"""Service settings. The API key is read from the environment of this service only and is never returned."""
import os

# ---------------------------------------------------------------- LLM
# LLM_API_KEY is accepted as an alias; the Anthropic SDK itself reads ANTHROPIC_API_KEY.
API_KEY = os.getenv("LLM_API_KEY") or os.getenv("ANTHROPIC_API_KEY") or None

LLM_MODEL = os.getenv("LLM_MODEL", "claude-opus-5")
LLM_MAX_TOKENS = int(os.getenv("LLM_MAX_TOKENS", "16000"))
# low | medium | high | xhigh | max - chat rarely needs more than medium
LLM_EFFORT = os.getenv("LLM_EFFORT", "medium")
LLM_TIMEOUT_SECONDS = float(os.getenv("LLM_TIMEOUT_SECONDS", "90"))
# Server-side refusal fallback (re-runs a declined request on Anthropic's recommended fallback model)
LLM_REFUSAL_FALLBACK = os.getenv("LLM_REFUSAL_FALLBACK", "true").lower() == "true"

MAX_HISTORY_TURNS = 20

# ---------------------------------------------------------------- RAG
# bge-small handles 512 tokens per input, so ~500-token chunks are embedded whole
# (all-MiniLM-L6-v2 stops at 256 and would silently drop half of every chunk).
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "BAAI/bge-small-en-v1.5")
# bge models retrieve better when queries (not passages) carry this instruction; other models get none
EMBEDDING_QUERY_PREFIX = os.getenv(
    "EMBEDDING_QUERY_PREFIX",
    "Represent this sentence for searching relevant passages: " if "bge" in EMBEDDING_MODEL.lower() else "")
CHUNK_SIZE = int(os.getenv("CHUNK_SIZE", "500"))          # tokens
CHUNK_OVERLAP = int(os.getenv("CHUNK_OVERLAP", "50"))     # tokens
RAG_TOP_K = int(os.getenv("RAG_TOP_K", "5"))
# Cosine similarity below which a chunk is not considered evidence. Measured with bge-small on the sample
# notes: covered questions scored 0.555-0.74, unrelated ones 0.37-0.66, so no cut-off separates them fully.
# 0.55 stops clearly unrelated questions without an LLM call; borderline ones reach the model, which is
# instructed to say "not covered in the course material" rather than answer from general knowledge.
RAG_MIN_SIMILARITY = float(os.getenv("RAG_MIN_SIMILARITY", "0.55"))
VECTOR_STORE_DIR = os.getenv("VECTOR_STORE_DIR", "./data/vectors")
MAX_UPLOAD_BYTES = int(os.getenv("MAX_UPLOAD_BYTES", str(25 * 1024 * 1024)))
# load (and on first run download) the embedding model at startup instead of on the first upload
EMBEDDING_PRELOAD = os.getenv("EMBEDDING_PRELOAD", "true").lower() == "true"


def llm_configured() -> bool:
    return API_KEY is not None
