# 주인: AI-B
"""LLM 단계: 등록 자료 → roles · achievements · 본인이 쓴 skills. 생성(generate_json 1회) → 코드 검증까지 한다.

LLMError 가 나면 빈 결과를 돌려준다. 호출하는 쪽(router)은 규칙 결과만으로 응답한다.
"""

import logging
from dataclasses import dataclass, field
from typing import Literal

from common.llm import LLMError, generate_json
from profile.evidence_check import CheckMetrics, Excluded, check_llm_items, check_llm_skills
from profile.llm_models import LlmProfile
from profile.models import ExtractRequest
from profile.prompts import PROMPT_VERSION, SYSTEM_INSTRUCTION, build_prompt
from profile.sources import build_candidates
from schemas.profile import ProfileAchievement, ProfileSkill

logger = logging.getLogger("ieum.profile")


@dataclass
class LlmResult:
    # OK: LLM 호출 · 검증 완료 · SKIPPED: 원문이 없어 호출 안 함 · FAILED: LLMError → 규칙 결과만
    status: Literal["OK", "SKIPPED", "FAILED"] = "OK"
    roles: list[str] = field(default_factory=list)
    achievements: list[ProfileAchievement] = field(default_factory=list)
    skills: list[ProfileSkill] = field(default_factory=list)
    excluded: list[Excluded] = field(default_factory=list)
    # 자동 지표 (roles + achievements + skills 합). LLM 을 부르지 않았거나 실패하면 None
    metrics: CheckMetrics | None = None


def has_llm_material(req: ExtractRequest) -> bool:
    """roles · achievements 를 뽑을 원문(프로젝트 readme · roleNote, 자기소개서, 경력 설명)이 하나라도 있는지"""
    if req.cover_letter is not None and req.cover_letter.content.strip():
        return True
    if any((c.description or "").strip() for c in req.careers):
        return True
    return any((p.readme or "").strip() or (p.role_note or "").strip() for p in req.projects)


def extract_by_llm(req: ExtractRequest) -> LlmResult:
    # 원문이 없으면 LLM 을 부르지 않는다. 자격증 · 학력 · 과정 이름만으로는 근거 있는 성과를 만들 수 없다.
    if not has_llm_material(req):
        logger.info("profile llm skipped: no project readme/roleNote, cover letter or career description")
        return LlmResult(status="SKIPPED")

    candidates = build_candidates(req)
    try:
        # temperature 0: 같은 입력이면 최대한 같은 결과 (재추출 때 프로필이 흔들리지 않게)
        generated = generate_json(
            build_prompt(candidates),
            LlmProfile,
            temperature=0.0,
            system_instruction=SYSTEM_INSTRUCTION,
            prompt_version=PROMPT_VERSION,
        )
    except LLMError as e:
        logger.warning("profile llm failed, rules only: %s", e)
        return LlmResult(status="FAILED")

    by_eid = {c.eid: c for c in candidates}
    roles, role_out, role_metrics = check_llm_items("roles", generated.roles, by_eid)
    achievements, ach_out, ach_metrics = check_llm_items("achievements", generated.achievements, by_eid)
    skills, skill_out, skill_metrics = check_llm_skills(generated.skills, by_eid)
    excluded = role_out + ach_out + skill_out
    logger.info(
        "profile llm checked candidates=%d roles=%d achievements=%d skills=%d excludedCount=%d reasons=%s",
        len(candidates), len(roles), len(achievements), len(skills), len(excluded),
        [f"{x.kind}: {x.reason}" for x in excluded],
    )
    return LlmResult(
        roles=list(dict.fromkeys(r.text for r in roles)),
        achievements=achievements,
        skills=skills,
        excluded=excluded,
        metrics=role_metrics + ach_metrics + skill_metrics,
    )
