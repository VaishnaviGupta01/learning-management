"""FAISS vector store, one index per course, persisted to disk next to a JSON metadata file."""
import json
import os
import threading
from dataclasses import asdict, dataclass
from functools import lru_cache

import faiss
import numpy as np

from . import config


@dataclass
class ChunkRecord:
    vector_id: int
    document_id: int
    title: str
    chunk_index: int
    page_number: int
    text: str


@dataclass
class Hit:
    score: float
    record: ChunkRecord


class CourseIndex:
    """IndexIDMap2 over an inner-product flat index; vectors must be L2-normalised (cosine)."""

    def __init__(self, directory: str, dimension: int):
        self.directory = directory
        self.index_path = os.path.join(directory, "index.faiss")
        self.meta_path = os.path.join(directory, "meta.json")
        if os.path.exists(self.index_path):
            self.index = faiss.read_index(self.index_path)
            with open(self.meta_path, encoding="utf-8") as f:
                meta = json.load(f)
            self.next_id = meta["next_id"]
            self.records = {int(k): ChunkRecord(**v) for k, v in meta["records"].items()}
        else:
            self.index = faiss.IndexIDMap2(faiss.IndexFlatIP(dimension))
            self.next_id = 1
            self.records: dict[int, ChunkRecord] = {}

    def add(self, document_id: int, title: str, chunks: list, vectors: np.ndarray) -> list[int]:
        ids = np.arange(self.next_id, self.next_id + len(chunks), dtype="int64")
        self.index.add_with_ids(vectors, ids)
        for vid, chunk in zip(ids.tolist(), chunks):
            self.records[vid] = ChunkRecord(vid, document_id, title, chunk.index, chunk.page_number, chunk.text)
        self.next_id += len(chunks)
        self._save()
        return ids.tolist()

    def remove_document(self, document_id: int) -> int:
        ids = [vid for vid, r in self.records.items() if r.document_id == document_id]
        if ids:
            self.index.remove_ids(np.array(ids, dtype="int64"))
            for vid in ids:
                del self.records[vid]
            self._save()
        return len(ids)

    def search(self, vector: np.ndarray, k: int) -> list[Hit]:
        if self.index.ntotal == 0:
            return []
        scores, ids = self.index.search(vector.reshape(1, -1).astype("float32"), min(k, self.index.ntotal))
        return [Hit(float(s), self.records[int(i)]) for s, i in zip(scores[0], ids[0]) if i != -1]

    @property
    def size(self) -> int:
        return self.index.ntotal

    def _save(self):
        os.makedirs(self.directory, exist_ok=True)
        tmp = self.index_path + ".tmp"
        faiss.write_index(self.index, tmp)
        os.replace(tmp, self.index_path)
        with open(self.meta_path + ".tmp", "w", encoding="utf-8") as f:
            json.dump({"next_id": self.next_id,
                       "records": {str(k): asdict(v) for k, v in self.records.items()}}, f)
        os.replace(self.meta_path + ".tmp", self.meta_path)


class VectorStore:
    def __init__(self, root: str):
        self.root = root
        self._indexes: dict[int, CourseIndex] = {}
        self._lock = threading.RLock()

    def _course(self, course_id: int, dimension: int | None) -> CourseIndex | None:
        directory = os.path.join(self.root, f"course_{course_id}")
        if course_id not in self._indexes:
            if dimension is None and not os.path.exists(os.path.join(directory, "index.faiss")):
                return None
            self._indexes[course_id] = CourseIndex(directory, dimension or 0)
        return self._indexes[course_id]

    def add(self, course_id: int, document_id: int, title: str, chunks: list, vectors: np.ndarray) -> list[int]:
        with self._lock:
            index = self._course(course_id, vectors.shape[1])
            index.remove_document(document_id)  # re-ingesting replaces the old chunks
            return index.add(document_id, title, chunks, vectors)

    def remove_document(self, course_id: int, document_id: int) -> int:
        with self._lock:
            index = self._course(course_id, None)
            return index.remove_document(document_id) if index else 0

    def search(self, course_id: int, vector: np.ndarray, k: int) -> list[Hit]:
        with self._lock:
            index = self._course(course_id, None)
            return index.search(vector, k) if index else []

    def size(self, course_id: int) -> int:
        with self._lock:
            index = self._course(course_id, None)
            return index.size if index else 0


@lru_cache(maxsize=1)
def get_store() -> VectorStore:
    return VectorStore(config.VECTOR_STORE_DIR)
