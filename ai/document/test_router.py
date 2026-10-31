# 주인: AI-A
"""POST /ai/document/draft 라우터 테스트. LLM 은 가짜 답(FAKE_DRAFT)으로 바꿔 끼운다 (키 · 비용 없이 실행).

실행: cd ai && .venv/bin/python -m unittest document.test_router -v
"""

import json
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi import FastAPI
from fastapi.testclient import TestClient

from common.llm import LLMError
from document.llm_models import LlmDraft
from document.router import router

FIXTURES = Path(__file__).resolve().parent.parent / "eval" / "fixtures"

# 예시 요청의 근거 후보 번호에 맞춘 가짜 LLM 답 (E6 · E8: 모아요 README, T1: 모아요 기술, C1: 온다랩 인재상)
FAKE_DRAFT = LlmDraft.model_validate({
    "skillsSummary": [{"text": "Spring Boot로 모집글 CRUD 등 REST API 12개 설계 · 구현", "evidenceIds": ["T1", "E6"]}],
    "tags": ["Spring Boot"],
    "projects": [{"itemId": 11, "role": "백엔드", "bullets": [
        {"text": "목록 조회 평균 응답을 1.8초에서 0.4초로 단축", "evidenceIds": ["E8"]}]}],
    "coverLetter": [{"text": "끝까지 책임지는 사람이라는 인재상처럼 끝까지 확인하는 개발자입니다.", "evidenceIds": ["C1", "E15"]}],
})


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


class DraftRouterTest(unittest.TestCase):
    def setUp(self):
        app = FastAPI()
        app.include_router(router)
        self.client = TestClient(app)
        self.request = load(FIXTURES / "draft_request.example.json")

    def post(self, body: dict):
        return self.client.post("/ai/document/draft", json=body)

    @patch("document.service.generate_json", return_value=FAKE_DRAFT)
    def test_returns_document(self, _llm):
        res = self.post(self.request)
        self.assertEqual(res.status_code, 200)
        document = res.json()["data"]["document"]
        self.assertEqual(document["companyInfoStatus"], "FOUND")
        self.assertEqual(document["sections"][0]["section"], "BASIC")
        # 값이 없어도 키는 null 로 남는다
        self.assertIn("url", document["sections"][6]["summary"][0]["evidence"][0])

    @patch("document.service.generate_json", return_value=FAKE_DRAFT)
    def test_ai_sentences_have_evidence(self, _llm):
        document = self.post(self.request).json()["data"]["document"]
        for section in document["sections"]:
            sentences = section.get("summary", []) + section.get("sentences", [])
            for item in section.get("items", []):
                sentences += item.get("bullets", [])
            for s in sentences:
                if s["source"] == "AI":
                    self.assertGreaterEqual(len(s["evidence"]), 1, s["sentenceId"])

    @patch("document.service.generate_json", side_effect=LLMError("응답 형식 오류"))
    def test_llm_failure_is_error_response(self, _llm):
        res = self.post(self.request)
        self.assertEqual(res.status_code, 502)
        self.assertEqual(res.json()["error"]["code"], "LLM_ERROR")

    def test_bad_request_is_rejected(self):
        res = self.post({"applicationId": 1})
        self.assertEqual(res.status_code, 422)


if __name__ == "__main__":
    unittest.main()
