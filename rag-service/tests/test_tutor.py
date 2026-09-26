"""Tutor endpoint tests with a fake Anthropic client - no network, no API key needed."""
from types import SimpleNamespace

import anthropic
import httpx2
import pytest
from fastapi.testclient import TestClient

from app import config, tutor
from app.main import app


class FakeMessages:
    def __init__(self, response=None, error=None):
        self.response = response
        self.error = error
        self.calls = []

    def create(self, **params):
        self.calls.append(params)
        if self.error:
            raise self.error
        return self.response


def fake_response(text="Recursion is a function calling itself.", stop_reason="end_turn", stop_details=None):
    return SimpleNamespace(
        content=[SimpleNamespace(type="thinking", thinking=""), SimpleNamespace(type="text", text=text)],
        model="claude-opus-5",
        stop_reason=stop_reason,
        stop_details=stop_details,
        usage=SimpleNamespace(input_tokens=321, output_tokens=45),
    )


@pytest.fixture
def fake():
    messages = FakeMessages(response=fake_response())
    client = SimpleNamespace(beta=SimpleNamespace(messages=messages))
    app.dependency_overrides[tutor.get_client] = lambda: client
    yield messages
    app.dependency_overrides.clear()


client = TestClient(app)

BODY = {
    "message": "Can you explain recursion?",
    "history": [
        {"role": "assistant", "content": "Hi! What shall we study?"},  # leading assistant turn is dropped
        {"role": "user", "content": "I'm stuck on trees"},
        {"role": "assistant", "content": "Trees build on recursion."},
    ],
    "context": {
        "student_name": "Sam",
        "course_title": "Data Structures",
        "topic_title": "Recursion",
        "topic_knowledge": {"topic_title": "Recursion", "classification": "WEAK", "mastery_score": 0.25},
        "weak_topics": [{"topic_title": "Arrays", "classification": "NEEDS_PRACTICE"}],
    },
}


def test_chat_calls_claude_with_context_and_history(fake):
    res = client.post("/api/tutor/chat", json=BODY)
    assert res.status_code == 200, res.text
    body = res.json()
    assert body["reply"] == "Recursion is a function calling itself."  # thinking block skipped
    assert body["model"] == "claude-opus-5"
    assert body["refused"] is False
    assert (body["input_tokens"], body["output_tokens"]) == (321, 45)

    params = fake.calls[0]
    assert params["model"] == config.LLM_MODEL
    assert params["max_tokens"] == config.LLM_MAX_TOKENS
    assert params["output_config"] == {"effort": config.LLM_EFFORT}
    assert params["betas"] == ["server-side-fallback-2026-07-01"]
    assert params["fallbacks"] == "default"
    assert "thinking" not in params and "temperature" not in params

    context_block = params["system"][1]["text"]
    assert "Course: Data Structures" in context_block
    assert "Knowledge level for this topic: WEAK (mastery 25%)" in context_block
    assert "Arrays (NEEDS_PRACTICE)" in context_block
    assert params["messages"] == [
        {"role": "user", "content": "I'm stuck on trees"},
        {"role": "assistant", "content": "Trees build on recursion."},
        {"role": "user", "content": "Can you explain recursion?"},
    ]


def test_refusal_returns_safe_reply(fake):
    fake.response = fake_response(text="", stop_reason="refusal",
                                  stop_details=SimpleNamespace(category="cyber", explanation="..."))
    body = client.post("/api/tutor/chat", json=BODY).json()
    assert body["refused"] is True
    assert body["stop_reason"] == "refusal"
    assert "can't help" in body["reply"]


def test_connection_error_maps_to_502(fake):
    fake.error = anthropic.APIConnectionError(request=httpx2.Request("POST", "https://api.anthropic.com/v1/messages"))
    res = client.post("/api/tutor/chat", json=BODY)
    assert res.status_code == 502
    assert "unavailable" in res.json()["detail"]


def test_not_configured_returns_503(monkeypatch):
    monkeypatch.setattr(config, "API_KEY", None)
    tutor.get_client.cache_clear()
    res = client.post("/api/tutor/chat", json=BODY)
    assert res.status_code == 503
    assert client.get("/api/health").json()["llm_configured"] is False


def test_validation(fake):
    assert client.post("/api/tutor/chat", json={"message": ""}).status_code == 422
    assert client.post("/api/tutor/chat", json={"message": "x" * 4001}).status_code == 422
