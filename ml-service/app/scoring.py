"""Knowledge scoring (Phase 7) - pure functions, no HTTP."""
from dataclasses import dataclass
from typing import Mapping, Optional

from . import config


@dataclass(frozen=True)
class KnowledgeResult:
    score: float
    classification: str
    components_used: list[str]


def classify(score: float, thresholds: Mapping[str, float] | None = None) -> str:
    """Highest band whose lower bound (inclusive) the score reaches; WEAK below all of them."""
    thresholds = config.KNOWLEDGE_THRESHOLDS if thresholds is None else thresholds
    for band, lower in sorted(thresholds.items(), key=lambda kv: kv[1], reverse=True):
        if score >= lower:
            return band
    return "WEAK"


def knowledge_score(components: Mapping[str, Optional[float]],
                    weights: Mapping[str, float] | None = None) -> KnowledgeResult:
    """Weighted average of the available components (each 0..1).

    Missing (None) components are left out and the remaining weights renormalised, so a student with only a
    diagnostic result is scored on that alone instead of being pulled towards 0. No evidence -> NOT_STARTED.
    """
    weights = config.KNOWLEDGE_WEIGHTS if weights is None else weights
    available = {name: components.get(name) for name in weights
                 if components.get(name) is not None and weights[name] > 0}
    if not available:
        return KnowledgeResult(0.0, "NOT_STARTED", [])
    total_weight = sum(weights[name] for name in available)
    score = round(sum(weights[name] * value for name, value in available.items()) / total_weight, 4)
    return KnowledgeResult(score, classify(score), list(available))
