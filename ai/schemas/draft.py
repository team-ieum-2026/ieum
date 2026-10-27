# 주인: AI-A
"""POST /ai/document/draft 요청 · 응답 (docs/API.md 4장, S7). 호출: BE-A (비동기)

예시: ai/eval/fixtures/draft_request.example.json
"""

from schemas.base import CamelModel
from schemas.company import CompanyInfo
from schemas.document import Document
from schemas.profile import CompetencyProfile
from schemas.student import StudentMaterial


class JobPostingInput(CamelModel):
    """지원한 공고 한 건 (DB job_posting 중 서류 생성에 쓰는 컬럼).

    공고를 근거로 다는 refType JOB_POSTING 은 아직 RefType 에 없음. 생기면 refId 는 jobPostingId
    """

    job_posting_id: int
    company_name: str
    title: str
    job_category: str | None = None
    location: str | None = None
    employment_type: str | None = None
    career: str | None = None
    education: str | None = None
    description: str | None = None  # 매칭용 본문: 주요 업무 + 우대 · 자격 항목


class DraftRequest(CamelModel):
    """{ applicationId, student, profile, job, companyInfo }

    - student: 프로필 추출 요청과 같은 등록 자료 (projects[].readme · roleNote 필수로 채움)
    - profile: AI-B 역량 프로필. 무엇을 강조할지 참고용이고 근거로는 쓰지 않음. 아직 없으면 null
    - companyInfo: 그 회사의 기업 정보. 없으면 null → companyInfoStatus NOT_FOUND
    """

    application_id: int
    student: StudentMaterial
    profile: CompetencyProfile | None = None
    job: JobPostingInput
    company_info: CompanyInfo | None = None


class DraftResult(CamelModel):
    """응답 { "data": DraftResult } → { "data": { "document": 서류 JSON } }"""

    document: Document
