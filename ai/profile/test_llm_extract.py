# 주인: AI-B
"""LLM 단계 테스트. generate_json 을 가짜로 바꿔서 실제 Gemini 를 부르지 않는다.

실행: cd ai && .venv/Scripts/python -m unittest profile.test_llm_extract -v
"""

import asyncio
import json
import unittest
from pathlib import Path
from unittest.mock import patch

from common.llm import LLMError
from profile import llm_extract
from profile.llm_models import LlmItem, LlmProfile
from profile.models import ExtractRequest
from profile.router import extract_profile
from profile.sources import MAX_CHARS, build_sources, render_sources
from schemas import EvidenceRef

SAMPLE = Path(__file__).parent.parent / "data" / "samples" / "profile_extract_request.json"
PROJECT = {"refType": "PROJECT", "refId": 9401}
QUOTE = "Spring Boot와 MySQL로 REST API 5개를 구현했습니다."


def request(**overrides) -> ExtractRequest:
    data = json.loads(SAMPLE.read_text(encoding="utf-8"))
    data.update(overrides)
    return ExtractRequest.model_validate(data)


def item(text, quote=QUOTE, evidence=(PROJECT,)) -> LlmItem:
    return LlmItem.model_validate({"text": text, "quote": quote, "evidence": list(evidence)})


def fake(roles=(), achievements=()):
    return patch.object(llm_extract, "generate_json", return_value=LlmProfile(roles=list(roles), achievements=list(achievements)))


class ExtractByLlmTest(unittest.TestCase):
    def test_valid_response_is_kept(self):
        with fake([item("백엔드 API 구현", "백엔드 API 구현")], [item("REST API 5개 구현")]):
            result = llm_extract.extract_by_llm(request())
        self.assertEqual(result.roles, ["백엔드 API 구현"])
        self.assertEqual([a.text for a in result.achievements], ["REST API 5개 구현"])
        self.assertEqual(result.achievements[0].evidence, [EvidenceRef(ref_type="PROJECT", ref_id=9401)])
        self.assertEqual(result.excluded, [])

    def test_unknown_ref_id_is_excluded(self):
        bad = item("REST API 5개 구현", evidence=[{"refType": "PROJECT", "refId": 1}])
        with fake(achievements=[bad]):
            result = llm_extract.extract_by_llm(request())
        self.assertEqual(result.achievements, [])
        self.assertEqual(result.excluded[0].reason, "근거 refId 가 입력에 없음")

    def test_unknown_ref_is_dropped_but_valid_one_keeps_item(self):
        mixed = item("REST API 5개 구현", evidence=[{"refType": "PROJECT", "refId": 1}, PROJECT])
        with fake(achievements=[mixed]):
            result = llm_extract.extract_by_llm(request())
        self.assertEqual([ev.ref_id for ev in result.achievements[0].evidence], [9401])

    def test_quote_not_in_source_is_excluded(self):
        with fake(achievements=[item("REST API 5개 구현", quote="Redis로 캐시를 5개 붙였습니다.")]):
            result = llm_extract.extract_by_llm(request())
        self.assertEqual(result.achievements, [])
        self.assertEqual(result.excluded[0].reason, "quote 가 근거 원문에 없음")

    def test_quote_matches_after_whitespace_normalization(self):
        spaced = QUOTE.replace(" ", "  \n")
        with fake(achievements=[item("REST API 5개 구현", quote=spaced)]):
            result = llm_extract.extract_by_llm(request())
        self.assertEqual(len(result.achievements), 1)

    def test_number_missing_in_quote_is_excluded(self):
        with fake(achievements=[item("REST API 50개 구현")]):
            result = llm_extract.extract_by_llm(request())
        self.assertEqual(result.achievements, [])
        self.assertIn("50", result.excluded[0].reason)

    def test_llm_error_returns_empty(self):
        with patch.object(llm_extract, "generate_json", side_effect=LLMError("fail")):
            result = llm_extract.extract_by_llm(request())
        self.assertEqual((result.roles, result.achievements, result.excluded), ([], [], []))


class RouterTest(unittest.TestCase):
    def test_merges_llm_result_with_rules(self):
        with fake([item("백엔드 API 구현", "백엔드 API 구현")], [item("REST API 5개 구현")]):
            profile = asyncio.run(extract_profile(request()))["data"]["profile"]
        self.assertEqual(profile["roles"], ["백엔드 API 구현"])
        self.assertEqual(profile["achievements"], [{"text": "REST API 5개 구현", "evidence": [PROJECT]}])
        self.assertEqual(profile["certificates"], ["모의 자격증"])
        self.assertEqual([s["name"] for s in profile["skills"]], ["Spring Boot", "MySQL"])
        self.assertNotIn("quote", json.dumps(profile))

    def test_no_material_skips_llm(self):
        data = json.loads(SAMPLE.read_text(encoding="utf-8"))
        for p in data["projects"]:
            p["readme"], p["roleNote"] = None, "  "
        req = request(projects=data["projects"], coverLetter=None)
        with patch.object(llm_extract, "generate_json") as llm:
            profile = asyncio.run(extract_profile(req))["data"]["profile"]
        llm.assert_not_called()
        self.assertEqual((profile["roles"], profile["achievements"]), ([], []))
        self.assertEqual(profile["certificates"], ["모의 자격증"])
        self.assertEqual([s["name"] for s in profile["skills"]], ["Spring Boot", "MySQL"])

    def test_llm_error_falls_back_to_rules_only(self):
        with patch.object(llm_extract, "generate_json", side_effect=LLMError("fail")):
            profile = asyncio.run(extract_profile(request()))["data"]["profile"]
        self.assertEqual(profile["roles"], [])
        self.assertEqual(profile["achievements"], [])
        self.assertEqual(profile["certificates"], ["모의 자격증"])
        self.assertEqual(len(profile["skills"]), 2)


class SourcesTest(unittest.TestCase):
    def test_tags_and_personal_info(self):
        req = request()
        req.basic.name, req.basic.email = "홍길동", "hong@example.com"
        text = render_sources(build_sources(req))
        for tag in ["[PROJECT#9401]", "[COVER_LETTER#9001]", "[CERTIFICATE#9301]", "[EDUCATION#9101]", "[COURSE#9002]"]:
            self.assertIn(tag, text)
        self.assertNotIn("홍길동", text)
        self.assertNotIn("hong@example.com", text)

    def test_contacts_are_masked(self):
        letter = {"content": "연락은 a.b@test.co.kr 또는 010-1234-5678 로 주세요. API 5개 구현", "updatedAt": "2026-10-26T12:00:00+09:00"}
        text = render_sources(build_sources(request(coverLetter=letter)))
        self.assertIn("[연락처]", text)
        self.assertNotIn("a.b@test.co.kr", text)
        self.assertNotIn("5678", text)
        self.assertIn("API 5개 구현", text)

    def test_long_text_is_truncated_and_logged(self):
        letter = {"content": "가" * (MAX_CHARS + 500), "updatedAt": "2026-10-26T12:00:00+09:00"}
        with self.assertLogs("ieum.profile", level="INFO") as logs:
            sources = build_sources(request(coverLetter=letter))
        cover = next(s for s in sources if s.ref_type == "COVER_LETTER")
        self.assertEqual(len(cover.text), MAX_CHARS)
        self.assertTrue(any("truncated" in line and "COVER_LETTER" in line for line in logs.output))


if __name__ == "__main__":
    unittest.main()
