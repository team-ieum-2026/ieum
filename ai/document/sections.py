# 주인: AI-A
"""등록 자료를 서류 섹션으로 옮기기 (AI-A-03). LLM 을 쓰지 않는다.

BASIC · EDUCATION · TRAINING · CAREER · MILITARY · CERTIFICATES · PORTFOLIO 는 그대로 옮기고,
PROJECTS 는 제목 · 기간 · 인원만 옮긴다 (bullets 는 LLM 이 채움).
itemId 는 서류 예시(eval/fixtures/document_draft.example.json)와 같은 "접두어-원래 id" 형식이다.
"""

import logging
from typing import cast, get_args

from schemas.document import (
    BasicFields,
    BasicSection,
    CareerItem,
    CareerSection,
    CertificateItem,
    CertificatesSection,
    EducationItem,
    EducationSection,
    MilitarySection,
    MilitaryValue,
    PortfolioItem,
    PortfolioSection,
    ProjectItem,
    ProjectsSection,
    Source,
    TrainingItem,
    TrainingSection,
)
from schemas.student import StudentMaterial

logger = logging.getLogger("ieum.document")

_MILITARY_VALUES = set(get_args(MilitaryValue))
_SOURCE_VALUES = set(get_args(Source))


def _source(value: str) -> Source:
    """입력 source 는 아무 문자열이나 받으므로(schemas/student.py) ["AI", "REGISTERED", "USER"] 3개로 좁힌다. 모르는 값은 REGISTERED"""
    if value not in _SOURCE_VALUES:
        logger.warning("unknown item source value=%s → REGISTERED", value)
        return "REGISTERED"
    return cast(Source, value)


def _military(student: StudentMaterial) -> MilitarySection:
    """병역 처리"""
    value = student.basic.military_status
    if value is None:
        return MilitarySection()
    if value not in _MILITARY_VALUES:
        # 모르는 값이면 서류 전체를 실패시키지 않고 "입력 안 함"으로 둔다
        logger.warning("unknown military status value=%s → null", value)
        return MilitarySection()
    return MilitarySection(value=cast(MilitaryValue, value))


def registered_sections(student: StudentMaterial) -> dict[str, object]:
    """섹션 이름 → 섹션. 순서는 조립(service)에서 정한다"""
    b = student.basic
    c = student.course
    return {
        "BASIC": BasicSection(fields=BasicFields(
            name=b.name, email=b.email, github_url=b.github_url, desired_job=b.desired_job,
        )),
        "EDUCATION": EducationSection(items=[
            EducationItem(
                item_id=f"edu-{e.item_id}", source=_source(e.source), school_name=e.school_name, major=e.major,
                degree=e.degree, status=e.status, graduated_on=e.graduated_on,
            )
            for e in student.education
        ]),
        "TRAINING": TrainingSection(items=[
            TrainingItem(
                item_id=f"trn-{c.course_id}", name=c.name, campus=c.campus,
                started_on=c.started_on, ended_on=c.ended_on,
            )
        ]),
        "CAREER": CareerSection(items=[
            CareerItem(
                item_id=f"car-{k.item_id}", source=_source(k.source), company_name=k.company_name, position=k.position,
                started_on=k.started_on, ended_on=k.ended_on, description=k.description,
            )
            for k in student.careers
        ]),
        "MILITARY": _military(student),
        "CERTIFICATES": CertificatesSection(items=[
            CertificateItem(
                item_id=f"cert-{r.item_id}", source=_source(r.source), name=r.name,
                issuer=r.issuer, acquired_on=r.acquired_on,
            )
            for r in student.certificates
        ]),
        "PROJECTS": ProjectsSection(items=[ 
            # 제목, 기간, 인원만 옮기고 bullets와 rold은 비워둬요. #3~4단계에서 LLM 결과로 채웁니다. 
            ProjectItem(item_id=f"prj-{p.item_id}", title=p.title, period_text=p.period_text, team_size=p.team_size)
            for p in student.projects
        ]),
        # includeInApply 는 프로젝트를 서류에서 뺄지가 아니라 포트폴리오 파일을 첨부할지다 (docs/API.md 2-1 PATCH 설명)
        "PORTFOLIO": PortfolioSection(items=[
            PortfolioItem(item_id=f"pf-{p.item_id}", file_name=p.portfolio_file_name, include=p.include_in_apply)
            for p in student.projects
            if p.portfolio_file_name
        ]),
    }
