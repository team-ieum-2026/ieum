# 주인: AI-B
"""근거 검증: 입력에 실제로 없는 근거는 지우고, 근거가 하나도 안 남은 항목은 뺀다.
규칙 추출 결과(check_evidence)와 LLM 결과(check_llm_items, roles · achievements)에 쓴다.
"""

import re
from dataclasses import dataclass

from profile.llm_models import LlmItem
from profile.models import ExtractRequest
from profile.sources import Candidate
from schemas import CompetencyProfile, EvidenceRef
from schemas.profile import ProfileAchievement

AllowedRefs = set[tuple[str, int]]

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


@dataclass
class CheckMetrics:
    """자동 지표용 숫자. 로그에만 남기고 응답에는 넣지 않는다.

    - generated: LLM 이 만든 항목 수
    - evidence_ids / valid_evidence_ids: 항목들이 고른 근거 번호 수(항목 안 중복 제외)와 그중 후보에 있는 번호 수
    - with_numbers / numbers_passed: 숫자 검사까지 간 항목 중 text 에 숫자가 있는 항목 수와 그중 통과한 수
    - excluded: 뺀 항목 수
    """

    generated: int = 0
    evidence_ids: int = 0
    valid_evidence_ids: int = 0
    with_numbers: int = 0
    numbers_passed: int = 0
    excluded: int = 0

    def __add__(self, other: "CheckMetrics") -> "CheckMetrics":
        return CheckMetrics(*(a + b for a, b in zip(vars(self).values(), vars(other).values())))

    @property
    def valid_evidence_ratio(self) -> float | None:
        return round(self.valid_evidence_ids / self.evidence_ids, 3) if self.evidence_ids else None

    @property
    def number_pass_ratio(self) -> float | None:
        return round(self.numbers_passed / self.with_numbers, 3) if self.with_numbers else None


def numbers(text: str) -> set[str]:
    """글 속 숫자. 천 단위 쉼표는 뗀다 (1,000 == 1000)."""
    return {m.group(0).replace(",", "") for m in _NUMBER.finditer(text)}


def check_llm_items(
    kind: str, items: list[LlmItem], candidates: dict[str, Candidate]
) -> tuple[list[ProfileAchievement], list[Excluded], CheckMetrics]:
    """LLM 이 만든 roles · achievements 를 코드로 검증한다. (남은 항목 {text, evidence}, 뺀 항목과 이유, 지표)

    1. 후보에 없는 근거 번호(eid)는 지우고, 남은 근거가 0개면 뺀다.
    2. text 안의 숫자는 고른 근거들의 quote 중 하나에 있어야 한다.
    3. eid → { refType, refId }. 같은 (refType, refId) 는 하나로 합친다 (예: 같은 프로젝트의 README 두 문장).
    """
    kept: list[ProfileAchievement] = []
    excluded: list[Excluded] = []
    metrics = CheckMetrics(generated=len(items))
    for item in items:
        eids = list(dict.fromkeys(e.strip().upper() for e in item.evidence_ids))
        chosen = [candidates[e] for e in eids if e in candidates]
        metrics.evidence_ids += len(eids)
        metrics.valid_evidence_ids += len(chosen)
        if not chosen:
            reason = "근거 번호가 후보에 없음" if eids else "근거 없음"
            excluded.append(Excluded(kind, item.text, reason))
            continue
        text_numbers = numbers(item.text)
        quoted = set().union(*(numbers(c.quote) for c in chosen))
        missing = sorted(text_numbers - quoted)
        metrics.with_numbers += bool(text_numbers)
        if missing:
            excluded.append(Excluded(kind, item.text, f"text 의 숫자 {', '.join(missing)} 가 고른 근거에 없음"))
            continue
        metrics.numbers_passed += bool(text_numbers)
        refs = dict.fromkeys((c.ref_type, c.ref_id) for c in chosen)
        evidence = [EvidenceRef(ref_type=t, ref_id=i) for t, i in refs]
        kept.append(ProfileAchievement(text=item.text, evidence=evidence))
    metrics.excluded = len(excluded)
    return kept, excluded, metrics
