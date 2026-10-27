# 주인: AI-A (AI-A-01)
"""리랭커 모듈 (cross-encoder, sentence-transformers). 모델은 .env RERANKER_MODEL, 비어 있으면 DEFAULT_MODEL.

사용:
    from common.reranker import rerank
    top = rerank(profile_text, job_texts, top_k=3)   # [(후보 인덱스, 점수 0~1)] 점수 내림차순

- 기본 bge-reranker-v2-m3. 비교 후보 Qwen/Qwen3-Reranker-0.6B 는 RERANKER_MODEL 만 바꾸면 된다 (CPU 에서는 많이 느림).
- 첫 호출 때 모델을 1회 로드한다 (처음 실행 시 Hugging Face 에서 내려받음, bge 약 2.2GB).
- 의미가 얼마나 맞는지만 본다. 신입 가능 · 지역 같은 조건은 호출 전에 코드로 거른다 (AI-B matching).
"""

import logging
import time
from collections.abc import Sequence
from functools import lru_cache

from common.config import get_settings
from common.llm import LLMError

logger = logging.getLogger("ieum.reranker")

DEFAULT_MODEL = "BAAI/bge-reranker-v2-m3"

# 한 쌍(query + 후보)의 최대 토큰 수. 넘치면 잘린다. 길수록 느려진다.
MAX_LENGTH = 512
BATCH_SIZE = 16

# Qwen3-Reranker 는 지시문을 함께 넣어야 한다 (영어 권장). bge 는 쓰지 않는다.
QWEN3_INSTRUCTION = (
    "Given a job seeker's profile, judge whether the job posting fits "
    "the candidate's desired role, skills, career level and region"
)


class RerankerError(LLMError):
    """리랭커 로드 · 호출 실패. LLMError 를 상속해서 라우터에서 같이 잡힌다."""


def _device() -> str:
    import torch

    if torch.cuda.is_available():
        return "cuda"
    if torch.backends.mps.is_available():
        return "mps"
    return "cpu"


@lru_cache
def _load_model():
    # sentence-transformers(torch 포함)는 무거워서 실제로 쓸 때만 불러온다
    try:
        import torch
        from sentence_transformers import CrossEncoder
    except ImportError as e:
        raise RerankerError("sentence-transformers 가 없어요. pip install -r requirements.txt 를 실행하세요.") from e

    name = get_settings().reranker_model or DEFAULT_MODEL
    kwargs = {}
    if "Qwen3-Reranker" in name:
        kwargs = {"prompts": {"match": QWEN3_INSTRUCTION}, "default_prompt_name": "match"}
    started = time.perf_counter()
    try:
        # Sigmoid: 점수를 0~1 로 (모델마다 기본 출력이 달라서 고정)
        model = CrossEncoder(
            name, max_length=MAX_LENGTH, device=_device(), activation_fn=torch.nn.Sigmoid(), **kwargs
        )
    except Exception as e:
        logger.warning("reranker load failed model=%s error=%s", name, type(e).__name__)
        raise RerankerError("리랭커 모델을 불러오지 못했어요.") from e
    logger.info("reranker loaded model=%s device=%s latency_ms=%d", name, model.device, int((time.perf_counter() - started) * 1000))
    return model


def rerank(query: str, candidates: Sequence[str], *, top_k: int = 3) -> list[tuple[int, float]]:
    """(query, 후보 문서들) → [(후보 인덱스, 점수 0~1)] 를 점수 내림차순으로 top_k 개. 점수가 같으면 원래 순서.

    추천 Top-3(AI-B-03)에서 임베딩 후보를 다시 정렬할 때 쓴다. 실패하면 RerankerError.
    """
    if not candidates or top_k <= 0:
        return []
    model = _load_model()
    started = time.perf_counter()
    try:
        raw = model.predict([(query, c) for c in candidates], batch_size=BATCH_SIZE, show_progress_bar=False)
    except Exception as e:
        logger.warning("reranker predict failed error=%s", type(e).__name__)
        raise RerankerError("리랭커 호출에 실패했어요.") from e
    scores = [float(s) for s in raw]
    logger.info("reranker predict count=%d latency_ms=%d", len(candidates), int((time.perf_counter() - started) * 1000))
    ranked = sorted(range(len(candidates)), key=lambda i: (-scores[i], i))
    return [(i, scores[i]) for i in ranked[:top_k]]
