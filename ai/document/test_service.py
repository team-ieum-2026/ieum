# 주인: AI-A
"""서류 조립(service.assemble) 테스트: 섹션 순서, 문장 id, 근거 변환, 제외 개수, 안내 문구, 자기소개서 대체.

LLM 은 부르지 않는다. 검증 결과를 직접 만들어 넣는다.
실행: cd ai && .venv/bin/python -m unittest document.test_service -v
"""

import json
import unittest
from pathlib import Path

from document.llm_models import LlmDraft
from document.service import SECTION_ORDER, assemble, company_note
from document.sources import build_sources
from document.verify import verify
from schemas.draft import DraftRequest

FIXTURE = Path(__file__).resolve().parent.parent / "eval" / "fixtures" / "draft_request.example.json"

DRAFT = {
    "skillsSummary": [
        {"text": "Spring Boot로 모집글 CRUD 등 REST API 12개 설계 · 구현", "evidenceIds": ["T1", "E6"]},
        {"text": "Kubernetes로 배포 자동화", "evidenceIds": ["E99"]},  # 제외: no_evidence
    ],
    "tags": ["spring boot", "React"],
    "projects": [
        {"itemId": 12, "role": "백엔드와 AI 연동", "bullets": [
            {"text": "Gemini API로 면접 질문 생성 · 피드백 기능 구현", "evidenceIds": ["E13", "T2"]}]},
        {"itemId": 11, "role": "백엔드 리드", "bullets": [
            {"text": "목록 응답 1.8초 → 0.2초 개선", "evidenceIds": ["E8"]},  # 제외: number_mismatch
            {"text": "목록 조회 평균 응답을 1.8초에서 0.4초로 단축", "evidenceIds": ["E7", "E8"]}]},
    ],
    "coverLetter": [{"text": "온다랩의 인재상처럼 끝까지 확인하는 개발자입니다.", "evidenceIds": ["C1", "E15"]}],
}


def load_request(**changes) -> DraftRequest:
    data = json.loads(FIXTURE.read_text(encoding="utf-8"))
    data.update(changes)
    return DraftRequest.model_validate(data)


def build(req: DraftRequest, draft: dict = DRAFT) -> dict:
    sources = build_sources(req)
    document = assemble(req, sources, verify(LlmDraft.model_validate(draft), req, sources))
    return document.model_dump(by_alias=True)


def section(document: dict, name: str) -> dict:
    return [s for s in document["sections"] if s["section"] == name][0]


class AssembleTest(unittest.TestCase):
    def test_section_order(self):
        document = build(load_request())
        self.assertEqual([s["section"] for s in document["sections"]], SECTION_ORDER)

    def test_projects_follow_llm_order_with_continuous_ids(self):
        items = section(build(load_request()), "PROJECTS")["items"]
        self.assertEqual([i["itemId"] for i in items], ["prj-12", "prj-11"])
        self.assertEqual([b["sentenceId"] for i in items for b in i["bullets"]], ["pj-1", "pj-2"])
        # 역할 기록에 없는 role("백엔드 리드")은 null, 제목 · 인원은 등록 자료 그대로
        self.assertEqual((items[1]["role"], items[1]["title"], items[1]["teamSize"]), (None, "모아요 · 스터디 모집 웹서비스", 4))

    def test_evidence_and_excluded_count(self):
        document = build(load_request())
        self.assertEqual(document["excludedSentenceCount"], 2)
        skills = section(document, "SKILLS")
        self.assertEqual(skills["tags"], ["Spring Boot"])  # React: 본인 기술 아님
        evidence = skills["summary"][0]["evidence"]
        self.assertEqual([(e["refType"], e["refId"]) for e in evidence], [("PROJECT", 11), ("PROJECT", 11)])
        company = section(document, "COVER_LETTER")["sentences"][0]["evidence"][0]
        self.assertEqual(company["refType"], "COMPANY_INFO")
        self.assertIsNotNone(company["url"])

    def test_cover_letter_falls_back_to_original(self):
        draft = {**DRAFT, "coverLetter": [{"text": "근거 없는 문장", "evidenceIds": []}]}
        sentences = section(build(load_request(), draft), "COVER_LETTER")["sentences"]
        self.assertTrue(sentences)
        self.assertTrue(all(s["source"] == "REGISTERED" and s["evidence"] == [] for s in sentences))

    def test_company_status_and_note(self):
        self.assertIsNone(build(load_request())["companyInfoNote"])
        document = build(load_request(companyInfo=None))
        self.assertEqual(document["companyInfoStatus"], "NOT_FOUND")
        self.assertIn("공고 내용만", document["companyInfoNote"])
        partial = load_request()
        partial = partial.model_copy(update={"company_info": partial.company_info.model_copy(
            update={"status": "PARTIAL", "recent_issue": None})})
        self.assertEqual(company_note(partial, "PARTIAL"), "온다랩의 최근 이슈를 찾지 못해 인재상 · 핵심 가치만 반영했어요")


if __name__ == "__main__":
    unittest.main()
