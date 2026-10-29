# 주인: AI-A
"""모델 비교 실험용 호출기 (Gemini · OpenAI). 서비스 코드(common/llm.py)와 별개다.

- Gemini: google-genai (서비스와 같은 방식: JSON 출력 + 스키마 강제)
- OpenAI: httpx 로 Chat Completions 호출 (새 라이브러리 없이). JSON 모드 + 프롬프트에 스키마
- 두 쪽 모두 결과를 같은 pydantic 모델로 검증한다. 온도 등은 모델 기본값 (최신 OpenAI 추론 모델은 온도를 못 바꿈)
- 키는 ai/.env 의 GEMINI_API_KEY · OPEN_API_KEY
"""

import json
import time
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

import httpx
from dotenv import dotenv_values
from pydantic import BaseModel, ValidationError

ENV = Path(__file__).resolve().parents[2] / ".env"

# (제공사, 등급, 100만 토큰당 달러 (입력, 출력)). 2026-10-29 공식 가격표 기준, 모두 정식(Stable) 모델.
# 등급은 가격대가 비슷한 것끼리 맞췄다 (경량은 OpenAI 가 3~5배 쌈). 인상 예정: argon 12/1, 3.8 Flash 1/1 에 두 배.
MODELS = {
    "gemini-3.5-flash-lite": ("gemini", "경량", (0.30, 2.50)),
    "gemini-3.8-flash": ("gemini", "중간", (0.75, 3.75)),
    "gemini-4-argon": ("gemini", "상위", (2.00, 10.00)),
    "gpt-6-luna": ("openai", "경량", (0.10, 0.50)),
    "gpt-6.1-terra": ("openai", "중간", (1.00, 5.00)),
    "gpt-6.1-sol": ("openai", "상위", (2.00, 10.00)),
}


@dataclass
class Result:
    model: str
    parsed: BaseModel | None
    raw: str
    error: str | None
    latency_s: float
    input_tokens: int
    output_tokens: int
    version: str = ""  # 응답에 찍힌 실제 모델 버전 (OpenAI 는 고정 버전이 없어 기록해 둔다)

    @property
    def cost_usd(self) -> float:
        price_in, price_out = MODELS[self.model][2]
        return (self.input_tokens * price_in + self.output_tokens * price_out) / 1_000_000


@lru_cache
def _env() -> dict:
    return dotenv_values(ENV)


@lru_cache
def _gemini():
    from google import genai

    return genai.Client(api_key=_env()["GEMINI_API_KEY"])


def _call_gemini(model: str, system: str, prompt: str, schema: type[BaseModel]) -> tuple[str, int, int, str]:
    from google.genai import types

    config = types.GenerateContentConfig(
        system_instruction=system,
        response_mime_type="application/json",
        response_json_schema=schema.model_json_schema(by_alias=True),
        automatic_function_calling=types.AutomaticFunctionCallingConfig(disable=True),
    )
    res = _gemini().models.generate_content(model=model, contents=prompt, config=config)
    usage = res.usage_metadata
    return res.text or "", (usage.prompt_token_count or 0) if usage else 0, \
        ((usage.candidates_token_count or 0) + (usage.thoughts_token_count or 0)) if usage else 0, \
        res.model_version or model


def _call_openai(model: str, system: str, prompt: str, schema: type[BaseModel]) -> tuple[str, int, int, str]:
    schema_text = json.dumps(schema.model_json_schema(by_alias=True), ensure_ascii=False)
    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": f"{system}\n\n반드시 다음 JSON 스키마에 맞는 JSON 객체 하나만 출력한다.\n{schema_text}"},
            {"role": "user", "content": prompt},
        ],
        "response_format": {"type": "json_object"},
    }
    res = httpx.post("https://api.openai.com/v1/chat/completions", json=body, timeout=180,
                     headers={"Authorization": f"Bearer {_env()['OPEN_API_KEY']}"})
    res.raise_for_status()
    data = res.json()
    usage = data.get("usage", {})
    return (data["choices"][0]["message"]["content"] or "", usage.get("prompt_tokens", 0),
            usage.get("completion_tokens", 0), data.get("model", model))


def call(model: str, system: str, prompt: str, schema: type[BaseModel]) -> Result:
    provider = MODELS[model][0]
    start = time.perf_counter()
    try:
        raw, tin, tout, version = (_call_gemini if provider == "gemini" else _call_openai)(model, system, prompt, schema)
    except Exception as e:  # 호출 실패도 결과로 남긴다 (실패율)
        return Result(model, None, "", f"{type(e).__name__}: {str(e)[:200]}", time.perf_counter() - start, 0, 0)
    latency = time.perf_counter() - start
    try:
        return Result(model, schema.model_validate_json(raw), raw, None, latency, tin, tout, version)
    except ValidationError as e:
        return Result(model, None, raw, f"JSON 형식 오류 {e.error_count()}건", latency, tin, tout, version)
