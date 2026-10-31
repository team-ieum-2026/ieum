# 주인: AI-A
"""서류 생성 LLM 답 검증 (AI-A-04). 규칙에 맞지 않는 문장은 빼고, 뺀 개수와 이유를 센다.

문장마다 (기술 요약 · 프로젝트 bullet · 자기소개서 공통)
1. 근거 번호가 후보 목록에 없으면 그 번호만 지운다
2. 남은 근거가 없으면 제외                                  → no_evidence
3. 수료생 근거(E · T)가 없으면 제외 (공고 · 기업 근거만으로 수료생 경험을 말할 수 없다) → no_student_evidence
4. 프로젝트 bullet 은 다른 프로젝트 근거를 지우고, 같은 프로젝트 근거가 없으면 제외 → other_project
5. 문장 속 숫자가 고른 근거의 원문 · 이름 칸에 모두 없으면 제외 → number_mismatch
   (이름 칸도 보는 이유: "팀 4명" 같은 인원 수는 원문이 아니라 이름 칸에 있다)
문장이 아닌 것
- role: 그 프로젝트 roleNote 에 글자 그대로 있을 때만
- 태그: 본인 기술만. 직접 입력 기술(등록된 표기로 바꿈)이거나 본인 자료(E) 원문에 나오는 기술 (대소문자 무시)
- 프로젝트: LLM 순서대로. 모르는 itemId · 중복은 버리고, 빠진 프로젝트는 원래 순서대로 뒤에 붙인다
이 검증은 의미(인용이 문장을 실제로 뒷받침하는지)는 보지 않는다.
"""

import logging
import re
from collections import Counter
from dataclasses import dataclass, field

from document.llm_models import LlmDraft, LlmProject, LlmSentence
from document.sources import Source
from schemas.draft import DraftRequest

logger = logging.getLogger("ieum.document")

MAX_TAGS = 8
STUDENT_PREFIXES = ("E", "T")

_NUMBER = re.compile(r"\d+(?:[.,]\d+)*")
_SPACES = re.compile(r"\s+")


@dataclass
class CheckedSentence:
    text: str
    sources: list[Source]


@dataclass
class CheckedProject:
    item_id: int
    role: str | None
    bullets: list[CheckedSentence]


@dataclass
class VerifiedDraft:
    skills_summary: list[CheckedSentence]
    tags: list[str]
    projects: list[CheckedProject]
    cover_letter: list[CheckedSentence]
    reasons: Counter = field(default_factory=Counter)  # 제외 이유별 문장 수

    @property
    def excluded(self) -> int:
        return sum(self.reasons.values())


def _numbers(text: str) -> set[str]:
    # "1,000" 과 "1000" 을 같은 숫자로 본다
    return {n.replace(",", "") for n in _NUMBER.findall(text)}


def _check_sentence(
    s: LlmSentence, by_id: dict[str, Source], reasons: Counter, project_id: int | None = None
) -> CheckedSentence | None:
    """통과하면 근거를 붙인 문장, 실패하면 None (reasons 에 이유를 센다)"""
    ids = list(dict.fromkeys(e.strip().upper() for e in s.evidence_ids))
    sources = [by_id[e] for e in ids if e in by_id]
    if not sources:
        reasons["no_evidence"] += 1
        return None
    if not any(src.eid.startswith(STUDENT_PREFIXES) for src in sources):
        reasons["no_student_evidence"] += 1
        return None
    if project_id is not None:
        # 다른 프로젝트 근거만 지운다. 같은 프로젝트 근거가 없으면 이 프로젝트의 bullet 이 아니다
        sources = [src for src in sources if not (src.ref_type == "PROJECT" and src.ref_id != project_id)]
        if not any(src.ref_type == "PROJECT" and src.ref_id == project_id for src in sources):
            reasons["other_project"] += 1
            return None
    if not _numbers(s.text) <= _numbers(" ".join(f"{src.label} {src.quote}" for src in sources)):
        reasons["number_mismatch"] += 1
        return None
    return CheckedSentence(s.text.strip(), sources)


def _check_sentences(
    sentences: list[LlmSentence], by_id: dict[str, Source], reasons: Counter, project_id: int | None = None
) -> list[CheckedSentence]:
    checked = (_check_sentence(s, by_id, reasons, project_id) for s in sentences if s.text.strip())
    return [c for c in checked if c is not None]


def _role(role: str | None, role_note: str | None) -> str | None:
    role = _SPACES.sub(" ", (role or "").strip())
    note = _SPACES.sub(" ", role_note or "")
    return role if role and role in note else None


def _tags(tags: list[str], req: DraftRequest, sources: list[Source]) -> list[str]:
    """본인 기술만: 직접 입력 기술이거나 본인 자료(E) 원문에 이름이 나오는 기술.
    프로젝트 기술 스택(T)은 팀이 쓴 기술일 수 있어 태그 근거로 쓰지 않는다 (v1 에서 React 가 들어간 문제)
    """
    skills = {t.strip().lower(): t.strip() for t in req.student.skills if t.strip()}
    own_text = " ".join(s.quote for s in sources if s.eid.startswith("E")).lower()
    kept: dict[str, None] = {}
    for tag in tags:
        key = tag.strip().lower()
        if key in skills:
            kept[skills[key]] = None
        elif key and key in own_text:
            kept[tag.strip()] = None
    return list(kept)[:MAX_TAGS]


def _projects(llm_projects: list[LlmProject], req: DraftRequest, by_id: dict[str, Source], reasons: Counter):
    inputs = {p.item_id: p for p in req.student.projects}
    checked: list[CheckedProject] = []
    for lp in llm_projects:
        p = inputs.get(lp.item_id)
        if p is None or any(c.item_id == lp.item_id for c in checked):
            continue
        bullets = _check_sentences(lp.bullets, by_id, reasons, project_id=p.item_id)
        checked.append(CheckedProject(p.item_id, _role(lp.role, p.role_note), bullets))
    done = {c.item_id for c in checked}
    checked += [CheckedProject(p.item_id, None, []) for p in req.student.projects if p.item_id not in done]
    return checked


def verify(draft: LlmDraft, req: DraftRequest, sources: list[Source]) -> VerifiedDraft:
    by_id = {s.eid: s for s in sources}
    reasons: Counter = Counter()
    result = VerifiedDraft(
        skills_summary=_check_sentences(draft.skills_summary, by_id, reasons),
        tags=_tags(draft.tags, req, sources),
        projects=_projects(draft.projects, req, by_id, reasons),
        cover_letter=_check_sentences(draft.cover_letter, by_id, reasons),
        reasons=reasons,
    )
    logger.info(
        "document verify application=%d excluded=%d reasons=%s",
        req.application_id, result.excluded, dict(reasons),
    )
    return result
