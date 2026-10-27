# 주인: AI-B
"""POST /ai/profile/extract — 등록 자료 → 역량 프로필 JSON (AI-B-01, S2). 호출: BE-B"""

import asyncio

from fastapi import APIRouter

from common.response import ok
from profile.evidence_check import allowed_refs, check_evidence
from profile.llm_extract import extract_by_llm
from profile.models import ExtractRequest
from profile.rules import extract_by_rules

router = APIRouter(prefix="/ai/profile", tags=["profile"])


@router.post("/extract")
async def extract_profile(body: ExtractRequest) -> dict:
    profile = extract_by_rules(body)
    # generate_json 은 동기 호출이라 스레드에서 돌려 서버가 멈추지 않게 한다. LLM 이 실패하면 roles · achievements 는 빈 배열
    llm = await asyncio.to_thread(extract_by_llm, body)
    profile = profile.model_copy(update={"roles": llm.roles, "achievements": llm.achievements})
    profile = check_evidence(profile, allowed_refs(body))
    # 응답: {"data": {"profile": CompetencyProfile}}
    return ok({"profile": profile.model_dump(by_alias=True)})
