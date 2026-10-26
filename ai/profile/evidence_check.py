# 주인: AI-B
"""근거 검증: 입력에 실제로 없는 근거는 지우고, 근거가 하나도 안 남은 항목은 뺀다.
지금은 규칙 추출 결과에 쓰고, 나중에 LLM 결과(roles · achievements)를 검증할 때도 쓴다.
"""

from profile.models import ExtractRequest
from schemas import CompetencyProfile, EvidenceRef

AllowedRefs = set[tuple[str, int]]


def allowed_refs(req: ExtractRequest) -> AllowedRefs:
    """입력에 실제로 있는 (refType, refId) 목록"""
    refs: AllowedRefs = set()
    refs.update(("PROJECT", p.id) for p in req.projects)
    refs.update(("CERTIFICATE", c.id) for c in req.certificates)
    refs.update(("EDUCATION", e.id) for e in req.educations)
    refs.add(("COURSE", req.course.id))
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
