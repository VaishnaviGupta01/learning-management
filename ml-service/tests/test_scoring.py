import pytest

from app.scoring import classify, knowledge_score


@pytest.mark.parametrize("student, topic, expected_score, expected_band", [
    # 0.4*0.9 + 0.3*0.85 + 0.2*0.95 + 0.1*0.8 = 0.885
    ("asha", "Recursion", 0.885, "STRONG"),
    # revision missing -> (0.4*1 + 0.3*0.9 + 0.2*0.9) / 0.9 = 0.9444
    ("asha", "Arrays", 0.9444, "STRONG"),
    # 0.4*0.2 + 0.3*0.8 + 0.2*0.9 + 0.1*0.4 = 0.54
    ("ben", "Recursion", 0.54, "NEEDS_PRACTICE"),
    # (0.4*0.5 + 0.2*0.6) / 0.6 = 0.5333
    ("ben", "Arrays", 0.5333, "NEEDS_PRACTICE"),
    # 0.04 + 0.06 + 0.06 + 0 = 0.16
    ("chen", "Recursion", 0.16, "WEAK"),
    # (0.12 + 0.12 + 0.04) / 0.9 = 0.3111
    ("chen", "Arrays", 0.3111, "WEAK"),
    # diagnostic only
    ("dana", "Recursion", 0.7, "MODERATE"),
])
def test_sample_students(students, student, topic, expected_score, expected_band):
    result = knowledge_score(students[student][topic])
    assert result.score == pytest.approx(expected_score, abs=1e-4)
    assert result.classification == expected_band


def test_student_with_no_evidence_is_not_started(students):
    result = knowledge_score(students["dana"]["Arrays"])
    assert (result.score, result.classification, result.components_used) == (0.0, "NOT_STARTED", [])


def test_components_used_lists_only_available_evidence(students):
    assert knowledge_score(students["ben"]["Arrays"]).components_used == ["diagnostic", "practice"]


def test_band_ordering_is_monotonic(students):
    """A student's band never goes down when every component improves."""
    order = ["WEAK", "NEEDS_PRACTICE", "MODERATE", "STRONG"]
    bands = [knowledge_score({k: v for k in ("diagnostic", "recent_quiz", "practice", "revision")}).classification
             for v in (0.1, 0.3, 0.45, 0.65, 0.85)]
    assert [order.index(b) for b in bands] == sorted(order.index(b) for b in bands)


@pytest.mark.parametrize("score, band", [(0.8, "STRONG"), (0.7999, "MODERATE"), (0.6, "MODERATE"),
                                         (0.5999, "NEEDS_PRACTICE"), (0.4, "NEEDS_PRACTICE"), (0.3999, "WEAK")])
def test_classify_boundaries_are_inclusive(score, band):
    assert classify(score) == band


def test_custom_weights_and_zero_weight_components_are_ignored():
    result = knowledge_score({"diagnostic": 1.0, "practice": 0.0},
                             weights={"diagnostic": 1.0, "recent_quiz": 0.0, "practice": 0.0, "revision": 0.0})
    assert result.score == 1.0
    assert result.components_used == ["diagnostic"]
