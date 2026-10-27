# 주인: AI-A
"""수료생 등록 자료 (S2 프로필 추출 · S7 서류 생성 공통 입력)

BE 전달 자료(2026-10-26, DRAFT) 형식. AI-B `profile/models.py` 의 ExtractRequest 와 같은 모양이라
BE-A는 프로필 추출 때 보내는 객체를 서류 생성 요청의 `student` 로 그대로 보내면 된다.
날짜(YYYY-MM-DD) · 시각(ISO 8601 +09:00)은 문자열 그대로 받는다.

근거 refId 규칙: 학력 · 경력 · 자격증 · 프로젝트는 각 itemId, 과정은 courseId, 자기소개서는 studentId
"""

from schemas.base import CamelModel

# REGISTERED | USER. BE 쪽 값이 늘어도 깨지지 않게 문자열로 받는다.
Source = str


class BasicInfo(CamelModel):
    # name · email · githubUrl 은 서류 BASIC 섹션에 옮기기만 하고 LLM 프롬프트에는 넣지 않는다 (개인정보)
    name: str
    email: str | None = None
    github_url: str | None = None
    desired_job: str | None = None
    preferred_region: str | None = None
    military_status: str | None = None
    completed_on: str


class CourseInfo(CamelModel):
    course_id: int
    name: str
    cohort: str
    campus: str
    started_on: str
    ended_on: str


class EducationItem(CamelModel):
    item_id: int
    source: Source
    school_name: str
    major: str | None = None
    degree: str | None = None
    status: str | None = None
    graduated_on: str | None = None


class CareerItem(CamelModel):
    item_id: int
    source: Source
    company_name: str
    position: str | None = None
    started_on: str | None = None
    ended_on: str | None = None
    description: str | None = None


class CertificateItem(CamelModel):
    item_id: int
    source: Source
    name: str
    issuer: str | None = None
    acquired_on: str | None = None


class ProjectItem(CamelModel):
    """readme · roleNote 는 서류 근거의 핵심이라 서류 생성 요청에서는 꼭 채워 보낸다 (M8 목업에는 없는 필드)"""

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


class CoverLetter(CamelModel):
    content: str
    updated_at: str


class StudentMaterial(CamelModel):
    """{ studentId, basic, course, education, careers, certificates, skills, projects, coverLetter }"""

    student_id: int
    basic: BasicInfo
    course: CourseInfo
    education: list[EducationItem] = []
    careers: list[CareerItem] = []
    certificates: list[CertificateItem] = []
    skills: list[str] = []  # 직접 입력한 기술. 근거로 쓰지 않는다 (근거는 그 기술을 쓴 프로젝트)
    projects: list[ProjectItem] = []
    cover_letter: CoverLetter | None = None
