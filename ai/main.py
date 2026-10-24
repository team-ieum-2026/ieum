# 주인: AI-A (라우터 등록은 이미 되어 있음. 각자 자기 폴더의 router.py 만 수정)
"""이음 AI 서버 (FastAPI, :8000). BE만 호출한다. 엔드포인트는 docs/API.md 4장.

실행: uvicorn main:app --reload --port 8000
문서: http://localhost:8000/docs
"""

from fastapi import FastAPI

from common.response import ok
from company.router import router as company_router
from document.router import router as document_router
from matching.router import router as matching_router
from profile.router import router as profile_router

app = FastAPI(title="이음 AI", version="P0")

app.include_router(profile_router)
app.include_router(matching_router)
app.include_router(company_router)
app.include_router(document_router)


@app.get("/ai/health", tags=["health"])
def health() -> dict:
    """GET /ai/health → { "data": { "status": "OK" } } (AI-A-06)"""
    return ok({"status": "OK"})
