# 주인: AI-B
"""POST /ai/profile/extract — 등록 자료 → 역량 프로필 JSON (AI-B-01, S2). 호출: BE-B"""

import asyncio
import logging

from fastapi import APIRouter

from common.response import ok
from profile.evidence_check import allowed_refs, check_evidence
from profile.llm_extract import LlmResult, extract_by_llm
from profile.models import ExtractRequest
from profile.rules import add_skills, extract_by_rules

router = APIRouter(prefix="/ai/profile", tags=["profile"])

logger = logging.getLogger("ieum.profile")


def log_metrics(student_id: int, llm: LlmResult) -> None:
    """자동 지표를 로그에만 남긴다 (응답에는 넣지 않음). 항목 text 는 남기지 않는다."""
    m = llm.metrics
    if m is None:
        logger.info("profile metrics studentId=%d llm=%s", student_id, llm.status)
        return
    logger.info(
        "profile metrics studentId=%d llm=%s generated=%d evidenceIds=%d validEvidenceIds=%d validEvidenceRatio=%s "
        "withNumbers=%d numbersPassed=%d numberPassRatio=%s excluded=%d",
        student_id, llm.status, m.generated, m.evidence_ids, m.valid_evidence_ids, m.valid_evidence_ratio,
        m.with_numbers, m.numbers_passed, m.number_pass_ratio, m.excluded,
    )


@router.post("/extract")
async def extract_profile(body: ExtractRequest) -> dict:
    profile = extract_by_rules(body)
    # generate_json 은 동기 호출이라 스레드에서 돌려 서버가 멈추지 않게 한다.
    # LLM 이 실패하면 roles · achievements 는 빈 배열, skills 는 규칙 결과만
    llm = await asyncio.to_thread(extract_by_llm, body)
    log_metrics(body.student_id, llm)
    profile = profile.model_copy(
        update={"roles": llm.roles, "achievements": llm.achievements, "skills": add_skills(profile.skills, llm.skills)}
    )
    profile = check_evidence(profile, allowed_refs(body))
    # 응답: {"data": {"profile": CompetencyProfile}}
    return ok({"profile": profile.model_dump(by_alias=True)})
