# 주인: AI-A (AI-A-01)
"""LLM(Gemini) 호출 모듈. 모든 LLM 호출은 이 모듈을 거친다. AI-B는 import만 한다."""

from typing import Any

from common.config import get_settings


def generate_json(prompt: str, schema: type, *, temperature: float = 0.2) -> Any:
    """프롬프트를 보내고 `schema`(pydantic 모델)에 맞는 JSON을 받아 검증해서 돌려준다.

    TODO(AI-A-01):
    - google-genai 클라이언트 생성 (get_settings().gemini_api_key, llm_model)
    - response_mime_type="application/json" + response_schema 로 JSON 출력 강제
    - 검증 실패 시 1회 재시도
    - model · latency · prompt 버전을 로그로 남김
    """
    raise NotImplementedError("AI-A-01: LLM 호출 모듈 구현 전")


def generate_text(prompt: str, *, temperature: float = 0.2) -> str:
    """자유 텍스트 응답. TODO(AI-A-01)"""
    raise NotImplementedError("AI-A-01: LLM 호출 모듈 구현 전")
