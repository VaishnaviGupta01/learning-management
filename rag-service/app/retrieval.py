"""Query-time retrieval for the tutor."""
from dataclasses import dataclass
from functools import lru_cache

from . import config
from .embeddings import get_embedder
from .vector_store import Hit, get_store


@dataclass
class Retrieval:
    indexed: bool       # the course has any material at all
    hits: list[Hit]     # top-k hits at or above the similarity threshold, best first


class Retriever:
    def __init__(self, store, embedder_factory, top_k: int, min_similarity: float):
        self.store = store
        self.embedder_factory = embedder_factory  # the model is only loaded for courses that have material
        self.top_k = top_k
        self.min_similarity = min_similarity

    def retrieve(self, course_id: int, query: str) -> Retrieval:
        if self.store.size(course_id) == 0:
            return Retrieval(indexed=False, hits=[])
        vector = self.embedder_factory().embed_query(query)
        hits = self.store.search(course_id, vector, self.top_k)
        return Retrieval(indexed=True, hits=[h for h in hits if h.score >= self.min_similarity])


@lru_cache(maxsize=1)
def get_retriever() -> Retriever:
    return Retriever(get_store(), get_embedder, config.RAG_TOP_K, config.RAG_MIN_SIMILARITY)
