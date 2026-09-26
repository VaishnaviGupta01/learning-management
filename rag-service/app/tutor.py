"""AI tutor: one Claude call per turn with the student's course/topic and knowledge level (Phase 10).
When the course has uploaded material, the answer is grounded in retrieved chunks (Phase 11)."""
import logging
from functools import lru_cache
from html import escape
from typing import Literal, Optional

import anthropic
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from . import config
from .retrieval import Retriever, get_retriever
from .vector_store import Hit

log = logging.getLogger(__name__)

router = APIRouter(prefix="/api/tutor", tags=["tutor"])

FALLBACK_BETA = "server-side-fallback-2026-07-01"

NOT_IN_MATERIAL = ("I couldn't find this in the course material for this course, so I won't guess. "
                   "Try rephrasing the question, or ask your instructor whether it's covered.")

SYSTEM_PROMPT = """You are a patient, encouraging tutor inside a learning management system.

How to teach:
- Explain concepts clearly, building from what the student already knows. Use short worked examples.
- Match depth to the student's knowledge level for the topic: for WEAK or NEEDS_PRACTICE, start from \
fundamentals in plain language, one step at a time; for MODERATE, fill gaps and connect ideas; for STRONG, \
go deeper, discuss edge cases and trade-offs, and offer a challenge question.
- If the student is working on a graded quiz question, guide them with hints and questions rather than \
stating the answer.
- Stay on the course material. If a question is unrelated to learning, briefly redirect.
- If you are unsure about something, say so rather than guessing.
- Keep answers focused; use Markdown for code and short lists.

Latency-sensitive; begin your visible answer immediately."""


class KnowledgeLevel(BaseModel):
    topic_title: str
    classification: str
    mastery_score: Optional[float] = None


class StudentContext(BaseModel):
    course_id: Optional[int] = None  # enables retrieval over that course's uploaded material
    student_name: Optional[str] = None
    course_title: Optional[str] = None
    course_description: Optional[str] = None
    topic_title: Optional[str] = None
    topic_description: Optional[str] = None
    topic_knowledge: Optional[KnowledgeLevel] = None
    weak_topics: list[KnowledgeLevel] = Field(default_factory=list)


class ChatTurn(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=8000)


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=4000)
    history: list[ChatTurn] = Field(default_factory=list)
    context: StudentContext = Field(default_factory=StudentContext)


class SourceRef(BaseModel):
    ref: int  # the [n] number used in the reply
    document_id: int
    title: str
    chunk_index: int
    page_number: int
    score: float


class ChatResponse(BaseModel):
    reply: str
    model: Optional[str]  # None when no LLM call was made
    stop_reason: Optional[str]
    refused: bool
    input_tokens: int
    output_tokens: int
    grounded: bool = False          # the course has indexed material and retrieval was used
    not_in_material: bool = False   # retrieval found nothing relevant, so no answer was generated
    sources: list[SourceRef] = Field(default_factory=list)


@lru_cache(maxsize=1)
def get_client() -> anthropic.Anthropic:
    if not config.llm_configured():
        raise HTTPException(status_code=503, detail="AI tutor is not configured (set ANTHROPIC_API_KEY)")
    return anthropic.Anthropic(api_key=config.API_KEY, timeout=config.LLM_TIMEOUT_SECONDS)


def render_context(ctx: StudentContext) -> str:
    """Student context as a tagged block appended to the system prompt."""
    lines = []
    if ctx.student_name:
        lines.append(f"Student: {ctx.student_name}")
    if ctx.course_title:
        lines.append(f"Course: {ctx.course_title}")
    if ctx.course_description:
        lines.append(f"Course description: {ctx.course_description}")
    if ctx.topic_title:
        lines.append(f"Current topic: {ctx.topic_title}")
    if ctx.topic_description:
        lines.append(f"Topic description: {ctx.topic_description}")
    if ctx.topic_knowledge:
        k = ctx.topic_knowledge
        score = f" (mastery {k.mastery_score:.0%})" if k.mastery_score is not None else ""
        lines.append(f"Knowledge level for this topic: {k.classification}{score}")
    if ctx.weak_topics:
        weak = ", ".join(f"{w.topic_title} ({w.classification})" for w in ctx.weak_topics)
        lines.append(f"Topics the student currently struggles with: {weak}")
    if not lines:
        lines.append("No course context was provided.")
    return "<student_context>\n" + "\n".join(lines) + "\n</student_context>"


