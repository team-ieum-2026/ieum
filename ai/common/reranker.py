# 주인: AI-A (AI-A-01)
"""리랭커 모듈. 모델은 AI-A가 월요일에 확정(.env RERANKER_MODEL)."""

from collections.abc import Sequence


def rerank(query: str, candidates: Sequence[str], *, top_k: int = 3) -> list[tuple[int, float]]:
    """(query, 후보 문서들) → [(후보 인덱스, 점수)] 를 점수 내림차순으로 top_k 개.

    TODO(AI-A-01): 추천 Top-3(AI-B-03)에서 임베딩 후보를 다시 정렬할 때 쓴다.
    """
    raise NotImplementedError("AI-A-01: 리랭커 모듈 구현 전")
