"""Fixed sample students used by the scoring and ranking tests.

Each student has per-topic evidence (accuracy ratios 0..1, None = no evidence of that kind) that mirrors what
the backend derives from real attempts, plus the topic importance / revision urgency the backend sends.
"""
import pytest

SAMPLE_STUDENTS = {
    # consistently strong
    "asha": {
        "Recursion": {"diagnostic": 0.9, "recent_quiz": 0.85, "practice": 0.95, "revision": 0.8},
        "Arrays": {"diagnostic": 1.0, "recent_quiz": 0.9, "practice": 0.9, "revision": None},
    },
    # improving: weak diagnostic, good recent work
    "ben": {
        "Recursion": {"diagnostic": 0.2, "recent_quiz": 0.8, "practice": 0.9, "revision": 0.4},
        "Arrays": {"diagnostic": 0.5, "recent_quiz": None, "practice": 0.6, "revision": None},
    },
    # struggling everywhere
    "chen": {
        "Recursion": {"diagnostic": 0.1, "recent_quiz": 0.2, "practice": 0.3, "revision": 0.0},
        "Arrays": {"diagnostic": 0.3, "recent_quiz": 0.4, "practice": 0.2, "revision": None},
    },
    # only took the diagnostic
    "dana": {
        "Recursion": {"diagnostic": 0.7, "recent_quiz": None, "practice": None, "revision": None},
        "Arrays": {"diagnostic": None, "recent_quiz": None, "practice": None, "revision": None},
    },
}

# topic -> (topic_id, importance)
TOPICS = {"Recursion": (1, 0.9), "Arrays": (2, 0.4), "Trees": (3, 0.7)}


@pytest.fixture
def students():
    return SAMPLE_STUDENTS


@pytest.fixture
def topics():
    return TOPICS
