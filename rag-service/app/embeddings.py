"""Sentence-transformers embedder. The model is downloaded from Hugging Face on first use and cached
under HF_HOME (a Docker volume), so only the first ingestion needs internet access."""
import logging
import threading
from typing import Protocol

import numpy as np

from . import config

log = logging.getLogger(__name__)


class Embedder(Protocol):
    dimension: int

    def token_spans(self, text: str) -> list[tuple[int, int]]: ...

    def embed_documents(self, texts: list[str]) -> np.ndarray: ...

    def embed_query(self, text: str) -> np.ndarray: ...


class SentenceTransformerEmbedder:
    """L2-normalised embeddings, so inner product == cosine similarity."""

    def __init__(self, model_name: str, query_prefix: str = ""):
        from sentence_transformers import SentenceTransformer  # heavy import, deferred

        self.model = SentenceTransformer(model_name, device="cpu")
        self.tokenizer = self.model.tokenizer
        self.query_prefix = query_prefix
        self.dimension = self.model.get_embedding_dimension()
        self._lock = threading.Lock()

    def token_spans(self, text: str) -> list[tuple[int, int]]:
        encoded = self.tokenizer(text, add_special_tokens=False, return_offsets_mapping=True,
                                 truncation=False, verbose=False)
        return [tuple(span) for span in encoded["offset_mapping"]]

    def embed_documents(self, texts: list[str]) -> np.ndarray:
        with self._lock:
            return self.model.encode(texts, batch_size=32, normalize_embeddings=True,
                                     convert_to_numpy=True).astype("float32")

    def embed_query(self, text: str) -> np.ndarray:
        with self._lock:
            return self.model.encode([self.query_prefix + text], normalize_embeddings=True,
                                     convert_to_numpy=True).astype("float32")[0]


_load_lock = threading.Lock()
_embedder: Embedder | None = None


def get_embedder() -> Embedder:
    """Loads the model once (downloading it on first use); concurrent callers wait for the same load."""
    global _embedder
    if _embedder is None:
        with _load_lock:
            if _embedder is None:
                _embedder = SentenceTransformerEmbedder(config.EMBEDDING_MODEL, config.EMBEDDING_QUERY_PREFIX)
    return _embedder


def embedder_ready() -> bool:
    return _embedder is not None


def preload_in_background() -> None:
    """Starts downloading/loading the model at startup so the first upload does not wait for it."""
    def load():
        try:
            get_embedder()
            log.info("Embedding model %s ready", config.EMBEDDING_MODEL)
        except Exception:
            log.exception("Could not load embedding model %s", config.EMBEDDING_MODEL)

    threading.Thread(target=load, name="embedder-preload", daemon=True).start()
