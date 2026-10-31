# 주인: AI-A
"""서류 JSON (docs/API.md 3-3, DB application_document.document)

이력서와 자기소개서를 한 문서로 담는다. sections 순서가 곧 M6 · C4 화면 순서다.
source 가 AI 인 문장은 evidence 가 1개 이상이어야 한다 (AI-A-04).
"""

from typing import Literal

from schemas.base import CamelModel
from schemas.evidence import Evidence

Source = Literal["AI", "REGISTERED", "USER"]
CompanyInfoStatus = Literal["FOUND", "PARTIAL", "NOT_FOUND"]
SectionName = Literal[
    "BASIC", "EDUCATION", "TRAINING", "CAREER", "MILITARY",
    "CERTIFICATES", "SKILLS", "PROJECTS", "PORTFOLIO", "COVER_LETTER",
]


class Sentence(CamelModel):
    sentence_id: str
    text: str
    source: Source
    evidence: list[Evidence] = []


class BasicFields(CamelModel):
    name: str
    email: str | None = None
    github_url: str | None = None
    desired_job: str | None = None


class BasicSection(CamelModel):
    section: Literal["BASIC"] = "BASIC"
    source: Source = "REGISTERED"
    fields: BasicFields


class EducationItem(CamelModel):
    item_id: str
    source: Source = "REGISTERED"
    school_name: str
    major: str | None = None
    degree: str | None = None
    status: str | None = None
    graduated_on: str | None = None


class EducationSection(CamelModel):
    section: Literal["EDUCATION"] = "EDUCATION"
    items: list[EducationItem] = []


class TrainingItem(CamelModel):
    item_id: str
    source: Source = "REGISTERED"
    name: str
    campus: str | None = None
    started_on: str | None = None
    ended_on: str | None = None


class TrainingSection(CamelModel):
    section: Literal["TRAINING"] = "TRAINING"
    items: list[TrainingItem] = []


class CareerItem(CamelModel):
    """DB student_career 컬럼과 같은 이름: company_name · position · started_on · ended_on(NULL 이면 재직 중) · description"""

    item_id: str
    source: Source = "REGISTERED"
    company_name: str
    position: str | None = None
    started_on: str | None = None
    ended_on: str | None = None
    description: str | None = None


class CareerSection(CamelModel):
    section: Literal["CAREER"] = "CAREER"
    items: list[CareerItem] = []


MilitaryValue = Literal["SERVED", "NOT_SERVED", "EXEMPT", "NOT_APPLICABLE"]


class MilitarySection(CamelModel):
    section: Literal["MILITARY"] = "MILITARY"
    value: MilitaryValue | None = None


class CertificateItem(CamelModel):
    item_id: str
    source: Source = "REGISTERED"
    name: str
    issuer: str | None = None
    acquired_on: str | None = None


class CertificatesSection(CamelModel):
    section: Literal["CERTIFICATES"] = "CERTIFICATES"
    items: list[CertificateItem] = []


class SkillsSection(CamelModel):
    section: Literal["SKILLS"] = "SKILLS"
    source: Source = "AI"
    summary: list[Sentence] = []
    tags: list[str] = []


class ProjectItem(CamelModel):
    item_id: str
    title: str
    period_text: str | None = None
    team_size: int | None = None
    role: str | None = None
    bullets: list[Sentence] = []


class ProjectsSection(CamelModel):
    section: Literal["PROJECTS"] = "PROJECTS"
    items: list[ProjectItem] = []


class PortfolioItem(CamelModel):
    item_id: str
    file_name: str
    include: bool = True


class PortfolioSection(CamelModel):
    section: Literal["PORTFOLIO"] = "PORTFOLIO"
    items: list[PortfolioItem] = []


class CoverLetterSection(CamelModel):
    section: Literal["COVER_LETTER"] = "COVER_LETTER"
    sentences: list[Sentence] = []


DocumentSection = (
    BasicSection
    | EducationSection
    | TrainingSection
    | CareerSection
    | MilitarySection
    | CertificatesSection
    | SkillsSection
    | ProjectsSection
    | PortfolioSection
    | CoverLetterSection
)


class Document(CamelModel):
    company_info_status: CompanyInfoStatus
    company_info_note: str | None = None
    excluded_sentence_count: int = 0
    sections: list[DocumentSection] = []
