# 주인: AI-A
"""서류 생성 전체 흐름과 조립 (AI-A-03 · 04 · 05).

draft_document(req): 근거 후보 → 프롬프트 → LLM(서류 모델) → 검증 → 조립. 라우터가 부른다.
assemble(...): 검증 결과를 서류 JSON 으로 조립한다. LLM 을 쓰지 않아 테스트할 수 있다.
- 섹션 순서는 서류 예시(eval/fixtures/document_draft.example.json)와 같다
- 프로젝트는 LLM 이 정한 순서. bullet 이 모두 빠진 프로젝트는 빈 채로 둔다 (지어낸 문장으로 채우지 않음)
- 자기소개서 문장이 모두 빠지면 원본 자기소개서를 source=REGISTERED(근거 없음)로 넣는다
"""

import logging
from typing import cast

from common.config import get_settings
from common.llm import generate_json
from document.llm_models import LlmDraft
from document.prompts import PROMPT_VERSION, SYSTEM_INSTRUCTION, build_prompt, company_status
from document.sections import registered_sections
from document.sources import Source, build_sources, company_display_name
from document.verify import CheckedSentence, VerifiedDraft, verify
from profile.sources import split_chunks
from schemas.document import (
    CompanyInfoStatus,
    CoverLetterSection,
    Document,
    ProjectItem,
    ProjectsSection,
    Sentence,
    SkillsSection,
)
from schemas.draft import DraftRequest
from schemas.evidence import Evidence, RefType

logger = logging.getLogger("ieum.document")

SECTION_ORDER = [
    "BASIC", "EDUCATION", "TRAINING", "CAREER", "MILITARY",
    "CERTIFICATES", "SKILLS", "PROJECTS", "PORTFOLIO", "COVER_LETTER",
]
_COMPANY_ITEMS = [("talent_keywords", "인재상"), ("core_values", "핵심 가치"), ("recent_issue", "최근 이슈")]


def _evidence(s: Source) -> Evidence:
    return Evidence(
        ref_type=cast(RefType, s.ref_type), ref_id=s.ref_id, label=s.label, quote=s.quote,
        url=s.url, collected_at=s.collected_at,
    )


def _sentences(checked: list[CheckedSentence], prefix: str, start: int = 1) -> list[Sentence]:
    return [
        Sentence(sentence_id=f"{prefix}-{i}", text=c.text, source="AI", evidence=[_evidence(s) for s in c.sources])
        for i, c in enumerate(checked, start=start)
    ]


def company_note(req: DraftRequest, status: CompanyInfoStatus) -> str | None:
    """화면에 보일 안내 문구. FOUND 면 null"""
    if status == "FOUND":
        return None
    info = req.company_info
    name = company_display_name(info.company_name if info else req.job.company_name)
    if status == "NOT_FOUND" or info is None:
        return f"{name}의 기업 정보를 찾지 못해 공고 내용만 반영했어요"
    found = [label for attr, label in _COMPANY_ITEMS if getattr(info, attr)]
    missing = [label for attr, label in _COMPANY_ITEMS if not getattr(info, attr)]
    return f"{name}의 {' · '.join(missing)}를 찾지 못해 {' · '.join(found)}만 반영했어요"


def _cover_letter(req: DraftRequest, verified: VerifiedDraft) -> CoverLetterSection:
    if verified.cover_letter:
        return CoverLetterSection(sentences=_sentences(verified.cover_letter, "cl"))
    letter = req.student.cover_letter
    if letter is None:
        return CoverLetterSection()
    logger.warning("cover letter all excluded application=%d → original as REGISTERED", req.application_id)
    return CoverLetterSection(sentences=[
        Sentence(sentence_id=f"cl-{i}", text=text, source="REGISTERED")
        for i, text in enumerate(split_chunks(letter.content), start=1)
    ])


def _projects(registered: ProjectsSection, verified: VerifiedDraft) -> ProjectsSection:
    by_id = {item.item_id: item for item in registered.items}
    items: list[ProjectItem] = []
    next_id = 1
    for p in verified.projects:
        item = by_id[f"prj-{p.item_id}"]
        bullets = _sentences(p.bullets, "pj", start=next_id)
        next_id += len(bullets)
        items.append(item.model_copy(update={"role": p.role, "bullets": bullets}))
    return ProjectsSection(items=items)


def assemble(req: DraftRequest, sources: list[Source], verified: VerifiedDraft) -> Document:
    status = cast(CompanyInfoStatus, company_status(req, sources))
    sections = registered_sections(req.student)
    sections["SKILLS"] = SkillsSection(summary=_sentences(verified.skills_summary, "sk"), tags=verified.tags)
    sections["PROJECTS"] = _projects(cast(ProjectsSection, sections["PROJECTS"]), verified)
    sections["COVER_LETTER"] = _cover_letter(req, verified)
    return Document(
        company_info_status=status,
        company_info_note=company_note(req, status),
        excluded_sentence_count=verified.excluded,
        sections=[sections[name] for name in SECTION_ORDER],
    )


def draft_document(req: DraftRequest) -> Document:
    """LLM 을 부르는 전체 흐름. LLM 실패는 common.llm.LLMError 로 올라간다"""
    sources = build_sources(req)
    llm_draft = generate_json(
        build_prompt(req, sources), LlmDraft,
        system_instruction=SYSTEM_INSTRUCTION, prompt_version=PROMPT_VERSION,
        model=get_settings().document_llm_model or None,
    )
    return assemble(req, sources, verify(llm_draft, req, sources))
