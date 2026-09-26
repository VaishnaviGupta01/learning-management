import pytest

from app.ranking import Candidate, priority, rank, reason
from app.scoring import knowledge_score


def candidates_for(student, students, topics, urgency=None):
    """Builds ranking candidates for a sample student: knowledge from scoring.py, importance from TOPICS."""
    urgency = urgency or {}
    out = []
    for name, (topic_id, importance) in topics.items():
        evidence = students[student].get(name, {})
        score = knowledge_score(evidence).score if evidence else 0.0
        out.append(Candidate(topic_id, score, importance, urgency.get(name, 0.0)))
    return out


def test_struggling_student_gets_important_weak_topic_first(students, topics):
    ranked = rank(candidates_for("chen", students, topics))
    # Recursion: 0.5*(1-0.16) + 0.3*0.9 = 0.69; Trees (no evidence): 0.5 + 0.21 = 0.71; Arrays: 0.3444 + 0.12 = 0.4644
    assert [r.topic_id for r in ranked] == [3, 1, 2]
    assert ranked[0].priority == pytest.approx(0.71)
    assert ranked[1].priority == pytest.approx(0.69)
    assert "low mastery (16%)" in ranked[1].reason.lower()


def test_strong_student_is_pointed_at_unstarted_topic(students, topics):
    ranked = rank(candidates_for("asha", students, topics))
    assert ranked[0].topic_id == 3                      # Trees: never studied
    assert ranked[-1].topic_id == 2                     # Arrays: strong and less important
    assert ranked[-1].reason == "Keep practising to consolidate"


def test_overdue_revision_can_outrank_a_weaker_topic(students, topics):
    base = rank(candidates_for("ben", students, topics))
    boosted = rank(candidates_for("ben", students, topics, urgency={"Arrays": 1.0}))
    arrays_rank = lambda rs: next(r.rank for r in rs if r.topic_id == 2)
    assert arrays_rank(boosted) < arrays_rank(base)
    assert "revision overdue" in next(r for r in boosted if r.topic_id == 2).reason.lower()


def test_top_k_and_rank_numbers(students, topics):
    ranked = rank(candidates_for("ben", students, topics), top_k=2)
    assert [r.rank for r in ranked] == [1, 2]


def test_ties_keep_input_order():
    ranked = rank([Candidate(7, 0.5), Candidate(3, 0.5)])
    assert [r.topic_id for r in ranked] == [7, 3]


def test_priority_formula_and_reason_wording():
    c = Candidate(1, knowledge_score=0.2, importance=0.9, urgency=0.0)
    assert priority(c) == pytest.approx(0.5 * 0.8 + 0.3 * 0.9)
    assert reason(c) == "Low mastery (20%), high-importance topic"
