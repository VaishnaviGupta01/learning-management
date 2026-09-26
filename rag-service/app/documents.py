"""Phase 11 - document ingestion: extract -> chunk -> embed -> index. The backend owns the Document /
DocumentChunk rows; this service returns the chunks so the backend can write them."""
import logging

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from pydantic import BaseModel

from . import config
from .chunking import UnsupportedDocument, chunk_pages, extract_pages
from .embeddings import Embedder, get_embedder
from .vector_store import VectorStore, get_store

log = logging.getLogger(__name__)

router = APIRouter(prefix="/api/documents", tags=["documents"])


class ChunkOut(BaseModel):
    chunk_index: int
    content: str
    token_count: int
    page_number: int
    embedding_id: str


class IngestResponse(BaseModel):
    document_id: int
    course_id: int
    pages: int
    chunk_count: int
    chunks: list[ChunkOut]


class DeleteResponse(BaseModel):
    document_id: int
    removed_chunks: int


def embedding_id(course_id: int, vector_id: int) -> str:
    return f"{course_id}:{vector_id}"


@router.post("/ingest", response_model=IngestResponse)
def ingest(file: UploadFile = File(...), document_id: int = Form(...), course_id: int = Form(...),
           title: str = Form(...),
           embedder: Embedder = Depends(get_embedder), store: VectorStore = Depends(get_store)) -> IngestResponse:
    data = file.file.read(config.MAX_UPLOAD_BYTES + 1)
    if len(data) > config.MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="File is too large")
    try:
        pages = extract_pages(data, file.content_type, file.filename)
    except UnsupportedDocument as e:
        raise HTTPException(status_code=415, detail=str(e))
    except Exception:
        log.exception("Could not read %s", file.filename)
        raise HTTPException(status_code=422, detail="The file could not be read; is it a valid PDF?")
    if not pages:
        raise HTTPException(status_code=422,
                            detail="No extractable text found (scanned PDFs need OCR before upload)")

    chunks = chunk_pages(pages, embedder.token_spans, config.CHUNK_SIZE, config.CHUNK_OVERLAP)
    vectors = embedder.embed_documents([c.text for c in chunks])
    vector_ids = store.add(course_id, document_id, title, chunks, vectors)
    log.info("Ingested document %s (course %s): %d pages, %d chunks", document_id, course_id, len(pages), len(chunks))

    return IngestResponse(
        document_id=document_id, course_id=course_id, pages=len(pages), chunk_count=len(chunks),
        chunks=[ChunkOut(chunk_index=c.index, content=c.text, token_count=c.token_count, page_number=c.page_number,
                         embedding_id=embedding_id(course_id, vid)) for c, vid in zip(chunks, vector_ids)],
    )


@router.delete("/{document_id}", response_model=DeleteResponse)
def delete(document_id: int, course_id: int, store: VectorStore = Depends(get_store)) -> DeleteResponse:
    return DeleteResponse(document_id=document_id, removed_chunks=store.remove_document(course_id, document_id))
