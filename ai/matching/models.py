# 주인: AI-B
"""POST /ai/matching/recommend 요청 · 응답 (docs/API.md 4장)
POST /ai/matching/index 요청 · 응답 (docs/API.md 4장, BE-A JobSyncService.indexRequest · indexInBatches)
"""

from typing import Any, Literal

from pydantic import ConfigDict, field_validator

from schemas.base import CamelModel
from schemas.evidence import EvidenceRef


class IndexJob(CamelModel):
    """색인할 공고 한 건. BE JobSyncService.indexRequest 가 보내는 9개 키."""

    model_config = ConfigDict(extra="ignore")

    job_posting_id: int
    content_hash: str
    company_name: str | None = None
    title: str | None = None
    job_category: str | None = None
    location: str | None = None
    career: str | None = None  # 관계없음 | 신입 | 경력
    description: str | None = None  # docs/JOB_API.md "매칭용 본문" 형식 ([직무] … [전형])
    education: str | None = None  # 최소 학력. 공고 API MIN_EDUBG 원문 (예: "학력무관"). 없으면 null


class IndexRequest(CamelModel):
    """{ jobs: [...], removedJobPostingIds: [...] }

    BE 는 jobs 를 20건씩 나눠 여러 번 보낸다. removedJobPostingIds(이번 배치에서 꺼진 공고)는 첫 요청에만 싣고
    나머지 요청은 [] 이다. 없거나 null 이어도 [] 로 받는다.
    jobs 는 공고마다 따로 검증한다(IndexJob). 한 건이 형식에 안 맞아도 요청 전체가 422 로 실패하지 않고 그 공고만 failed.
    """

    model_config = ConfigDict(extra="ignore")

    jobs: list[dict[str, Any]] = []
    removed_job_posting_ids: list[int] = []

    @field_validator("jobs", "removed_job_posting_ids", mode="before")
    @classmethod
    def _null_to_empty(cls, value: Any) -> Any:
        return [] if value is None else value


class IndexData(CamelModel):
    """응답의 data.

    BE(JobSyncService.indexInBatches)는 묶음마다 indexedCount · skippedCount · removedCount 만 읽어 더한다 (없으면 0).
    failedCount · failedJobPostingIds · retriedCount · embeddingStatus 는 BE 가 읽지 않는 추가 정보다 (로그 · 디버깅용).
    """

    indexed_count: int
    skipped_count: int
    removed_count: int
    failed_count: int
    failed_job_posting_ids: list[int]
    # 이번 요청과 별개로, 저장소에서 vector 가 없거나 모델이 다른 공고를 다시 임베딩해 채운 수
    retried_count: int = 0
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
