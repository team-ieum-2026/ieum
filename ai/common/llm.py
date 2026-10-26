# 주인: AI-A (AI-A-01)
"""LLM(Gemini) 호출 모듈. 모든 LLM 호출은 이 모듈을 거친다. AI-B는 import만 한다.

사용:
    from common.llm import generate_json
    profile = generate_json(prompt, CompetencyProfile, prompt_version="profile-v1")
"""

import logging
import time
from functools import lru_cache
from typing import TypeVar

from google import genai
from google.genai import types
from pydantic import BaseModel, ValidationError

from common.config import get_settings

logger = logging.getLogger("ieum.llm")

T = TypeVar("T", bound=BaseModel)

# JSON 검증 실패 시 다시 부르는 횟수
MAX_RETRIES = 1


class LLMError(RuntimeError):
    """LLM 호출 또는 응답 검증 실패. 라우터에서 잡아 error("LLM_ERROR", ...) 로 돌려준다."""


@lru_cache
def _client() -> genai.Client:
    settings = get_settings()
    if not settings.gemini_api_key:
        raise LLMError("GEMINI_API_KEY 가 비어 있어요. ai/.env 를 확인하세요.")
    return genai.Client(api_key=settings.gemini_api_key)


def _model() -> str:
    model = get_settings().llm_model
    if not model:
        raise LLMError("LLM_MODEL 이 비어 있어요. ai/.env 를 확인하세요.")
    return model


def _call(prompt: str, config: types.GenerateContentConfig, prompt_version: str) -> str:
    """Gemini 한 번 호출 → 응답 텍스트. 모델 · 걸린 시간 · 프롬프트 버전을 로그로 남긴다 (키 · 프롬프트 본문은 남기지 않음)."""
    model = _model()
    started = time.perf_counter()
    try:
        response = _client().models.generate_content(model=model, contents=prompt, config=config)
    except Exception as e:
        logger.warning("llm call failed model=%s prompt=%s error=%s", model, prompt_version, type(e).__name__)
        raise LLMError("LLM 호출에 실패했어요.") from e
    latency_ms = int((time.perf_counter() - started) * 1000)
    usage = response.usage_metadata
    logger.info(
        "llm call model=%s prompt=%s latency_ms=%d input_tokens=%s output_tokens=%s",
        model,
        prompt_version,
        latency_ms,
        usage.prompt_token_count if usage else None,
        usage.candidates_token_count if usage else None,
    )
    if not response.text:
        raise LLMError("LLM 응답이 비어 있어요.")
    return response.text


def generate_json(
    prompt: str,
    schema: type[T],
    *,
    temperature: float = 0.2,
    system_instruction: str | None = None,
    prompt_version: str = "unversioned",
) -> T:
    """프롬프트를 보내고 `schema`(pydantic 모델)에 맞는 JSON을 받아 검증해서 돌려준다.

    - response_json_schema 로 JSON 출력을 강제한다. 키는 camelCase(alias) 로 받는다.
    - 검증에 실패하면 MAX_RETRIES 번 다시 부르고, 그래도 실패하면 LLMError.
    - prompt_version 은 로그로만 쓴다. 프롬프트를 바꾸면 버전도 올린다. 예: "document-draft-v2"
    """
    config = types.GenerateContentConfig(
        temperature=temperature,
        system_instruction=system_instruction,
        response_mime_type="application/json",
        response_json_schema=schema.model_json_schema(by_alias=True),
    )
    for attempt in range(MAX_RETRIES + 1):
        text = _call(prompt, config, prompt_version)
        try:
            return schema.model_validate_json(text)
        except ValidationError as e:
            logger.warning(
                "llm json invalid prompt=%s attempt=%d errors=%d", prompt_version, attempt + 1, e.error_count()
            )
    raise LLMError("LLM 응답이 정해진 JSON 형식과 맞지 않아요.")


def generate_text(
    prompt: str,
    *,
    temperature: float = 0.2,
    system_instruction: str | None = None,
    prompt_version: str = "unversioned",
) -> str:
    """자유 텍스트 응답."""
    config = types.GenerateContentConfig(temperature=temperature, system_instruction=system_instruction)
    return _call(prompt, config, prompt_version)
