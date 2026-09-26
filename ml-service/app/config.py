"""Tunable model parameters.

Each dict can be overridden without code changes by setting an environment variable
with the same name to a JSON object, e.g.
    KNOWLEDGE_WEIGHTS='{"diagnostic": 0.5, "recent_quiz": 0.3, "practice": 0.2, "revision": 0.0}'
"""
import json
import os


def _load(name: str, default: dict) -> dict:
    raw = os.getenv(name)
    if not raw:
        return dict(default)
    override = json.loads(raw)
    unknown = set(override) - set(default)
    if unknown:
        raise ValueError(f"{name} has unknown keys: {sorted(unknown)}")
    return {**default, **override}


# Phase 7: knowledge score = weighted sum of per-topic accuracy ratios (each 0..1).
KNOWLEDGE_WEIGHTS = _load("KNOWLEDGE_WEIGHTS", {
    "diagnostic": 0.4,
    "recent_quiz": 0.3,
    "practice": 0.2,
    "revision": 0.1,
})

# Lower bound (inclusive) of each band; anything below the last bound is WEAK.
KNOWLEDGE_THRESHOLDS = _load("KNOWLEDGE_THRESHOLDS", {
    "STRONG": 0.80,
    "MODERATE": 0.60,
    "NEEDS_PRACTICE": 0.40,
})

# Phase 8: priority = 0.5 * (1 - knowledge) + 0.3 * importance + 0.2 * urgency
RECOMMEND_WEIGHTS = _load("RECOMMEND_WEIGHTS", {
    "knowledge_gap": 0.5,
    "importance": 0.3,
    "urgency": 0.2,
})
