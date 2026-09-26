"""Phase 11: extraction, chunking, vector store, ingestion endpoint and grounded tutor answers."""
import io
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient
from pypdf import PdfWriter
from pypdf.generic import DecodedStreamObject, DictionaryObject, NameObject

from app import config, tutor
from app.chunking import Page, UnsupportedDocument, chunk_pages, extract_pages
from app.embeddings import get_embedder
from app.main import app
from app.retrieval import Retriever, get_retriever
from app.vector_store import VectorStore, get_store

client = TestClient(app)


def words(n, start=0):
    return " ".join(f"w{i}" for i in range(start, start + n))


def pdf_with_pages(*texts: str) -> bytes:
    """Minimal real PDF with one text line per page (Helvetica), readable by pypdf."""
    writer = PdfWriter()
    for text in texts:
        page = writer.add_blank_page(width=612, height=792)
        font = DictionaryObject({NameObject("/Type"): NameObject("/Font"),
                                 NameObject("/Subtype"): NameObject("/Type1"),
                                 NameObject("/BaseFont"): NameObject("/Helvetica")})
        page[NameObject("/Resources")] = DictionaryObject(
            {NameObject("/Font"): DictionaryObject({NameObject("/F1"): writer._add_object(font)})})
        stream = DecodedStreamObject()
        stream.set_data(f"BT /F1 12 Tf 72 720 Td ({text}) Tj ET".encode())
        page[NameObject("/Contents")] = writer._add_object(stream)
    buf = io.BytesIO()
    writer.write(buf)
    return buf.getvalue()


# ---------------------------------------------------------------- extraction & chunking

def test_pdf_pages_are_extracted():
    pages = extract_pages(pdf_with_pages("Binary trees have two children", "Heaps are complete trees"),
                          "application/pdf", "notes.pdf")
    assert [p.number for p in pages] == [1, 2]
    assert "Binary trees" in pages[0].text and "Heaps" in pages[1].text


def test_unsupported_type_is_rejected():
    with pytest.raises(UnsupportedDocument):
        extract_pages(b"\x00\x01", "image/png", "diagram.png")


def test_chunks_have_size_and_overlap_in_tokens(fake_embedder):
    pages = [Page(1, words(1200))]
    chunks = chunk_pages(pages, fake_embedder.token_spans, size=500, overlap=50)
    # windows start at tokens 0, 450, 900 -> the last one holds the remaining 300
    assert [c.token_count for c in chunks] == [500, 500, 300]
    assert chunks[0].text.split()[-50:] == chunks[1].text.split()[:50]  # 50-token overlap
    assert chunks[1].text.split()[0] == "w450"
    assert chunks[2].text.split()[-1] == "w1199"


def test_chunks_keep_original_text_and_track_pages(fake_embedder):
    pages = [Page(1, "Recursion: a Function calls ITSELF."), Page(2, words(10))]
    chunks = chunk_pages(pages, fake_embedder.token_spans, size=6, overlap=1)
    assert chunks[0].text.startswith("Recursion: a Function calls ITSELF.")  # casing/punctuation kept
    assert chunks[0].page_number == 1
    assert chunks[-1].page_number == 2


# ---------------------------------------------------------------- vector store

def test_vector_store_persists_searches_and_removes(tmp_path, fake_embedder):
    store = VectorStore(str(tmp_path))
    chunks = chunk_pages([Page(1, "stacks are last in first out"), Page(2, "queues are first in first out")],
                         fake_embedder.token_spans, size=6, overlap=0)
    store.add(7, 100, "Notes", chunks, fake_embedder.embed_documents([c.text for c in chunks]))

    reopened = VectorStore(str(tmp_path))  # reload from disk
    hits = reopened.search(7, fake_embedder.embed_query("what is a queue first in first out"), 2)
    assert hits[0].record.page_number == 2 and hits[0].record.title == "Notes"
    assert hits[0].score > hits[1].score

    assert reopened.remove_document(7, 100) == 2
    assert reopened.size(7) == 0
    assert VectorStore(str(tmp_path)).search(99, fake_embedder.embed_query("x"), 3) == []  # unknown course


# ---------------------------------------------------------------- ingestion endpoint

