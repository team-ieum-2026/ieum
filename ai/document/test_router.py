# 주인: AI-A
"""POST /ai/document/draft 고정 응답 테스트: 예시 요청을 받고, BE 목업과 같은 서류를 돌려주는지.

실행: cd ai && .venv/bin/python -m unittest document.test_router -v
"""

import json
import unittest
from pathlib import Path

from fastapi import FastAPI
from fastapi.testclient import TestClient

from document.router import router

AI_DIR = Path(__file__).resolve().parent.parent
FIXTURES = AI_DIR / "eval" / "fixtures"
BE_MOCK = AI_DIR.parent / "backend" / "src" / "main" / "resources" / "ai-mock" / "document_draft.json"


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


class DraftStubTest(unittest.TestCase):
    def setUp(self):
        app = FastAPI()
        app.include_router(router)
        self.client = TestClient(app)
        self.request = load(FIXTURES / "draft_request.example.json")

    def test_returns_document(self):
        res = self.client.post("/ai/document/draft", json=self.request)
        self.assertEqual(res.status_code, 200)
        document = res.json()["data"]["document"]
        self.assertIn(document["companyInfoStatus"], {"FOUND", "PARTIAL", "NOT_FOUND"})
        self.assertEqual(document["sections"][0]["section"], "BASIC")
        # 값이 없어도 키는 null 로 남는다
        self.assertIn("url", document["sections"][6]["summary"][0]["evidence"][0])

    def test_same_as_be_mock(self):
        if not BE_MOCK.exists():
            self.skipTest("BE 목업 없음")
        res = self.client.post("/ai/document/draft", json=self.request)
        self.assertEqual(res.json(), load(BE_MOCK))

    def test_ai_sentences_have_evidence(self):
        document = self.client.post("/ai/document/draft", json=self.request).json()["data"]["document"]
        for section in document["sections"]:
            sentences = section.get("summary", []) + section.get("sentences", [])
            for item in section.get("items", []):
                sentences += item.get("bullets", [])
            for s in sentences:
                if s["source"] == "AI":
                    self.assertGreaterEqual(len(s["evidence"]), 1, s["sentenceId"])

    def test_bad_request_is_rejected(self):
        res = self.client.post("/ai/document/draft", json={"applicationId": 1})
        self.assertEqual(res.status_code, 422)


if __name__ == "__main__":
    unittest.main()
