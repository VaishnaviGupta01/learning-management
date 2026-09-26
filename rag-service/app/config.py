"""LLM settings. The API key is read from the environment of this service only and is never returned."""
import os

# LLM_API_KEY is accepted as an alias; the Anthropic SDK itself reads ANTHROPIC_API_KEY.
API_KEY = os.getenv("LLM_API_KEY") or os.getenv("ANTHROPIC_API_KEY") or None

LLM_MODEL = os.getenv("LLM_MODEL", "claude-opus-5")
LLM_MAX_TOKENS = int(os.getenv("LLM_MAX_TOKENS", "16000"))
# low | medium | high | xhigh | max - chat rarely needs more than medium
LLM_EFFORT = os.getenv("LLM_EFFORT", "medium")
LLM_TIMEOUT_SECONDS = float(os.getenv("LLM_TIMEOUT_SECONDS", "90"))
# Server-side refusal fallback (re-runs a declined request on Anthropic's recommended fallback model)
LLM_REFUSAL_FALLBACK = os.getenv("LLM_REFUSAL_FALLBACK", "true").lower() == "true"

MAX_HISTORY_TURNS = 20


def llm_configured() -> bool:
    return API_KEY is not None
