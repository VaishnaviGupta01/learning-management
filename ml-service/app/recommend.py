"""Phase 8 - recommendation ranking HTTP API. The ranking itself lives in ranking.py.

This service only ranks the candidates it is given. Prerequisite gating (a topic whose prerequisites
are not complete is not recommended directly) is done by the backend, which owns the topic graph.
"""
from typing import Optional

from fastapi import APIRouter
from pydantic import BaseModel, Field

from . import config
from .ranking import Candidate as RankCandidate
from .ranking import rank

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


@router.post("/recommend", response_model=RecommendResponse)
def recommend(request: RecommendRequest) -> RecommendResponse:
    ranked = rank([RankCandidate(**c.model_dump()) for c in request.topics], request.top_k)
    return RecommendResponse(
        recommendations=[RankedTopic(topic_id=r.topic_id, rank=r.rank, priority=r.priority, reason=r.reason)
                         for r in ranked],
        weights=config.RECOMMEND_WEIGHTS)
