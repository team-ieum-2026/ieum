# 주인: AI-A
"""공통 응답 형식 (docs/API.md 1장): 성공 { "data": ... } · 실패 { "error": { "code", "message" } }"""

from typing import Any

from fastapi.responses import JSONResponse


def ok(data: Any) -> dict:
    return {"data": data}


def error(code: str, message: str, status_code: int = 400) -> JSONResponse:
    """code 는 UPPER_SNAKE, message 는 화면에 그대로 띄워도 되는 한국어 문장."""
    return JSONResponse(status_code=status_code, content={"error": {"code": code, "message": message}})


def not_implemented() -> JSONResponse:
    """라우터 뼈대용 고정 응답 (501)"""
    return error("NOT_IMPLEMENTED", "아직 구현 전이에요.", status_code=501)
