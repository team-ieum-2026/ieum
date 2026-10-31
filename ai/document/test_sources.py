# 주인: AI-A
"""서류 근거 후보(sources.py) 테스트: 번호 접두어 순서, 기업 정보 근거 규칙.

E(수료생 자료) 개수는 AI-B build_candidates 가 정하므로 고정하지 않는다.
실행: cd ai && .venv/bin/python -m unittest document.test_sources -v
"""

import json
import unittest
from pathlib import Path

from document.sources import build_sources
from schemas.draft import DraftRequest

FIXTURE = Path(__file__).resolve().parent.parent / "eval" / "fixtures" / "draft_request.example.json"


def load_request(**company_changes) -> DraftRequest:
    """예시 요청. company_changes 로 기업 정보 필드를 바꾼다 (company_info=None 이면 기업 정보 없음)"""
    data = json.loads(FIXTURE.read_text(encoding="utf-8"))
    if company_changes.pop("company_info", ...) is None:
        data["companyInfo"] = None
    else:
        data["companyInfo"].update(company_changes)
    return DraftRequest.model_validate(data)


def company_sources(req: DraftRequest):
    return [s for s in build_sources(req) if s.eid.startswith("C")]


class SourcesTest(unittest.TestCase):
    def test_prefix_order(self):
        sources = build_sources(load_request())
        prefixes = "".join(s.eid[0] for s in sources)
        self.assertRegex(prefixes, r"^E+T+J+C+$")
        job = [s for s in sources if s.eid == "J1"][0]
        self.assertEqual((job.ref_type, job.ref_id, job.label), ("JOB_POSTING", 501, "공고 제목"))

    def test_tech_source_per_project(self):
        tech = [s for s in build_sources(load_request()) if s.eid.startswith("T")]
        first = tech[0]
        self.assertEqual((first.eid, first.ref_type, first.ref_id), ("T1", "PROJECT", 11))
        self.assertEqual(first.quote, "Spring Boot, MySQL, React")

    def test_company_source_has_url(self):
        sources = company_sources(load_request())
        issue = [s for s in sources if s.label.endswith("최근 이슈")][0]
        self.assertEqual((issue.ref_type, issue.ref_id), ("COMPANY_INFO", 3))
        self.assertEqual(issue.url, "https://news.example.com/onda-lab-ai-launch")
        self.assertIsNotNone(issue.collected_at)
        self.assertEqual([s.eid for s in sources], [f"C{i}" for i in range(1, len(sources) + 1)])

    def test_not_found_has_no_company(self):
        req = load_request(status="NOT_FOUND", talentKeywords=[], coreValues=[], recentIssue=None, sources=[])
        self.assertEqual(company_sources(req), [])

    def test_null_company_has_no_company(self):
        self.assertEqual(company_sources(load_request(company_info=None)), [])

    def test_item_without_source_skipped(self):
        data = json.loads(FIXTURE.read_text(encoding="utf-8"))["companyInfo"]
        kept = [s for s in data["sources"] if s["item"] != "recentIssue"]
        sources = company_sources(load_request(status="PARTIAL", sources=kept))
        self.assertTrue(sources)
        self.assertFalse(any(s.label.endswith("최근 이슈") for s in sources))

    def test_company_without_id_has_no_company(self):
        with self.assertLogs("ieum.document", level="WARNING"):
            self.assertEqual(company_sources(load_request(companyInfoId=None)), [])


if __name__ == "__main__":
    unittest.main()
