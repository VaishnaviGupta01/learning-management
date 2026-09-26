"""Shared fakes: a deterministic bag-of-words embedder and whitespace tokenizer, so tests need no model download."""
import hashlib
import os
import re

os.environ.setdefault("EMBEDDING_PRELOAD", "false")  # never download the real model in tests

import numpy as np
import pytest

DIM = 64


class FakeEmbedder:
    """Hashes words into a fixed-size vector; texts sharing words get high cosine similarity."""
    dimension = DIM

    def __init__(self):
        self.queries = []

    def token_spans(self, text):
        return [m.span() for m in re.finditer(r"\S+", text)]

    def _embed(self, text):
        v = np.zeros(DIM, dtype="float32")
        for word in re.findall(r"[a-z]+", text.lower()):
            v[int(hashlib.md5(word.encode()).hexdigest(), 16) % DIM] += 1.0
        norm = np.linalg.norm(v)
        return v / norm if norm else v

    def embed_documents(self, texts):
        return np.stack([self._embed(t) for t in texts]).astype("float32")

    def embed_query(self, text):
        self.queries.append(text)
        return self._embed(text)


@pytest.fixture
def fake_embedder():
    return FakeEmbedder()