def build_messages(request: ChatRequest) -> list[dict]:
    history = request.history[-config.MAX_HISTORY_TURNS:]
    # the conversation must start with a user turn
    while history and history[0].role != "user":
        history = history[1:]
    return [{"role": t.role, "content": t.content} for t in history] + [
        {"role": "user", "content": request.message}
    ]


def render_material(hits: list[Hit]) -> str:
    """Retrieved chunks as numbered sources, plus the grounding rules."""
    sources = "\n".join(
        f'<source id="{i}" document="{escape(h.record.title)}" page="{h.record.page_number}">\n'
        f"{h.record.text}\n</source>"
        for i, h in enumerate(hits, start=1))
    return (f"<course_material>\n{sources}\n</course_material>\n\n"
            "Answer from the course material above. Cite the sources you use like [1] or [2]. "
            "If the material does not cover what the student asked, say that it is not covered in the "
            "course material instead of answering from general knowledge.")


def source_refs(hits: list[Hit]) -> list[SourceRef]:
    return [SourceRef(ref=i, document_id=h.record.document_id, title=h.record.title,
                      chunk_index=h.record.chunk_index, page_number=h.record.page_number, score=round(h.score, 4))
            for i, h in enumerate(hits, start=1)]


@router.post("/chat", response_model=ChatResponse)
def chat(request: ChatRequest, client: anthropic.Anthropic = Depends(get_client),
         retriever: Retriever = Depends(get_retriever)) -> ChatResponse:
    system = [
        {"type": "text", "text": SYSTEM_PROMPT},
        {"type": "text", "text": render_context(request.context)},
    ]
    hits: list[Hit] = []
    grounded = False
    if request.context.course_id is not None:
        retrieval = retriever.retrieve(request.context.course_id, request.message)
        if retrieval.indexed:
            grounded = True
            if not retrieval.hits:
                # nothing similar enough: say so explicitly rather than let the model guess
                return ChatResponse(reply=NOT_IN_MATERIAL, model=None, stop_reason=None, refused=False,
                                    input_tokens=0, output_tokens=0, grounded=True, not_in_material=True)
            hits = retrieval.hits
            system.append({"type": "text", "text": render_material(hits)})

    params = dict(
        model=config.LLM_MODEL,
        max_tokens=config.LLM_MAX_TOKENS,
        system=system,
        messages=build_messages(request),
        output_config={"effort": config.LLM_EFFORT},
    )
    if config.LLM_REFUSAL_FALLBACK:
        params.update(betas=[FALLBACK_BETA], fallbacks="default")

    try:
        response = client.beta.messages.create(**params)
    except anthropic.AuthenticationError:
        log.error("LLM credentials were rejected")
        raise HTTPException(status_code=503, detail="AI tutor credentials were rejected")
    except anthropic.RateLimitError:
        raise HTTPException(status_code=503, detail="AI tutor is busy, please retry shortly")
    except anthropic.BadRequestError as e:
        log.error("LLM rejected the request: %s", e.message)
        raise HTTPException(status_code=502, detail="AI tutor request was rejected")
    except anthropic.APIStatusError as e:
        log.error("LLM API error %s: %s", e.status_code, e.message)
        raise HTTPException(status_code=502, detail="AI tutor is temporarily unavailable")
    except anthropic.APIConnectionError:
        log.error("Could not reach the LLM API")
        raise HTTPException(status_code=502, detail="AI tutor is temporarily unavailable")

    refused = response.stop_reason == "refusal"
    if refused:
        category = response.stop_details.category if response.stop_details else None
        log.warning("Tutor request refused (category=%s)", category)
        reply = "I can't help with that request. Let's get back to your course material - what would you like to work on?"
    else:
        reply = "".join(block.text for block in response.content if block.type == "text").strip()

    return ChatResponse(
        reply=reply,
        model=response.model,
        stop_reason=response.stop_reason,
        refused=refused,
        input_tokens=response.usage.input_tokens,
        output_tokens=response.usage.output_tokens,
        grounded=grounded,
        not_in_material=False,
        sources=source_refs(hits),
    )
