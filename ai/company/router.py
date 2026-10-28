# 주인: AI-B
"""POST /ai/company/collect — 기업 정보 → company_info 형식 (AI-B-04, M5 · S7). 호출: BE-A (시연 전 1회)

1단계: 저장 파일(ai/data/company/company_info.json)에서 회사명으로 찾는다. 없으면 NOT_FOUND.
2단계(검색 수집)는 company/collect.py 의 TODO.
"""

from fastapi import APIRouter

from common.response import error, ok
from company.collect import collect_company_info
from company.models import CollectRequest
from company.store import CompanyStore

router = APIRouter(prefix="/ai/company", tags=["company"])

# 기업 정보 저장 파일. 테스트에서는 임시 경로로 바꾼다
store = CompanyStore()


@router.post("/collect")
async def collect(body: CollectRequest):
    # 요청: { companyName }
    # 응답: { companyInfo: { companyInfoId(null), companyName, status, talentKeywords, coreValues, recentIssue, sources, collectedAt } }
    # BE(CompanyInfoService)는 data.companyInfo 의 status · talentKeywords · coreValues · recentIssue · sources 만 읽는다
    company_name = (body.company_name or "").strip()
    if not company_name:
        return error("INVALID_REQUEST", "회사명을 적어 주세요.")
    info = collect_company_info(company_name, store)
    return ok({"companyInfo": info.model_dump(by_alias=True)})