@pytest.fixture
def rag(tmp_path, fake_embedder, monkeypatch):
    monkeypatch.setattr(config, "CHUNK_SIZE", 8)
    monkeypatch.setattr(config, "CHUNK_OVERLAP", 2)
    store = VectorStore(str(tmp_path))
    retriever = Retriever(store, lambda: fake_embedder, top_k=3, min_similarity=0.5)
    app.dependency_overrides[get_embedder] = lambda: fake_embedder
    app.dependency_overrides[get_store] = lambda: store
    app.dependency_overrides[get_retriever] = lambda: retriever
    yield SimpleNamespace(store=store, embedder=fake_embedder)
    app.dependency_overrides.clear()


def ingest(document_id, course_id, filename, data, content_type):
    return client.post("/api/documents/ingest",
                       data={"document_id": document_id, "course_id": course_id, "title": filename},
                       files={"file": (filename, data, content_type)})


def test_ingest_pdf_returns_chunks_for_the_backend(rag):
    pdf = pdf_with_pages("A binary heap is a complete binary tree", "Heap insert runs in logarithmic time")
    res = ingest(11, 3, "heaps.pdf", pdf, "application/pdf")
    assert res.status_code == 200, res.text
    body = res.json()
    assert body["pages"] == 2
    assert body["chunk_count"] == len(body["chunks"]) >= 2
    first = body["chunks"][0]
    assert first["chunk_index"] == 0 and first["page_number"] == 1
    assert first["embedding_id"].startswith("3:")
    assert rag.store.size(3) == body["chunk_count"]

    # re-ingesting the same document replaces its vectors instead of duplicating them
    ingest(11, 3, "heaps.pdf", pdf, "application/pdf")
    assert rag.store.size(3) == body["chunk_count"]

    assert client.delete("/api/documents/11", params={"course_id": 3}).json()["removed_chunks"] == body["chunk_count"]


def test_ingest_errors(rag):
    assert ingest(1, 1, "pic.png", b"\x89PNG", "image/png").status_code == 415
    assert ingest(1, 1, "broken.pdf", b"not a pdf", "application/pdf").status_code == 422
    assert ingest(1, 1, "blank.pdf", pdf_with_pages(""), "application/pdf").status_code == 422


# ---------------------------------------------------------------- grounded tutor

class FakeMessages:
    def __init__(self):
        self.calls = []

    def create(self, **params):
        self.calls.append(params)
        return SimpleNamespace(content=[SimpleNamespace(type="text", text="Heaps are complete trees [1].")],
                               model="claude-opus-5", stop_reason="end_turn", stop_details=None,
                               usage=SimpleNamespace(input_tokens=500, output_tokens=20))


@pytest.fixture
def llm():
    messages = FakeMessages()
    app.dependency_overrides[tutor.get_client] = lambda: SimpleNamespace(beta=SimpleNamespace(messages=messages))
    return messages


def test_grounded_answer_cites_retrieved_chunks(rag, llm):
    ingest(21, 5, "heaps.txt", b"A binary heap is a complete binary tree stored in an array", "text/plain")
    res = client.post("/api/tutor/chat", json={"message": "what is a binary heap",
                                               "context": {"course_id": 5, "course_title": "DSA"}})
    body = res.json()
    assert res.status_code == 200, res.text
    assert body["grounded"] is True and body["not_in_material"] is False
    assert body["sources"][0]["document_id"] == 21 and body["sources"][0]["ref"] == 1
    material = llm.calls[0]["system"][2]["text"]
    assert '<source id="1" document="heaps.txt" page="1">' in material
    assert "binary heap is a complete binary tree" in material
    assert "not covered in the course material" in material


def test_below_threshold_says_not_in_material_without_calling_llm(rag, llm):
    ingest(22, 6, "heaps.txt", b"A binary heap is a complete binary tree stored in an array", "text/plain")
    body = client.post("/api/tutor/chat", json={"message": "explain photosynthesis in plants",
                                                "context": {"course_id": 6}}).json()
    assert body["not_in_material"] is True and body["grounded"] is True
    assert "couldn't find this in the course material" in body["reply"]
    assert body["model"] is None and body["sources"] == []
    assert llm.calls == []


def test_course_without_material_answers_ungrounded(rag, llm):
    body = client.post("/api/tutor/chat", json={"message": "explain recursion", "context": {"course_id": 404}}).json()
    assert body["grounded"] is False and body["sources"] == []
    assert len(llm.calls[0]["system"]) == 2  # no material block
    assert rag.embedder.queries == []        # the embedding model is not even loaded
