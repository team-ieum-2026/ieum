# 주인: AI-A
"""ai/.env 설정 읽기 (pydantic-settings). 키 값은 코드 · 로그에 쓰지 않는다."""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    gemini_api_key: str = ""
    # 모델 이름은 AI-A가 월요일에 확정 후 .env 에 기입
    llm_model: str = ""
    embedding_model: str = ""
    reranker_model: str = ""


@lru_cache
def get_settings() -> Settings:
    return Settings()
