# 주인: AI-B
"""근거 검증: 입력에 실제로 없는 근거는 지우고, 근거가 하나도 안 남은 항목은 뺀다.
규칙 추출 결과(check_evidence)와 LLM 결과(check_llm_items: roles · achievements, check_llm_skills: skills)에 쓴다.
"""

import re
from dataclasses import dataclass

from profile.llm_models import LlmItem
from profile.models import ExtractRequest
from profile.skill_aliases import canonical_skill, mentions
from profile.sources import Candidate
from schemas import CompetencyProfile, EvidenceRef
from schemas.profile import ProfileAchievement, ProfileSkill

AllowedRefs = set[tuple[str, int]]

_NUMBER = re.compile(r"\d+(?:[.,]\d+)*")
# 원문에 없으면 쓰면 안 되는 강한 표현 (부풀리기)
STRONG_WORDS = ("주도", "총괄", "리드", "리더", "팀장", "전담", "단독", "혼자")
# 원문에 있으면 성과에서도 남겨야 하는 참여 정도 표현. "일부"는 "일부 메시지가…" 처럼 다른 뜻으로도 써서 프롬프트로만 다룬다
HEDGE_WORDS = ("참여", "함께", "보조")
# 숫자도 결과도 없이 작업만 말하는 성과 ("챗봇 제작", "알림 설정"). 이런 문장은 역할 쪽이다
_TASK_ONLY = re.compile(r"(구현|제작|설정|구성|개발|진행|작성|적용|담당)(했다|하였다|함|했음)?[.。]?$")
# 외부 평가 결과 (수상 · 선정 · 채택). 수치가 없어도 성과로 인정한다. "대상"은 "직장인 12명 대상"과 헷갈려서 뺌
_AWARD = re.compile(r"수상|선정|채택|입상|우승|우수상|장려상")
# 팀 기술 목록 줄 ("Java, Spring Boot, JPA, MySQL"). 누가 썼는지 알 수 없어 skills 근거로 쓰지 않는다
_TECH_LIST = re.compile(r"[A-Za-z0-9 .#+\-]+(?:\s*[,/·]\s*[A-Za-z0-9 .#+\-]+)+")


def allowed_refs(req: ExtractRequest) -> AllowedRefs:
    """입력에 실제로 있는 (refType, refId) 목록"""
    refs: AllowedRefs = set()
    refs.update(("PROJECT", p.item_id) for p in req.projects)
    refs.update(("CERTIFICATE", c.item_id) for c in req.certificates)
    refs.update(("EDUCATION", e.item_id) for e in req.education)
    refs.update(("CAREER", c.item_id) for c in req.careers)
    refs.add(("COURSE", req.course.course_id))
    # 임시 규칙(AI-A 확인 전): 자기소개서는 student_cover_letter 의 PK 가 student_id 라서 refId = studentId
    if req.cover_letter is not None:
        refs.add(("COVER_LETTER", req.student_id))
    return refs


def _keep(evidence: list[EvidenceRef], allowed: AllowedRefs) -> list[EvidenceRef]:
    return [ev for ev in evidence if (ev.ref_type, ev.ref_id) in allowed]


