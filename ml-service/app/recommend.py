"""Phase 8 - recommendation ranking.

priority = 0.5 * (1 - knowledge) + 0.3 * importance + 0.2 * urgency   (weights in config.RECOMMEND_WEIGHTS)

This service only ranks the candidates it is given. Prerequisite gating (a topic whose prerequisites
are not complete is not recommended directly) is done by the backend, which owns the topic graph.
"""
from typing import Optional

from fastapi import APIRouter
from pydantic import BaseModel, Field

from . import config

router = APIRouter(prefix="/api", tags=["recommendations"])


class Candidate(BaseModel):
    topic_id: int
    knowledge_score: float = Field(ge=0, le=1)
    importance: float = Field(default=0.5, ge=0, le=1)
    urgency: float = Field(default=0.0, ge=0, le=1)


class RecommendRequest(BaseModel):
    topics: list[Candidate]
    top_k: Optional[int] = Field(default=None, ge=1)


class RankedTopic(BaseModel):
    topic_id: int
    rank: int
    priority: float
    reason: str


class RecommendResponse(BaseModel):
    recommendations: list[RankedTopic]
    weights: dict[str, float]


def priority(c: Candidate) -> float:
    w = config.RECOMMEND_WEIGHTS
    return round(w["knowledge_gap"] * (1 - c.knowledge_score)
                 + w["importance"] * c.importance
                 + w["urgency"] * c.urgency, 4)


def reason(c: Candidate) -> str:
    """Human-readable explanation built from the factors that contribute most."""
    w = config.RECOMMEND_WEIGHTS
    contributions = {
        "gap": w["knowledge_gap"] * (1 - c.knowledge_score),
        "importance": w["importance"] * c.importance,
        "urgency": w["urgency"] * c.urgency,
    }
    parts = []
    for factor, _ in sorted(contributions.items(), key=lambda kv: kv[1], reverse=True):
        if factor == "gap" and c.knowledge_score < 0.6:
            parts.append(f"low mastery ({round(c.knowledge_score * 100)}%)")
        elif factor == "importance" and c.importance >= 0.7:
            parts.append("high-importance topic")
        elif factor == "urgency" and c.urgency >= 0.5:
            parts.append("revision overdue")
    if not parts:
        parts.append("keep practising to consolidate")
    text = ", ".join(parts[:2])
    return text[0].upper() + text[1:]


def rank(candidates: list[Candidate], top_k: Optional[int] = None) -> list[RankedTopic]:
    # sorted() is stable, so equal priorities keep the caller's order
    ordered = sorted(candidates, key=priority, reverse=True)
    if top_k is not None:
        ordered = ordered[:top_k]
    return [RankedTopic(topic_id=c.topic_id, rank=i + 1, priority=priority(c), reason=reason(c))
            for i, c in enumerate(ordered)]


@router.post("/recommend", response_model=RecommendResponse)
def recommend(request: RecommendRequest) -> RecommendResponse:
    return RecommendResponse(recommendations=rank(request.topics, request.top_k),
                             weights=config.RECOMMEND_WEIGHTS)
