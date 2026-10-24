# 주인: AI-A
"""근거 (docs/API.md 3-2)"""

from typing import Literal

from schemas.base import CamelModel

RefType = Literal["PROJECT", "COVER_LETTER", "CERTIFICATE", "EDUCATION", "COURSE", "COMPANY_INFO"]


class EvidenceRef(CamelModel):
    """역량 프로필 안에서 쓰는 짧은 근거 참조: { refType, refId }"""

    ref_type: RefType
    ref_id: int


class Evidence(EvidenceRef):
    """문장에 붙는 근거. 기업 정보 근거(COMPANY_INFO)는 url · collectedAt 을 채운다."""

    label: str
    quote: str
    url: str | None = None
    collected_at: str | None = None
