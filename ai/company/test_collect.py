# 주인: AI-B
"""기업 정보 조회 1단계 테스트. 저장 파일은 임시 폴더에 직접 써 둔다. 외부 API 는 부르지 않는다.

실행: cd ai && .venv/Scripts/python -m unittest company.test_collect -v
"""

import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi.testclient import TestClient

from company import router
from company.store import DEFAULT_PATH, CompanyStore, normalize_name
from main import app
from schemas.company import CompanyInfo

# BE CompanyInfoService.collectOne · toInfo 가 읽는 키
BE_KEYS = {"status", "talentKeywords", "coreValues", "recentIssue", "sources"}
INFO_KEYS = {"companyInfoId", "companyName", "status", "talentKeywords", "coreValues", "recentIssue", "sources", "collectedAt"}
SOURCE = {"item": "talentKeywords", "url": "https://mock-a.example.com/careers", "collectedAt": "2026-10-24T08:00:00+09:00"}

COMPANIES = [
    {
        "companyName": "(주)모의에이",
        "status": "FOUND",
        "talentKeywords": ["끝까지 책임지는 사람"],
        "coreValues": ["책임감"],
        "recentIssue": "모의 서비스 출시 (모의 기사)",
        "sources": [SOURCE],
        "collectedAt": "2026-10-24T08:00:00+09:00",
    },
    {
        "companyName": "모의 비 주식회사",
        "status": "PARTIAL",
        "talentKeywords": ["데이터로 말하는 사람"],
        "coreValues": [],
        "recentIssue": None,
        "sources": [],
        "collectedAt": "2026-10-24T08:00:00+09:00",
    },
]