def check_evidence(profile: CompetencyProfile, allowed: AllowedRefs) -> CompetencyProfile:
    """없는 근거를 지운 새 프로필을 돌려준다. 원래 profile 은 바꾸지 않는다.

    skills 중 처음부터 근거가 없는 것(수료생이 직접 적은 기술)은 남기고, 근거가 있었는데 다 지워진 것만 뺀다.
    """
    skills = []
    for skill in profile.skills:
        evidence = _keep(skill.evidence, allowed)
        if evidence or not skill.evidence:
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

    kind: str  # roles | achievements | skills
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
    kind: str, items: list[LlmItem], candidates: dict[str, Candidate], team_refs: AllowedRefs = frozenset()
) -> tuple[list[ProfileAchievement], list[Excluded], CheckMetrics]:
    """LLM 이 만든 roles · achievements 를 코드로 검증한다. (남은 항목 {text, evidence}, 뺀 항목과 이유, 지표)

    1. 후보에 없는 근거 번호(eid)는 지우고, 남은 근거가 0개면 뺀다.
    2. text 안의 숫자는 고른 근거들의 quote 중 하나에 있어야 한다.
    3. 강한 표현(STRONG_WORDS)은 고른 근거들의 quote 중 하나에 있어야 한다.
    4. achievements 만: 고른 근거에 참여 표현(HEDGE_WORDS)이 있으면 text 에도 있어야 한다. 숫자 없이 작업만 말하면 뺀다.
       수상 · 선정 · 채택은 숫자가 없어도 성과다. 단 팀 프로젝트(team_refs) 근거면 text 에 "팀"이 있어야 한다.
    5. eid → { refType, refId }. 같은 (refType, refId) 는 하나로 합친다 (예: 같은 프로젝트의 README 두 문장).

    "팀"은 HEDGE_WORDS 에 넣지 않는다. 근거 문장에 흔해서("팀원들이 …", "팀 규칙으로 …") 정상 성과까지 빠진다. 수상에만 본다.
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
        reason = _wording_problem(kind, item.text, chosen, bool(text_numbers), team_refs)
        if reason:
            excluded.append(Excluded(kind, item.text, reason))
            continue
        refs = dict.fromkeys((c.ref_type, c.ref_id) for c in chosen)
        evidence = [EvidenceRef(ref_type=t, ref_id=i) for t, i in refs]
        kept.append(ProfileAchievement(text=item.text, evidence=evidence))
    metrics.excluded = len(excluded)
    return kept, excluded, metrics


def team_refs(req: ExtractRequest) -> AllowedRefs:
    """팀 프로젝트(teamSize 2명 이상)의 (PROJECT, itemId)"""
    return {("PROJECT", p.item_id) for p in req.projects if (p.team_size or 0) > 1}


def _wording_problem(
    kind: str, text: str, chosen: list[Candidate], has_numbers: bool, team: AllowedRefs
) -> str | None:
    """표현 검사. 문제가 있으면 이유, 없으면 None"""
    strong = [w for w in STRONG_WORDS if w in text and not any(w in c.quote for c in chosen)]
    if strong:
        return f"원문에 없는 강한 표현 {', '.join(strong)}"
    if kind != "achievements":
        return None
    hedges = [w for w in HEDGE_WORDS if any(w in c.quote for c in chosen)]
    if hedges and not any(w in text for w in HEDGE_WORDS):
        return f"원문의 참여 표현 {', '.join(hedges)} 가 빠짐"
    if _AWARD.search(text):
        by_team = any((c.ref_type, c.ref_id) in team or "팀" in c.quote for c in chosen)
        return "팀 수상인데 팀 표현이 빠짐" if by_team and "팀" not in text else None
    if not has_numbers and _TASK_ONLY.search(text.strip()):
        return "결과 · 수치 없이 작업만 있음"
    return None


def check_llm_skills(
    items: list[LlmItem], candidates: dict[str, Candidate]
) -> tuple[list[ProfileSkill], list[Excluded], CheckMetrics]:
    """LLM 이 고른 skills 를 코드로 검증한다. (남은 기술, 뺀 기술과 이유, 지표)

    1. 후보에 없는 근거 번호는 지운다.
    2. 고른 근거 중 quote 에 그 기술 이름(또는 별칭)이 실제로 나오는 것만 근거로 남긴다. 팀 기술 목록 줄은 근거가 아니다.
    3. 남은 근거가 0개면 뺀다. text 는 대표 이름으로 바꾼다 ("자바" → "Java").
    """
    kept: list[ProfileSkill] = []
    excluded: list[Excluded] = []
    metrics = CheckMetrics(generated=len(items))
    for item in items:
        name = canonical_skill(item.text)
        eids = list(dict.fromkeys(e.strip().upper() for e in item.evidence_ids))
        chosen = [candidates[e] for e in eids if e in candidates]
        metrics.evidence_ids += len(eids)
        metrics.valid_evidence_ids += len(chosen)
        named = [c for c in chosen if mentions(c.quote, name) and not _TECH_LIST.fullmatch(c.quote.strip())]
        if not name or not named:
            reason = "근거 없음" if not chosen else "기술 이름이 본인 작업 근거에 없음"
            excluded.append(Excluded("skills", item.text, reason))
            continue
        refs = dict.fromkeys((c.ref_type, c.ref_id) for c in named)
        kept.append(ProfileSkill(name=name, evidence=[EvidenceRef(ref_type=t, ref_id=i) for t, i in refs]))
    metrics.excluded = len(excluded)
    return kept, excluded, metrics
