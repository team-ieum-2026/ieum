# 주인: AI-A
"""서류 생성 전체 흐름과 조립 (AI-A-03 · 04 · 05).

draft_document(req): 근거 후보 → 프롬프트 → LLM(서류 모델) → 긴 자기소개서 문장 나누기 → 검증 → 조립. 라우터가 부른다.
assemble(...): 검증 결과를 서류 JSON 으로 조립한다. LLM 을 쓰지 않아 테스트할 수 있다.
- 섹션 순서는 서류 예시(eval/fixtures/document_draft.example.json)와 같다
- 프로젝트는 LLM 이 정한 순서. bullet 이 모두 빠진 프로젝트는 빈 채로 둔다 (지어낸 문장으로 채우지 않음)
- 자기소개서 문장이 모두 빠지면 원본 자기소개서를 source=REGISTERED(근거 없음)로 넣는다
"""

import logging
import re
from collections import Counter
from typing import cast

from common.config import get_settings
from common.llm import LLMError, generate_json
from document.llm_models import LlmDraft, LlmSentence, LlmSplit
from document.prompts import (
    COVER_SENTENCE_MAX,
    PROMPT_VERSION,
    SPLIT_PROMPT_VERSION,
    SYSTEM_INSTRUCTION,
    build_prompt,
    build_split_prompt,
    company_status,
)
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


_NUMBER = re.compile(r"\d+(?:\.\d+)?")


def _split_ok(original: str, parts: list[str]) -> bool:
    """나눈 결과를 쓸지: 2~3문장, 각각 상한 이내, 숫자가 빠지거나 더해지지 않음"""
    if not 2 <= len(parts) <= 3 or any(not p.strip() or len(p) > COVER_SENTENCE_MAX for p in parts):
        return False
    return Counter(_NUMBER.findall(original)) == Counter(_NUMBER.findall(" ".join(parts)))


def split_long_sentences(draft: LlmDraft, application_id: int) -> LlmDraft:
    """100자를 넘는 자기소개서 문장만 LLM 에 한 번 더 보내 나눈다. verify 앞에서 부른다.

    - 긴 문장이 없으면 부르지 않는다
    - 나눈 문장은 원래 문장의 근거 번호를 물려받는다 → verify 가 숫자 · 근거를 다시 확인한다
    - 확인을 못 넘기거나 호출이 실패하면 원래 문장을 그대로 둔다 (서류 생성은 실패하지 않는다)
    """
    cover = draft.cover_letter
    long_ones = [(i, s.text) for i, s in enumerate(cover) if len(s.text) > COVER_SENTENCE_MAX]
    if not long_ones:
        return draft
    try:
        result = generate_json(
            build_split_prompt(long_ones), LlmSplit,
            prompt_version=SPLIT_PROMPT_VERSION, model=get_settings().document_llm_model or None,
        )
    except LLMError:
        logger.warning("split failed application=%d long=%d → keep original", application_id, len(long_ones))
        return draft
    targets = {i for i, _ in long_ones}
    parts_by_index = {
        item.index: item.sentences for item in result.splits
        if item.index in targets and _split_ok(cover[item.index].text, item.sentences)
    }
    new_cover: list[LlmSentence] = []
    for i, s in enumerate(cover):
        parts = parts_by_index.get(i)
        new_cover.extend([LlmSentence(text=p, evidence_ids=s.evidence_ids) for p in parts] if parts else [s])
    logger.info("split application=%d long=%d split=%d", application_id, len(long_ones), len(parts_by_index))
    return draft.model_copy(update={"cover_letter": new_cover})


def draft_document(req: DraftRequest) -> Document:
    """LLM 을 부르는 전체 흐름. LLM 실패는 common.llm.LLMError 로 올라간다"""
    sources = build_sources(req)
    llm_draft = generate_json(
        build_prompt(req, sources), LlmDraft,
        system_instruction=SYSTEM_INSTRUCTION, prompt_version=PROMPT_VERSION,
        model=get_settings().document_llm_model or None,
    )
    llm_draft = split_long_sentences(llm_draft, req.application_id)
    return assemble(req, sources, verify(llm_draft, req, sources))