class CollectCase(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        path = Path(self.tmp.name) / "company_info.json"
        path.write_text(json.dumps({"companies": COMPANIES}, ensure_ascii=False), encoding="utf-8")
        self.store = CompanyStore(path)
        self.client = TestClient(app)

    def tearDown(self):
        self.tmp.cleanup()

    def post(self, body: dict, store: CompanyStore | None = None):
        with patch.object(router, "store", store or self.store):
            return self.client.post("/ai/company/collect", json=body)

    def info(self, company_name: str) -> dict:
        res = self.post({"companyName": company_name})
        self.assertEqual(res.status_code, 200)
        return res.json()["data"]["companyInfo"]


class LookupTest(CollectCase):
    def test_found_returns_stored_values(self):
        info = self.info("(주)모의에이")
        self.assertEqual(info["status"], "FOUND")
        self.assertEqual(info["talentKeywords"], ["끝까지 책임지는 사람"])
        self.assertEqual(info["coreValues"], ["책임감"])
        self.assertEqual(info["recentIssue"], "모의 서비스 출시 (모의 기사)")
        self.assertEqual(info["sources"], [SOURCE])
        self.assertEqual(info["collectedAt"], "2026-10-24T08:00:00+09:00")
        self.assertIsNone(info["companyInfoId"])

    def test_partial_keeps_empty_items(self):
        info = self.info("모의 비 주식회사")
        self.assertEqual(info["status"], "PARTIAL")
        self.assertEqual(info["coreValues"], [])
        self.assertIsNone(info["recentIssue"])

    def test_unknown_company_is_not_found(self):
        info = self.info("모의없는회사")
        self.assertEqual(info, {
            "companyInfoId": None, "companyName": "모의없는회사", "status": "NOT_FOUND",
            "talentKeywords": [], "coreValues": [], "recentIssue": None, "sources": [], "collectedAt": None,
        })

    def test_missing_store_file_is_not_found(self):
        res = self.post({"companyName": "(주)모의에이"}, store=CompanyStore(Path(self.tmp.name) / "none.json"))
        self.assertEqual(res.json()["data"]["companyInfo"]["status"], "NOT_FOUND")


class NameTest(CollectCase):
    def test_corp_marks_and_spaces_are_ignored(self):
        for name in ("모의에이", "㈜모의에이", "모의에이(주)", "주식회사 모의에이", " (주) 모의 에이 "):
            with self.subTest(name=name):
                self.assertEqual(self.info(name)["status"], "FOUND")
        self.assertEqual(self.info("(주)모의비")["status"], "PARTIAL")

    def test_response_name_is_request_name(self):
        # BE 는 요청한 이름으로 저장하므로 응답도 요청값(앞뒤 공백만 뺌)
        self.assertEqual(self.info(" 모의에이(주) ")["companyName"], "모의에이(주)")

    def test_different_name_does_not_match(self):
        self.assertEqual(self.info("모의에이랩")["status"], "NOT_FOUND")

    def test_normalize_name(self):
        self.assertEqual(normalize_name(" (주) Mock Lab "), "mocklab")
        self.assertEqual(normalize_name(None), "")

    def test_blank_or_missing_name_is_400(self):
        for body in ({"companyName": ""}, {"companyName": "   "}, {"companyName": None}, {}):
            with self.subTest(body=body):
                res = self.post(body)
                self.assertEqual(res.status_code, 400)
                self.assertEqual(res.json()["error"]["code"], "INVALID_REQUEST")
                self.assertEqual(set(res.json()["error"]), {"code", "message"})


class ShapeTest(CollectCase):
    def test_response_matches_be_and_schema(self):
        for name in ("(주)모의에이", "모의비", "모의없는회사"):
            with self.subTest(name=name):
                body = self.post({"companyName": name, "unknownKey": 1}).json()
                self.assertEqual(set(body), {"data"})
                self.assertEqual(set(body["data"]), {"companyInfo"})
                info = body["data"]["companyInfo"]
                self.assertEqual(set(info), INFO_KEYS)
                self.assertTrue(BE_KEYS <= set(info))
                self.assertIn(info["status"], {"FOUND", "PARTIAL", "NOT_FOUND"})
                self.assertIsInstance(info["talentKeywords"], list)
                self.assertIsInstance(info["coreValues"], list)
                self.assertIsInstance(info["sources"], list)
                self.assertTrue(info["recentIssue"] is None or isinstance(info["recentIssue"], str))
                for s in info["sources"]:
                    self.assertEqual(set(s), {"item", "url", "collectedAt"})
                # 공용 스키마로 다시 읽어도 같은 값
                self.assertEqual(CompanyInfo.model_validate(info).model_dump(by_alias=True), info)


class DemoFileTest(unittest.TestCase):
    """ai/data/company/company_info.json (시연용 가상 데이터)"""

    def setUp(self):
        self.companies = CompanyStore(DEFAULT_PATH).load()

    def test_every_entry_passes_schema(self):
        for c in self.companies:
            with self.subTest(company=c.get("companyName")):
                info = CompanyInfo.model_validate(c)
                self.assertIsNone(info.company_info_id)
                self.assertTrue(set(c) <= INFO_KEYS)

    def test_status_matches_contents(self):
        for c in self.companies:
            with self.subTest(company=c["companyName"]):
                filled = {k for k in ("talentKeywords", "coreValues", "recentIssue") if c[k]}
                expected = {3: "FOUND", 0: "NOT_FOUND"}.get(len(filled), "PARTIAL")
                self.assertEqual(c["status"], expected)
                # 채운 항목마다 출처가 있고, 출처는 채운 항목만 가리킨다
                self.assertEqual({s["item"] for s in c["sources"]}, filled)

    def test_mock_only(self):
        for c in self.companies:
            for s in c["sources"]:
                self.assertRegex(s["url"], r"^https://[a-z0-9-]+\.example\.com/")
            if c["recentIssue"]:
                self.assertIn("(가상)", c["recentIssue"])
        # 실존 기업과 이름이 겹칠 수 있는 회사는 넣지 않는다 (company_info_review.md)
        keys = [normalize_name(c["companyName"]) for c in self.companies]
        self.assertNotIn("한빛솔루션", keys)
        self.assertNotIn("모바일팩토리", keys)
        self.assertEqual(len(keys), len(set(keys)))

    def test_demo_lookup(self):
        store = CompanyStore(DEFAULT_PATH)
        with patch.object(router, "store", store):
            client = TestClient(app)
            status = {n: client.post("/ai/company/collect", json={"companyName": n}).json()["data"]["companyInfo"]["status"]
                      for n in ("온다랩", "데이터웨이브(주)", "(주)서울에이아이랩", "(주)한빛솔루션")}
        self.assertEqual(status, {"온다랩": "FOUND", "데이터웨이브(주)": "PARTIAL",
                                  "(주)서울에이아이랩": "NOT_FOUND", "(주)한빛솔루션": "NOT_FOUND"})


if __name__ == "__main__":
    unittest.main()
