# 주인: AI-A (형식) · 채우는 쪽: AI-B (POST /ai/company/collect, AI-B-04)
"""기업 정보 (DB company_info: status + info JSON + collected_at)

DB 에는 status 와 info({talentKeywords, coreValues, recentIssue, sources}) 가 따로 있지만
AI 서버를 오갈 때는 한 객체로 편다.
- AI-B 수집 결과: companyInfoId 는 null (아직 DB 저장 전)
- 서류 생성 요청: BE-A가 DB id 를 companyInfoId 로 채워 보낸다 → 근거 { refType: COMPANY_INFO, refId: companyInfoId }
  DB 행 모양 { id, companyName, status, info, collectedAt } 으로 보내도 받는다 (id → companyInfoId, info 를 펼침)

status
- FOUND: 인재상 · 핵심 가치 · 최근 이슈를 모두 찾음
- PARTIAL: 일부만 찾음. 못 찾은 항목은 빈 배열 · null (지어내지 않음)
- NOT_FOUND: 하나도 못 찾음. 화면에 "공고 외 정보 확인 불가"
"""

import json

from pydantic import model_validator

from schemas.base import CamelModel
from schemas.document import CompanyInfoStatus


class CompanyInfoSource(CamelModel):
    """출처 한 건. item 은 이 출처가 뒷받침하는 항목: talentKeywords | coreValues | recentIssue"""

    item: str
    url: str
    collected_at: str  # ISO 8601 +09:00


class CompanyInfo(CamelModel):
    company_info_id: int | None = None
    company_name: str
    status: CompanyInfoStatus
    talent_keywords: list[str] = []
    core_values: list[str] = []
    recent_issue: str | None = None
    sources: list[CompanyInfoSource] = []
    collected_at: str | None = None

    @model_validator(mode="before")
    @classmethod
    def _flatten_db_row(cls, data):
        if not isinstance(data, dict) or ("info" not in data and "id" not in data):
            return data
        data = dict(data)
        info = data.pop("info", None)
        if isinstance(info, str):  # JSON 컬럼이 문자열로 올 때
            info = json.loads(info)
        for key, value in (info or {}).items():
            data.setdefault(key, value)
        if "id" in data:
            data.setdefault("companyInfoId", data.pop("id"))
        return data
