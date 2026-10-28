# 주인: AI-B
"""POST /ai/company/collect 요청 (BE-A CompanyInfoService.collectOne: { companyName })
응답은 schemas/company.py CompanyInfo 를 { companyInfo: ... } 로 감싼다.
"""

from schemas.base import CamelModel


class CollectRequest(CamelModel):
    """BE 는 companyName 하나만 보낸다. 모르는 키는 무시. 빠지면 빈 문자열로 받아 400 으로 돌려준다."""

    company_name: str | None = ""
