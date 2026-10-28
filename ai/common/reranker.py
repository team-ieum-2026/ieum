# 주인: AI-B (AI-A-01 에서 넘겨받음)
"""리랭커 모듈 (cross-encoder, sentence-transformers). 모델은 .env RERANKER_MODEL, 비어 있으면 DEFAULT_MODEL.

사용:
    from common.reranker import rerank
    top = rerank(profile_text, job_texts, top_k=3)   # [(후보 인덱스, 점수 0~1)] 점수 내림차순

- 기본 bge-reranker-v2-m3. 비교 후보 Qwen/Qwen3-Reranker-0.6B 는 RERANKER_MODEL 만 바꾸면 된다 (CPU 에서는 많이 느림).
- 첫 호출 때 모델을 1회 로드한다 (처음 실행 시 Hugging Face 에서 내려받음, bge 약 2.2GB).
  서버 시작 때 미리 불러 두려면 warmup() 을 부른다 (호출은 AI-A main.py).
- 후보를 입력 길이순으로 정렬해 BATCH_SIZE 개씩 계산한다 (비슷한 길이끼리 묶어 패딩 낭비를 줄임).
  점수는 원래 후보 순서로 되돌려 쓰므로 결과 모양은 정렬 전과 같다.
- 의미가 얼마나 맞는지만 본다. 신입 가능 · 지역 같은 조건은 호출 전에 코드로 거른다 (AI-B matching).
"""

import importlib.util
import logging
import os
import sys
import sysconfig
import time
from collections.abc import Sequence
from contextlib import contextmanager
from functools import lru_cache

from common.config import get_settings
from common.llm import LLMError

logger = logging.getLogger("ieum.reranker")

DEFAULT_MODEL = "BAAI/bge-reranker-v2-m3"

# 한 쌍(query + 후보)의 최대 토큰 수. 넘치면 잘린다. 길수록 느려진다.
MAX_LENGTH = 512
# 한 번에 계산하는 쌍 수. 길이순으로 정렬한 뒤 이만큼씩 나눠 계산한다 (CPU 기준)
BATCH_SIZE = 8

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


@contextmanager
def _stdlib_profile():
    """torch import 동안만 sys.modules["profile"] 을 표준 라이브러리 profile 로 바꿔 둔다.

    서버는 ai/ 에서 실행해서 `import profile` 이 AI-B 의 ai/profile/ 패키지를 가리킨다.
    torch 는 import 중에 cProfile → profile.run 을 쓰므로 그대로 두면 AttributeError 가 난다.
    끝나면 원래 모듈(AI-B 패키지)로 되돌린다. ai/profile/ 이름이 바뀌면 이 함수는 지워도 된다.
    """
    current = sys.modules.get("profile")
    if current is not None and not hasattr(current, "run"):
        path = os.path.join(sysconfig.get_paths()["stdlib"], "profile.py")
        spec = importlib.util.spec_from_file_location("profile", path)
        assert spec is not None and spec.loader is not None
        stdlib = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(stdlib)
        sys.modules["profile"] = stdlib
    try:
        yield
    finally:
        if current is None:
            sys.modules.pop("profile", None)
        else:
            sys.modules["profile"] = current


@lru_cache
def _load_model():
    # sentence-transformers(torch 포함)는 무거워서 실제로 쓸 때만 불러온다
    try:
        with _stdlib_profile():
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


def warmup() -> None:
    """모델을 미리 불러 둔다 (서버 시작 때 1번). 이미 불러왔으면 아무것도 안 한다. 실패하면 RerankerError."""
    _load_model()


def _batches(query: str, candidates: Sequence[str]) -> list[list[int]]:
    """후보 인덱스를 (query + 후보) 길이가 긴 순으로 정렬해 BATCH_SIZE 개씩 나눈다. 길이가 같으면 원래 순서."""
    order = sorted(range(len(candidates)), key=lambda i: (-(len(query) + len(candidates[i])), i))
    return [order[i : i + BATCH_SIZE] for i in range(0, len(order), BATCH_SIZE)]


def rerank(query: str, candidates: Sequence[str], *, top_k: int = 3) -> list[tuple[int, float]]:
    """(query, 후보 문서들) → [(후보 인덱스, 점수 0~1)] 를 점수 내림차순으로 top_k 개. 점수가 같으면 원래 순서.

    추천 Top-3(AI-B-03)에서 임베딩 후보를 다시 정렬할 때 쓴다. 실패하면 RerankerError.
    """
    if not candidates or top_k <= 0:
        return []
    model = _load_model()
    started = time.perf_counter()
    scores = [0.0] * len(candidates)
    try:
        for batch in _batches(query, candidates):
            raw = model.predict([(query, candidates[i]) for i in batch], batch_size=BATCH_SIZE, show_progress_bar=False)
            raw = list(raw)
            if len(raw) != len(batch):
                raise ValueError(f"predict returned {len(raw)} scores for {len(batch)} pairs")
            for i, s in zip(batch, raw):
                scores[i] = float(s)
    except Exception as e:
        logger.warning("reranker predict failed error=%s", type(e).__name__)
        raise RerankerError("리랭커 호출에 실패했어요.") from e
    logger.info("reranker predict count=%d batch=%d latency_ms=%d", len(candidates), BATCH_SIZE,
                int((time.perf_counter() - started) * 1000))
    ranked = sorted(range(len(candidates)), key=lambda i: (-scores[i], i))
    return [(i, scores[i]) for i in ranked[:top_k]]
