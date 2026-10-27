# 주인: AI-B
"""POST /ai/profile/extract — 등록 자료 → 역량 프로필 JSON (AI-B-01, S2). 호출: BE-B"""

from fastapi import APIRouter

from common.response import ok
from profile.evidence_check import allowed_refs, check_evidence
from profile.models import ExtractRequest
from profile.rules import extract_by_rules

router = APIRouter(prefix="/ai/profile", tags=["profile"])


@router.post("/extract")
async def extract_profile(body: ExtractRequest) -> dict:
    profile = extract_by_rules(body)
    # TODO(AI-B-01): common.llm 이 준비되면 roleNote · readme · coverLetter 로 roles · achievements 를 채운다 (항목마다 근거 연결)
    profile = check_evidence(profile, allowed_refs(body))
    # 응답: {"data": {"profile": CompetencyProfile}}
    return ok({"profile": profile.model_dump(by_alias=True)})
