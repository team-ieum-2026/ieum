# 주인: AI-A (AI-A-01)
"""임베딩 모듈. 모델은 AI-A가 월요일에 확정(.env EMBEDDING_MODEL). 라이브러리도 그때 requirements.txt 에 추가."""

from collections.abc import Sequence


def embed_texts(texts: Sequence[str]) -> list[list[float]]:
    """문장 목록 → 벡터 목록. 공고 색인(AI-B-02)과 추천 후보 검색(AI-B-03)에서 쓴다.

    TODO(AI-A-01): 모델 로드는 1회만(모듈 전역 또는 lru_cache), 배치 처리.
    """
    raise NotImplementedError("AI-A-01: 임베딩 모듈 구현 전")
