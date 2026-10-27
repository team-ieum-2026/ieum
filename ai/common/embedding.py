# 주인: AI-A (AI-A-01)
"""임베딩 모듈 (임시 구현: Gemini 임베딩 API). 모델은 .env EMBEDDING_MODEL. 라이브러리 추가 없음 (google-genai).

사용:
    from common.embedding import embed_texts
    job_vectors = embed_texts(job_texts)                               # 공고 색인 (AI-B-02)
    [query_vector] = embed_texts([profile_text], task_type="RETRIEVAL_QUERY")  # 추천 후보 검색 (AI-B-03)
"""

import logging
import time
from collections.abc import Sequence
from typing import Literal

from google.genai import types

from common.config import get_settings
from common.llm import LLMError, _client

logger = logging.getLogger("ieum.embedding")

# Gemini 임베딩 API 한 번에 보낼 수 있는 최대 문장 수
BATCH_SIZE = 100

# 색인할 문서는 RETRIEVAL_DOCUMENT, 검색어(프로필)는 RETRIEVAL_QUERY. 둘을 맞춰 써야 검색 품질이 나온다.
TaskType = Literal["RETRIEVAL_DOCUMENT", "RETRIEVAL_QUERY", "SEMANTIC_SIMILARITY"]


class EmbeddingError(LLMError):
    """임베딩 호출 실패. LLMError 를 상속해서 라우터에서 같이 잡힌다."""


def _model() -> str:
    model = get_settings().embedding_model
    if not model:
        raise EmbeddingError("EMBEDDING_MODEL 이 비어 있어요. ai/.env 를 확인하세요.")
    return model


def embed_texts(texts: Sequence[str], *, task_type: TaskType = "RETRIEVAL_DOCUMENT") -> list[list[float]]:
    """문장 목록 → 벡터 목록 (입력 순서 그대로). BATCH_SIZE 개씩 나눠 부른다. 빈 목록이면 []."""
    if not texts:
        return []
    model = _model()
    config = types.EmbedContentConfig(task_type=task_type)
    vectors: list[list[float]] = []
    started = time.perf_counter()
    for start in range(0, len(texts), BATCH_SIZE):
        batch = list(texts[start : start + BATCH_SIZE])
        try:
            response = _client().models.embed_content(model=model, contents=batch, config=config)
        except LLMError:
            raise
        except Exception as e:
            logger.warning("embedding call failed model=%s error=%s", model, type(e).__name__)
            raise EmbeddingError("임베딩 호출에 실패했어요.") from e
        embeddings = response.embeddings or []
        if len(embeddings) != len(batch):
            raise EmbeddingError("임베딩 결과 개수가 입력과 달라요.")
        vectors.extend(list(e.values or []) for e in embeddings)
    logger.info(
        "embedding call model=%s task=%s count=%d latency_ms=%d",
        model,
        task_type,
        len(texts),
        int((time.perf_counter() - started) * 1000),
    )
    return vectors
