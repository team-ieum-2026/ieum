# 주인: AI-B
"""POST /ai/matching/recommend 요청 · 응답 (docs/API.md 4장)
POST /ai/matching/index 요청 · 응답 (docs/JOB_API.md "AI 색인 요청", BE-A 초안 · 확정 전)
"""

from typing import Any, Literal

from pydantic import ConfigDict

from schemas.base import CamelModel
from schemas.evidence import EvidenceRef


class IndexJob(CamelModel):
    """색인할 공고 한 건. BE JobSyncService.indexRequest 가 보내는 8개 키 + 합의 전 선택 필드(minEducation)."""

    model_config = ConfigDict(extra="ignore")

    job_posting_id: int
    content_hash: str
    company_name: str | None = None
    title: str | None = None
    job_category: str | None = None
    location: str | None = None
    career: str | None = None  # 관계없음 | 신입 | 경력
    description: str | None = None  # docs/JOB_API.md "매칭용 본문" 형식 ([직무] … [전형])
    min_education: str | None = None  # BE와 합의 전. 안 오면 None


class IndexRequest(CamelModel):
    """{ jobs: [...], removedJobPostingIds: [...] }

    jobs 는 공고마다 따로 검증한다(IndexJob). 한 건이 형식에 안 맞아도 요청 전체가 422 로 실패하지 않고 그 공고만 failed.
    removedJobPostingIds 는 BE와 합의 전. 안 오면 [].
    """

    model_config = ConfigDict(extra="ignore")

    jobs: list[dict[str, Any]] = []
    removed_job_posting_ids: list[int] = []


class IndexData(CamelModel):
    """응답의 data. BE 는 이 객체를 키를 읽지 않고 JobSyncResult.indexResult 에 그대로 담는다."""

    indexed_count: int
    skipped_count: int
    removed_count: int
    failed_count: int
    failed_job_posting_ids: list[int]
    # OK: 벡터까지 저장 · NOT_READY: common.embedding 구현 전이라 전처리만 저장하고 vector 는 null
    embedding_status: Literal["OK", "NOT_READY"]


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
