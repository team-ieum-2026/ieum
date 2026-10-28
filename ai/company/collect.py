# 주인: AI-B
"""기업 정보 조회 1단계: 저장 파일에서 회사명으로 찾는다. 없으면 NOT_FOUND (지어내지 않음)."""

from company.store import CompanyStore
from schemas.company import CompanyInfo


def not_found(company_name: str) -> CompanyInfo:
    return CompanyInfo(company_name=company_name, status="NOT_FOUND")


def collect_company_info(company_name: str, store: CompanyStore) -> CompanyInfo:
    """company_name 은 앞뒤 공백을 뺀 요청값. 응답 companyName 도 요청값 그대로 (BE 는 요청한 이름으로 저장한다)."""
    entry = store.find(company_name)
    if entry is None:
        # TODO(AI-B-04): 2단계: 검색 기반 수집 (P1, 팀 결정 후)
        # 인재상 · 채용 페이지 + 최근 6개월 뉴스 1~2건 → 인재상 키워드 · 핵심 가치 · 최근 이슈 1개, 출처 URL · 수집일
        return not_found(company_name)
    # 저장값 그대로. companyInfoId 는 DB 저장 전이라 null (schemas/company.py)
    info = CompanyInfo.model_validate({**entry, "companyName": company_name})
    return info.model_copy(update={"company_info_id": None})
