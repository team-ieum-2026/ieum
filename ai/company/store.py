# 주인: AI-B
"""기업 정보 저장 파일: ai/data/company/company_info.json

{ "_note": "...", "companies": [ { companyName, status, talentKeywords, coreValues, recentIssue, sources, collectedAt } ] }
형식은 schemas/company.py CompanyInfo 와 같다 (companyInfoId 는 DB id 라 저장하지 않는다).
status 는 FOUND · PARTIAL · NOT_FOUND 그대로 돌려준다. 파일에 없는 회사도 NOT_FOUND 로 돌려준다.
지금은 시드 DEV_SEED 공고의 가상 회사만 들어 있다 (검토표: company_info_review.md).
"""

import json
import re
from pathlib import Path
from typing import Any

DEFAULT_PATH = Path(__file__).resolve().parent.parent / "data" / "company" / "company_info.json"

# 회사명 비교에서 무시하는 법인 표기
CORP_MARKS = ("(주)", "㈜", "주식회사")


def normalize_name(name: str | None) -> str:
    """공백 · (주) · ㈜ · 주식회사 를 빼고 영문은 소문자로. 예: "(주) 온다랩" → "온다랩" """
    key = re.sub(r"\s+", "", name or "")
    for mark in CORP_MARKS:
        key = key.replace(mark, "")
    return key.lower()


class CompanyStore:
    def __init__(self, path: Path = DEFAULT_PATH):
        self.path = Path(path)

    def load(self) -> list[dict[str, Any]]:
        if not self.path.exists():
            return []
        return json.loads(self.path.read_text(encoding="utf-8")).get("companies", [])

    def find(self, company_name: str) -> dict[str, Any] | None:
        key = normalize_name(company_name)
        if not key:
            return None
        for entry in self.load():
            if normalize_name(entry.get("companyName")) == key:
                return entry
        return None
