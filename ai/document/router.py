# 주인: AI-A
"""POST /ai/document/draft — 등록 자료 + 프로필 + 공고 + 기업 정보 → 서류 JSON (AI-A-03, S7). 호출: BE-A (비동기)"""

from fastapi import APIRouter, Request

from common.response import not_implemented

router = APIRouter(prefix="/ai/document", tags=["document"])


@router.post("/draft")
async def draft_document(request: Request):
    # TODO(AI-A-03): 요청 { applicationId, student, profile, job, companyInfo | null }
    # TODO(AI-A-04): source=AI 문장은 근거 1개 이상, 근거 없는 문장은 제외하고 excludedSentenceCount 에 기록
    # TODO(AI-A-05): 기업 정보가 없거나 일부면 companyInfoStatus PARTIAL · NOT_FOUND
    # 응답: {"data": {"document": schemas.Document}}
    return not_implemented()
