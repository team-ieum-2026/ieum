# 주인: AI-B
"""LLM 없이 등록 자료에서 바로 옮길 수 있는 항목만 채운다. roles · achievements 는 LLM 단계에서 채운다.

basic.name · basic.email · basic.githubUrl 은 프로필에 쓰지 않는다. 나중에 LLM 입력에도 넣지 않는다.
"""

from profile.models import ExtractRequest
from schemas import CompetencyProfile, EvidenceRef
from schemas.profile import ProfileSkill


def merge_skills(req: ExtractRequest) -> list[ProfileSkill]:
    """모든 프로젝트의 techStack 을 모아 같은 이름(대소문자 무시)은 하나로. 이름은 처음 나온 표기를 쓴다.

    evidence 는 그 기술이 나온 프로젝트마다 { refType: PROJECT, refId: 프로젝트 itemId } 하나씩.
    """
    # TODO: 최상위 skills(직접 입력)는 refType 확정 후 반영 (AI-A 확인 필요)
    # TODO: careers 는 refType 확정 후 반영 (AI-A 확인 필요)
    skills: dict[str, ProfileSkill] = {}
    for project in req.projects:
        for raw in project.tech_stack or []:
            name = raw.strip()
            if not name:
                continue
            skill = skills.setdefault(name.casefold(), ProfileSkill(name=name, evidence=[]))
            if all(ev.ref_id != project.item_id for ev in skill.evidence):
                skill.evidence.append(EvidenceRef(ref_type="PROJECT", ref_id=project.item_id))
    return list(skills.values())


def extract_by_rules(req: ExtractRequest) -> CompetencyProfile:
    return CompetencyProfile(
        roles=[],
        skills=merge_skills(req),
        achievements=[],
        certificates=[cert.name for cert in req.certificates],
        desired_job=req.basic.desired_job,
        preferred_region=req.basic.preferred_region,
    )
