# 주인: AI-B
"""LLM 없이 등록 자료에서 바로 옮길 수 있는 항목만 채운다. roles · achievements 는 LLM 단계에서 채운다.

basic.name · basic.email · basic.githubUrl 은 프로필에 쓰지 않는다. 나중에 LLM 입력에도 넣지 않는다.
"""

from profile.models import ExtractRequest
from profile.skill_aliases import SKILL_ALIASES, canonical_skill, mentioned_skills, skill_key
from schemas import CompetencyProfile, EvidenceRef
from schemas.profile import ProfileSkill


def own_skills(req: ExtractRequest) -> list[ProfileSkill]:
    """수료생 본인 기술: 직접 적은 기술(최상위 skills) + 역할 기록 · 경력에 이름이 나온 기술.

    프로젝트 techStack 은 팀 전체 기술이라 그대로 옮기지 않는다. 역할 기록 · 경력에서 찾을 이름 목록으로만 쓴다.
    README · 자기소개서에서 본인이 쓴 것으로 나온 기술은 LLM 단계에서 더한다 (add_skills).
    - 직접 적은 기술은 근거가 없어도 남긴다 (evidence 빈 배열). 역할 기록 · 경력에 나오면 그 근거를 붙인다.
    - evidence: 역할 기록 → { PROJECT, 프로젝트 itemId }, 경력 → { CAREER, 경력 itemId }
    - 별칭 사전(skill_aliases)에 있으면 대표 이름으로 바꾸고("자바" → "Java"), 대소문자 · 띄어쓰기를 무시하고 합친다.
    """
    names = [*req.skills, *(t for p in req.projects for t in p.tech_stack or []), *SKILL_ALIASES]
    texts = [("PROJECT", p.item_id, p.role_note) for p in req.projects]
    texts += [("CAREER", c.item_id, " ".join(x for x in [c.position, c.description] if x)) for c in req.careers]

    skills = add_skills([], [ProfileSkill(name=raw, evidence=[]) for raw in req.skills])
    for ref_type, ref_id, text in texts:
        found = [ProfileSkill(name=n, evidence=[EvidenceRef(ref_type=ref_type, ref_id=ref_id)])
                 for n in mentioned_skills(text, names)]
        skills = add_skills(skills, found)
    return skills


def add_skills(base: list[ProfileSkill], extra: list[ProfileSkill]) -> list[ProfileSkill]:
    """같은 기술은 하나로 합치고 근거를 이어 붙인다 (같은 근거는 한 번만). base 순서 → extra 의 새 기술 순서"""
    merged: dict[str, ProfileSkill] = {}
    for skill in [*base, *extra]:
        name = canonical_skill(skill.name)
        if not name:
            continue
        current = merged.setdefault(skill_key(name), ProfileSkill(name=name, evidence=[]))
        for ev in skill.evidence:
            if ev not in current.evidence:
                current.evidence.append(ev)
    return list(merged.values())


def extract_by_rules(req: ExtractRequest) -> CompetencyProfile:
    return CompetencyProfile(
        roles=[],
        skills=own_skills(req),
        achievements=[],
        certificates=[cert.name for cert in req.certificates],
        desired_job=req.basic.desired_job,
        preferred_region=req.basic.preferred_region,
    )
