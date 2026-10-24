# 주인: AI-B
"""POST /ai/profile/extract — 등록 자료 → 역량 프로필 JSON (AI-B-01, S2). 호출: BE-B"""

from fastapi import APIRouter, Request

from common.response import not_implemented

router = APIRouter(prefix="/ai/profile", tags=["profile"])


@router.post("/extract")
async def extract_profile(request: Request):
    # TODO(AI-B-01): 요청 본문(등록 자료) → schemas.CompetencyProfile, 항목마다 근거(EvidenceRef) 연결
    # 응답: {"data": {"profile": CompetencyProfile}}
    return not_implemented()
