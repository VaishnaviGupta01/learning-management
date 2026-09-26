"""Phase 7 - knowledge model HTTP API. The scoring itself lives in scoring.py."""
from typing import Optional

from fastapi import APIRouter
from pydantic import BaseModel, Field

from . import config
from .scoring import knowledge_score

router = APIRouter(prefix="/api/knowledge", tags=["knowledge"])

Ratio = Optional[float]


class TopicEvidence(BaseModel):
    """Per-topic accuracy ratios in [0, 1]. Null means "no evidence of this kind yet"."""
    topic_id: int
    diagnostic: Ratio = Field(default=None, ge=0, le=1)
    recent_quiz: Ratio = Field(default=None, ge=0, le=1)
    practice: Ratio = Field(default=None, ge=0, le=1)
    revision: Ratio = Field(default=None, ge=0, le=1)


class KnowledgeRequest(BaseModel):
    topics: list[TopicEvidence]


class TopicKnowledge(BaseModel):
    topic_id: int
    score: float
    classification: str
    components_used: list[str]


class KnowledgeResponse(BaseModel):
    results: list[TopicKnowledge]
    weights: dict[str, float]
    thresholds: dict[str, float]


@router.post("/score", response_model=KnowledgeResponse)
def score(request: KnowledgeRequest) -> KnowledgeResponse:
    results = []
    for t in request.topics:
        r = knowledge_score(t.model_dump(exclude={"topic_id"}))
        results.append(TopicKnowledge(topic_id=t.topic_id, score=r.score, classification=r.classification,
                                      components_used=r.components_used))
    return KnowledgeResponse(results=results, weights=config.KNOWLEDGE_WEIGHTS,
                             thresholds=config.KNOWLEDGE_THRESHOLDS)
