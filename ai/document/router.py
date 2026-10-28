# 주인: AI-A
"""POST /ai/document/draft — 등록 자료 + 프로필 + 공고 + 기업 정보 → 서류 JSON (AI-A-03, S7). 호출: BE-A (비동기)"""

import json
from pathlib import Path

from fastapi import APIRouter

from common.response import ok
from schemas.draft import DraftRequest, DraftResult

router = APIRouter(prefix="/ai/document", tags=["document"])

# 실제 생성 전까지 쓰는 고정 응답. BE 목업(backend/.../ai-mock/document_draft.json)의 data 와 같은 서류
STUB_PATH = Path(__file__).resolve().parent.parent / "eval" / "fixtures" / "document_draft.example.json"
STUB = DraftResult.model_validate(json.loads(STUB_PATH.read_text(encoding="utf-8")))


@router.post("/draft")
async def draft_document(body: DraftRequest) -> dict:
    # TODO(AI-A-03): body.student · profile · job · companyInfo 로 자기소개서 · 이력서 섹션 생성 (지금은 고정 응답)
    # TODO(AI-A-04): source=AI 문장은 근거 1개 이상, 근거 없는 문장은 제외하고 excludedSentenceCount 에 기록
    # TODO(AI-A-05): 기업 정보가 없거나 일부면 companyInfoStatus PARTIAL · NOT_FOUND
    # 응답: {"data": {"document": schemas.Document}}
    return ok(STUB.model_dump(by_alias=True))
