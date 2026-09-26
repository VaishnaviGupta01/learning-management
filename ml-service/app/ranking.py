"""Recommendation ranking (Phase 8) - pure functions, no HTTP.

priority = 0.5 * (1 - knowledge) + 0.3 * importance + 0.2 * urgency   (weights in config.RECOMMEND_WEIGHTS)
"""
from dataclasses import dataclass
from typing import Mapping, Optional, Sequence


@dataclass(frozen=True)
class Candidate:
    topic_id: int
    knowledge_score: float
    importance: float = 0.5
    urgency: float = 0.0


@dataclass(frozen=True)
class Ranked:
    topic_id: int
    rank: int
    priority: float
    reason: str


def _weights(weights: Mapping[str, float] | None) -> Mapping[str, float]:
    from . import config
    return config.RECOMMEND_WEIGHTS if weights is None else weights


def priority(c: Candidate, weights: Mapping[str, float] | None = None) -> float:
    w = _weights(weights)
    return round(w["knowledge_gap"] * (1 - c.knowledge_score)
                 + w["importance"] * c.importance
                 + w["urgency"] * c.urgency, 4)


def reason(c: Candidate, weights: Mapping[str, float] | None = None) -> str:
    """Human-readable explanation from the factors that contribute most."""
    w = _weights(weights)
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


def rank(candidates: Sequence[Candidate], top_k: Optional[int] = None,
         weights: Mapping[str, float] | None = None) -> list[Ranked]:
    """Highest priority first; sorted() is stable, so equal priorities keep the caller's order."""
    ordered = sorted(candidates, key=lambda c: priority(c, weights), reverse=True)
    if top_k is not None:
        ordered = ordered[:top_k]
    return [Ranked(c.topic_id, i + 1, priority(c, weights), reason(c, weights)) for i, c in enumerate(ordered)]
