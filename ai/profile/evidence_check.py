# 주인: AI-B
"""근거 검증: 입력에 실제로 없는 근거는 지우고, 근거가 하나도 안 남은 항목은 뺀다.
규칙 추출 결과(check_evidence)와 LLM 결과(check_llm_items, roles · achievements)에 쓴다.
"""

import re
from dataclasses import dataclass

from profile.llm_models import LlmItem
from profile.models import ExtractRequest
from schemas import CompetencyProfile, EvidenceRef

AllowedRefs = set[tuple[str, int]]

_WHITESPACE = re.compile(r"\s+")
_NUMBER = re.compile(r"\d+(?:[.,]\d+)*")


def allowed_refs(req: ExtractRequest) -> AllowedRefs:
    """입력에 실제로 있는 (refType, refId) 목록"""
    refs: AllowedRefs = set()
    refs.update(("PROJECT", p.item_id) for p in req.projects)
    refs.update(("CERTIFICATE", c.item_id) for c in req.certificates)
    refs.update(("EDUCATION", e.item_id) for e in req.education)
    refs.add(("COURSE", req.course.course_id))
    # 임시 규칙(AI-A 확인 전): 자기소개서는 student_cover_letter 의 PK 가 student_id 라서 refId = studentId
    if req.cover_letter is not None:
        refs.add(("COVER_LETTER", req.student_id))
    return refs


def _keep(evidence: list[EvidenceRef], allowed: AllowedRefs) -> list[EvidenceRef]:
    return [ev for ev in evidence if (ev.ref_type, ev.ref_id) in allowed]


def check_evidence(profile: CompetencyProfile, allowed: AllowedRefs) -> CompetencyProfile:
    """없는 근거를 지운 새 프로필을 돌려준다. 원래 profile 은 바꾸지 않는다."""
    skills = []
    for skill in profile.skills:
        evidence = _keep(skill.evidence, allowed)
        if evidence:
            skills.append(skill.model_copy(update={"evidence": evidence}))

    achievements = []
    for achievement in profile.achievements:
        evidence = _keep(achievement.evidence, allowed)
        if evidence:
            achievements.append(achievement.model_copy(update={"evidence": evidence}))

    return profile.model_copy(update={"skills": skills, "achievements": achievements})


@dataclass(frozen=True)
class Excluded:
    """LLM 항목을 뺀 기록. 로그에는 kind · reason 만 남기고 text 는 남기지 않는다 (개인정보)."""

    kind: str  # roles | achievements
    text: str
    reason: str


def normalize(text: str) -> str:
    return _WHITESPACE.sub(" ", text).strip()


def numbers(text: str) -> set[str]:
    """글 속 숫자. 천 단위 쉼표는 뗀다 (1,000 == 1000)."""
    return {m.group(0).replace(",", "") for m in _NUMBER.finditer(text)}


def check_llm_items(
    kind: str, items: list[LlmItem], allowed: AllowedRefs, source_texts: dict[tuple[str, int], str]
) -> tuple[list[LlmItem], list[Excluded]]:
    """LLM 이 만든 roles · achievements 를 코드로 검증한다. (남은 항목, 뺀 항목과 이유)

    1. 입력에 없는 refId 근거는 지우고, 근거가 0개면 뺀다.
    2. quote 가 남은 근거 원문 중 하나에 실제로 있어야 한다 (공백 정규화 후 부분 문자열).
    3. text 안의 숫자는 quote 안에도 있어야 한다.
    """
    kept: list[LlmItem] = []
    excluded: list[Excluded] = []
    for item in items:
        evidence = list(dict.fromkeys((ev.ref_type, ev.ref_id) for ev in item.evidence if (ev.ref_type, ev.ref_id) in allowed))
        if not evidence:
            reason = "근거 refId 가 입력에 없음" if item.evidence else "근거 없음"
            excluded.append(Excluded(kind, item.text, reason))
            continue
        quote = normalize(item.quote)
        if not quote or not any(quote in normalize(source_texts.get(ref, "")) for ref in evidence):
            excluded.append(Excluded(kind, item.text, "quote 가 근거 원문에 없음"))
            continue
        missing = sorted(numbers(item.text) - numbers(quote))
        if missing:
            excluded.append(Excluded(kind, item.text, f"text 의 숫자 {', '.join(missing)} 가 quote 에 없음"))
            continue
        kept.append(item.model_copy(update={"evidence": [EvidenceRef(ref_type=t, ref_id=i) for t, i in evidence]}))
    return kept, excluded
