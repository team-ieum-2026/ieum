# 주인: AI-B
"""POST /ai/company/collect — 기업 정보 웹 검색 수집 → company_info 형식 (AI-B-04, S7). 호출: BE-A (시연 전 1회)"""

from fastapi import APIRouter, Request

from common.response import not_implemented

router = APIRouter(prefix="/ai/company", tags=["company"])


@router.post("/collect")
async def collect_company_info(request: Request):
    # TODO(AI-B-04): 인재상 · 채용 페이지 + 최근 6개월 뉴스 1~2건 → 인재상 키워드 · 핵심 가치 · 최근 이슈 1개
    # 출처 URL · 수집일 저장. 못 찾으면 status NOT_FOUND (지어내지 않음)
    return not_implemented()
