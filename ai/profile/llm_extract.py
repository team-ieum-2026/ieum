# 주인: AI-B
"""LLM 단계: 등록 자료 → roles · achievements. 생성(generate_json 1회) → 코드 검증까지 한다.

LLMError 가 나면 빈 결과를 돌려준다. 호출하는 쪽(router)은 규칙 결과만으로 응답한다.
"""

import logging
from dataclasses import dataclass, field

from common.llm import LLMError, generate_json
from profile.evidence_check import Excluded, check_llm_items
from profile.llm_models import LlmProfile
from profile.models import ExtractRequest
from profile.prompts import PROMPT_VERSION, SYSTEM_INSTRUCTION, build_prompt
from profile.sources import build_candidates
from schemas.profile import ProfileAchievement

logger = logging.getLogger("ieum.profile")


@dataclass
class LlmResult:
    roles: list[str] = field(default_factory=list)
    achievements: list[ProfileAchievement] = field(default_factory=list)
    excluded: list[Excluded] = field(default_factory=list)


def has_llm_material(req: ExtractRequest) -> bool:
    """roles · achievements 를 뽑을 원문(프로젝트 readme · roleNote, 자기소개서)이 하나라도 있는지"""
    if req.cover_letter is not None and req.cover_letter.content.strip():
        return True
    return any((p.readme or "").strip() or (p.role_note or "").strip() for p in req.projects)


def extract_by_llm(req: ExtractRequest) -> LlmResult:
    # 원문이 없으면 LLM 을 부르지 않는다. 자격증 · 학력 · 과정 이름만으로는 근거 있는 성과를 만들 수 없다.
    if not has_llm_material(req):
        logger.info("profile llm skipped: no project readme/roleNote or cover letter")
        return LlmResult()

    candidates = build_candidates(req)
    try:
        generated = generate_json(
            build_prompt(candidates), LlmProfile, system_instruction=SYSTEM_INSTRUCTION, prompt_version=PROMPT_VERSION
        )
    except LLMError as e:
        logger.warning("profile llm failed, rules only: %s", e)
        return LlmResult()

    by_eid = {c.eid: c for c in candidates}
    roles, role_out = check_llm_items("roles", generated.roles, by_eid)
    achievements, ach_out = check_llm_items("achievements", generated.achievements, by_eid)
    excluded = role_out + ach_out
    logger.info(
        "profile llm checked candidates=%d roles=%d achievements=%d excludedCount=%d reasons=%s",
        len(candidates), len(roles), len(achievements), len(excluded), [f"{x.kind}: {x.reason}" for x in excluded],
    )
    return LlmResult(
        roles=list(dict.fromkeys(r.text for r in roles)),
        achievements=achievements,
        excluded=excluded,
    )
