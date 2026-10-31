# 주인: AI-A
"""POST /ai/document/draft — 등록 자료 + 프로필 + 공고 + 기업 정보 → 서류 JSON (AI-A-03 · 04 · 05, S7). 호출: BE-A (비동기)"""

import asyncio

from fastapi import APIRouter
from fastapi.responses import JSONResponse

from common.llm import LLMError
from common.response import error, ok
from document.service import draft_document
from schemas.draft import DraftRequest

router = APIRouter(prefix="/ai/document", tags=["document"])


# 반환이 dict(성공) 또는 JSONResponse(실패)라 응답 모델을 타입 표기로 만들지 않는다
@router.post("/draft", response_model=None)
async def draft(body: DraftRequest) -> dict | JSONResponse:
    try:
        # generate_json 은 동기 호출이라 스레드에서 돌려 서버가 멈추지 않게 한다
        document = await asyncio.to_thread(draft_document, body)
    except LLMError:
        # BE 는 이 응답을 받으면 application.status 를 FAILED 로 바꾼다
        return error("LLM_ERROR", "서류 초안을 만들지 못했어요. 잠시 후 다시 시도해 주세요.", status_code=502)
    # 응답: {"data": {"document": schemas.Document}}
    return ok({"document": document.model_dump(by_alias=True)})
