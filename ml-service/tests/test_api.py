import pytest
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def score(**components):
    body = {"topics": [{"topic_id": 1, **components}]}
    res = client.post("/api/knowledge/score", json=body)
    assert res.status_code == 200, res.text
    return res.json()["results"][0]


# ---------------------------------------------------------------- knowledge

def test_weighted_formula_with_all_components():
    r = score(diagnostic=1.0, recent_quiz=0.5, practice=0.0, revision=1.0)
    # 0.4*1 + 0.3*0.5 + 0.2*0 + 0.1*1 = 0.65
    assert r["score"] == pytest.approx(0.65)
    assert r["classification"] == "MODERATE"


@pytest.mark.parametrize("value, band", [
    (1.00, "STRONG"),
    (0.80, "STRONG"),          # boundary is inclusive
    (0.7999, "MODERATE"),
    (0.60, "MODERATE"),
    (0.5999, "NEEDS_PRACTICE"),
    (0.40, "NEEDS_PRACTICE"),
    (0.3999, "WEAK"),
    (0.0, "WEAK"),
])
def test_all_four_bands_at_boundaries(value, band):
    r = score(diagnostic=value, recent_quiz=value, practice=value, revision=value)
    assert r["score"] == pytest.approx(value)
    assert r["classification"] == band


def test_missing_components_are_renormalised():
    # only diagnostic + practice: (0.4*0.9 + 0.2*0.3) / 0.6 = 0.7
    r = score(diagnostic=0.9, practice=0.3)
    assert r["score"] == pytest.approx(0.7)
    assert r["components_used"] == ["diagnostic", "practice"]


def test_no_evidence_is_not_started():
    r = score()
    assert r["score"] == 0.0
    assert r["classification"] == "NOT_STARTED"


def test_out_of_range_ratio_is_rejected():
    res = client.post("/api/knowledge/score", json={"topics": [{"topic_id": 1, "diagnostic": 1.5}]})
    assert res.status_code == 422


# ---------------------------------------------------------------- recommend

def test_priority_formula_and_ranking():
    body = {"topics": [
        {"topic_id": 1, "knowledge_score": 0.9, "importance": 0.2, "urgency": 0.0},  # strong, minor
        {"topic_id": 2, "knowledge_score": 0.2, "importance": 0.9, "urgency": 0.0},  # weak + important
        {"topic_id": 3, "knowledge_score": 0.2, "importance": 0.3, "urgency": 0.0},  # weak, minor
        {"topic_id": 4, "knowledge_score": 0.7, "importance": 0.5, "urgency": 1.0},  # revision overdue
    ]}
    res = client.post("/api/recommend", json=body)
    assert res.status_code == 200, res.text
    recs = res.json()["recommendations"]

    # priorities: 2 -> 0.67, 4 -> 0.50, 3 -> 0.49, 1 -> 0.11
    assert [r["topic_id"] for r in recs] == [2, 4, 3, 1]
    assert recs[0]["priority"] == pytest.approx(0.5 * 0.8 + 0.3 * 0.9)
    assert recs[1]["priority"] == pytest.approx(0.5 * 0.3 + 0.3 * 0.5 + 0.2 * 1.0)
    assert recs[0]["rank"] == 1
    assert "low mastery" in recs[0]["reason"].lower()
    assert "high-importance" in recs[0]["reason"].lower()
    assert "revision overdue" in recs[1]["reason"].lower()


def test_top_k_and_defaults():
    body = {"topics": [{"topic_id": i, "knowledge_score": i / 10} for i in range(5)], "top_k": 2}
    recs = client.post("/api/recommend", json=body).json()["recommendations"]
    assert [r["topic_id"] for r in recs] == [0, 1]
    # defaults: importance 0.5, urgency 0 -> 0.5*1 + 0.3*0.5 = 0.65
    assert recs[0]["priority"] == pytest.approx(0.65)


def test_ties_keep_input_order():
    body = {"topics": [{"topic_id": 7, "knowledge_score": 0.5}, {"topic_id": 3, "knowledge_score": 0.5}]}
    recs = client.post("/api/recommend", json=body).json()["recommendations"]
    assert [r["topic_id"] for r in recs] == [7, 3]
