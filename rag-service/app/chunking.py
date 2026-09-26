"""Text extraction and token-window chunking."""
import io
from dataclasses import dataclass
from typing import Callable

from pypdf import PdfReader

# Returns the (start, end) character span of every token in the text, in order.
TokenSpans = Callable[[str], list[tuple[int, int]]]

PDF_TYPES = {"application/pdf"}
TEXT_TYPES = {"text/plain", "text/markdown", "text/x-markdown"}


class UnsupportedDocument(ValueError):
    pass


@dataclass
class Page:
    number: int  # 1-based
    text: str


@dataclass
class Chunk:
    index: int
    text: str
    token_count: int
    page_number: int


def extract_pages(data: bytes, content_type: str | None, filename: str | None) -> list[Page]:
    name = (filename or "").lower()
    if content_type in PDF_TYPES or name.endswith(".pdf"):
        reader = PdfReader(io.BytesIO(data))
        pages = [Page(i + 1, (p.extract_text() or "").strip()) for i, p in enumerate(reader.pages)]
    elif content_type in TEXT_TYPES or name.endswith((".txt", ".md")):
        pages = [Page(1, data.decode("utf-8", errors="replace").strip())]
    else:
        raise UnsupportedDocument(f"Unsupported file type {content_type or name!r}; upload a PDF, .txt or .md file")
    return [p for p in pages if p.text]


def chunk_pages(pages: list[Page], token_spans: TokenSpans, size: int, overlap: int) -> list[Chunk]:
    """Slides a window of `size` tokens (stepping `size - overlap`) over the whole document.

    Chunks are cut on token boundaries but their text is sliced from the original string, so casing,
    punctuation and whitespace survive (decoding token ids would lower-case with uncased tokenizers).
    Chunks may span pages; `page_number` is the page the chunk starts on.
    """
    if not 0 <= overlap < size:
        raise ValueError("overlap must be >= 0 and smaller than size")

    separator = "\n\n"
    text_parts, page_starts, offset = [], [], 0
    for page in pages:
        page_starts.append((offset, page.number))
        text_parts.append(page.text)
        offset += len(page.text) + len(separator)
    text = separator.join(text_parts)

    spans = token_spans(text)
    chunks: list[Chunk] = []
    step = size - overlap
    start = 0
    while start < len(spans):
        window = spans[start:start + size]
        char_start, char_end = window[0][0], window[-1][1]
        page_number = max(n for s, n in page_starts if s <= char_start)
        chunks.append(Chunk(len(chunks), text[char_start:char_end].strip(), len(window), page_number))
        if start + size >= len(spans):
            break
        start += step
    return chunks
