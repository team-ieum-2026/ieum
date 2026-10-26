# 주인: AI-B
"""POST /ai/profile/extract 요청 (등록 자료).

가정한 형식, BE와 확정 전. docs/API.md 4장에 요청 형식이 없어서 db/schema.sql 의
student · course · student_education · student_certificate · student_project · student_cover_letter 를 보고 정했다.
"""

from schemas.base import CamelModel


class StudentInfo(CamelModel):
    desired_job: str | None = None
    preferred_region: str | None = None


class CourseInfo(CamelModel):
    id: int
    name: str


class EducationInfo(CamelModel):
    id: int
    school_name: str
    major: str | None = None


class CertificateInfo(CamelModel):
    id: int
    name: str


class ProjectInfo(CamelModel):
    id: int
    title: str
    role_note: str | None = None
    readme: str | None = None
    tech_stack: list[str] = []


class CoverLetterInfo(CamelModel):
    content: str


class ExtractRequest(CamelModel):
    """{ studentId, student, course, educations, certificates, projects, coverLetter }"""

    student_id: int
    student: StudentInfo
    course: CourseInfo
    educations: list[EducationInfo] = []
    certificates: list[CertificateInfo] = []
    projects: list[ProjectInfo] = []
    cover_letter: CoverLetterInfo | None = None
