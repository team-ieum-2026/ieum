# 주인: AI-A
"""ai/.env 설정 읽기 (pydantic-settings). 키 값은 코드 · 로그에 쓰지 않는다."""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    gemini_api_key: str = ""
    # 모델 이름은 .env.example 에 적힌 값을 쓴다 (AI-A 확정)
    llm_model: str = ""
    document_llm_model: str = ""  # 서류 생성 전용. 비우면 llm_model
    # LLM 호출 1번의 시간 제한(초). 넘으면 LLMError → 라우터가 오류 응답. 서류 생성 p95 목표 60초 기준
    llm_timeout_sec: int = 60
    embedding_model: str = ""
    reranker_model: str = ""


@lru_cache
def get_settings() -> Settings:
    return Settings()
