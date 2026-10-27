# 주인: AI-A (AI-A-02: 데이터 형식. AI-B는 형식에 맞춰 데이터만 채움)
"""docs/API.md 3장의 핵심 JSON 형식을 pydantic 모델로. 모든 모델은 camelCase alias + populate_by_name."""

from schemas.base import CamelModel
from schemas.company import CompanyInfo
from schemas.document import Document
from schemas.draft import DraftRequest, DraftResult, JobPostingInput
from schemas.evidence import Evidence, EvidenceRef
from schemas.profile import CompetencyProfile
from schemas.student import StudentMaterial

__all__ = [
    "CamelModel",
    "CompanyInfo",
    "CompetencyProfile",
    "Document",
    "DraftRequest",
    "DraftResult",
    "Evidence",
    "EvidenceRef",
    "JobPostingInput",
    "StudentMaterial",
]
