# 주인: AI-B
"""POST /ai/profile/extract 요청 (등록 자료 전체).

BE 잠정 형식(DRAFT, 2026-10-26 BE 전달 자료 기준), 최종 합의 전.
샘플: ai/data/samples/profile_extract_request.json · profile_extract_response.example.json
날짜(YYYY-MM-DD) · 시각(ISO 8601 +09:00)은 문자열 그대로 받는다.
"""

from pydantic import ConfigDict

from schemas.base import CamelModel

# REGISTERED | USER | AI. BE 쪽 값이 늘어도 깨지지 않게 문자열로 받는다.
Source = str


class _Input(CamelModel):
    """이 파일의 요청 모델 공통 부모. BE가 필드를 추가해도 깨지지 않게 모르는 필드는 무시한다."""

    model_config = ConfigDict(extra="ignore")


class BasicInfo(_Input):
    # name · email · githubUrl 은 받기만 하고 프로필에도 LLM 입력에도 쓰지 않는다 (개인정보)
    name: str
    email: str | None = None
    github_url: str | None = None
    desired_job: str | None = None
    preferred_region: str | None = None
    military_status: str | None = None
    completed_on: str


class CourseInfo(_Input):
    course_id: int
    name: str
    cohort: str
    campus: str
    started_on: str
    ended_on: str


class EducationItem(_Input):
    item_id: int
    source: Source
    school_name: str
    major: str | None = None
    degree: str | None = None
    status: str | None = None
    graduated_on: str | None = None


class CareerItem(_Input):
    item_id: int
    source: Source
    company_name: str
    position: str | None = None
    started_on: str | None = None
    ended_on: str | None = None
    description: str | None = None


class CertificateItem(_Input):
    item_id: int
    source: Source
    name: str
    issuer: str | None = None
    acquired_on: str | None = None


class ProjectItem(_Input):
    item_id: int
    title: str
    period_text: str | None = None
    team_size: int | None = None
    tech_stack: list[str] | None = None  # 원문 값 그대로라 null 가능
    portfolio_file_name: str | None = None
    include_in_apply: bool
    role_note: str | None = None
    readme: str | None = None
    source: Source


class CoverLetter(_Input):
    content: str
    updated_at: str


class ExtractRequest(_Input):
    """{ studentId, basic, course, education, careers, certificates, skills, projects, coverLetter }"""

    student_id: int
    basic: BasicInfo
    course: CourseInfo
    education: list[EducationItem] = []
    careers: list[CareerItem] = []
    certificates: list[CertificateItem] = []
    skills: list[str] = []  # 수료생이 직접 입력 · 확인한 기술
    projects: list[ProjectItem] = []
    cover_letter: CoverLetter | None = None
