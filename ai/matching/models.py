# 주인: AI-B
"""POST /ai/matching/recommend 요청 · 응답 (docs/API.md 4장)"""

from schemas.base import CamelModel
from schemas.evidence import EvidenceRef


class RecommendRequest(CamelModel):
    """{ studentId, profile, excludeJobPostingIds, topK }"""

    student_id: int
    profile: dict
    exclude_job_posting_ids: list[int] = []
    top_k: int = 3


class RecommendReason(CamelModel):
    """카드에 보이는 추천 근거 한 줄: { text, evidence: [{ refType, refId }] }"""

    text: str
    evidence: list[EvidenceRef]


class RecommendItem(CamelModel):
    """{ jobPostingId, rank, reasons }"""

    job_posting_id: int
    rank: int
    reasons: list[RecommendReason]


class RecommendData(CamelModel):
    """응답의 data 부분: { items: [...] }. 맞는 공고가 없으면 items 는 []"""

    items: list[RecommendItem]
