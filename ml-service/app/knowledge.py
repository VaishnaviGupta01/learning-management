"""Phase 7 - knowledge model: per-topic mastery score and classification band."""
from typing import Optional

from fastapi import APIRouter
from pydantic import BaseModel, Field

from . import config

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


def classify(score: float) -> str:
    for band, lower in sorted(config.KNOWLEDGE_THRESHOLDS.items(), key=lambda kv: kv[1], reverse=True):
        if score >= lower:
            return band
    return "WEAK"


def score_topic(evidence: TopicEvidence) -> TopicKnowledge:
    """Weighted average of the available components.

    Missing components are left out and the remaining weights are renormalised, so a student
    with only a diagnostic result is scored on that alone instead of being pulled towards 0.
    """
    available = {
        name: value
        for name in config.KNOWLEDGE_WEIGHTS
        if (value := getattr(evidence, name)) is not None and config.KNOWLEDGE_WEIGHTS[name] > 0
    }
    if not available:
        return TopicKnowledge(topic_id=evidence.topic_id, score=0.0,
                              classification="NOT_STARTED", components_used=[])

    total_weight = sum(config.KNOWLEDGE_WEIGHTS[name] for name in available)
    score = sum(config.KNOWLEDGE_WEIGHTS[name] * value for name, value in available.items()) / total_weight
    score = round(score, 4)
    return TopicKnowledge(topic_id=evidence.topic_id, score=score,
                          classification=classify(score), components_used=list(available))


@router.post("/score", response_model=KnowledgeResponse)
def score(request: KnowledgeRequest) -> KnowledgeResponse:
    return KnowledgeResponse(
        results=[score_topic(t) for t in request.topics],
        weights=config.KNOWLEDGE_WEIGHTS,
        thresholds=config.KNOWLEDGE_THRESHOLDS,
    )
