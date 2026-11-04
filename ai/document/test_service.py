# 주인: AI-A
"""서류 조립(service.assemble) 테스트: 섹션 순서, 문장 id, 근거 변환, 제외 개수, 안내 문구, 자기소개서 대체.
긴 자기소개서 문장 나누기(service.split_long_sentences) 테스트: 호출 여부, 바꿔 넣기, 숫자 확인, 호출 실패.
가운데점 바꾸기(service.drop_middle_dots) 테스트.
자기소개서 문단 번호 정리(service.tidy_paragraphs) 테스트.

LLM 은 부르지 않는다. 검증 결과를 직접 만들어 넣는다.
실행: cd ai && .venv/bin/python -m unittest document.test_service -v
"""

import json
import unittest
from pathlib import Path
from unittest.mock import patch

from common.llm import LLMError
from document.llm_models import LlmDraft, LlmSplit
from document.service import SECTION_ORDER, assemble, company_note, drop_middle_dots, split_long_sentences, tidy_paragraphs
from schemas.document import Sentence
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


LONG = ("초기에는 평가셋 50건 중 9건에서 출력 형식 오류가 발생해, LLM 출력을 Pydantic schema로 정의하고 "
        "검증 실패 시 오류 내용을 프롬프트에 넣어 최대 2회 재시도하는 로직을 구현했습니다.")
SHORT = "온다랩의 인재상처럼 끝까지 확인하는 개발자입니다."


def cover_draft(*texts: str) -> LlmDraft:
    return LlmDraft.model_validate({"coverLetter": [{"text": t, "evidenceIds": ["E13"]} for t in texts]})


class SplitLongSentencesTest(unittest.TestCase):
    """긴 자기소개서 문장 나누기. generate_json 을 바꿔 끼워 LLM 을 부르지 않는다"""

    def split_with(self, draft: LlmDraft, response):
        with patch("document.service.generate_json") as fake:
            if isinstance(response, Exception):
                fake.side_effect = response
            else:
                fake.return_value = LlmSplit.model_validate(response)
            result = split_long_sentences(draft, application_id=1)
        return result, fake

    def test_no_long_sentence_no_call(self):
        draft = cover_draft(SHORT)
        result, fake = self.split_with(draft, {"splits": []})
        fake.assert_not_called()
        self.assertIs(result, draft)

    def test_split_replaces_and_keeps_evidence(self):
        parts = ["초기에는 평가셋 50건 중 9건에서 출력 형식 오류가 발생했습니다.",
                 "이를 해결하려고 LLM 출력을 Pydantic schema로 정의하고 최대 2회 재시도하는 로직을 구현했습니다."]
        result, _ = self.split_with(cover_draft(SHORT, LONG), {"splits": [{"index": 1, "sentences": parts}]})
        self.assertEqual([s.text for s in result.cover_letter], [SHORT, *parts])
        self.assertTrue(all(s.evidence_ids == ["E13"] for s in result.cover_letter))

    def test_changed_number_keeps_original(self):
        parts = ["초기에는 평가셋 50건 중 8건에서 출력 형식 오류가 발생했습니다.",  # 9 → 8
                 "이를 해결하려고 최대 2회 재시도하는 로직을 구현했습니다."]
        result, _ = self.split_with(cover_draft(LONG), {"splits": [{"index": 0, "sentences": parts}]})
        self.assertEqual([s.text for s in result.cover_letter], [LONG])

    def test_llm_error_keeps_original(self):
        draft = cover_draft(LONG)
        result, _ = self.split_with(draft, LLMError("서버 오류"))
        self.assertIs(result, draft)


class DropMiddleDotsTest(unittest.TestCase):
    def test_all_sentences_and_role(self):
        draft = LlmDraft.model_validate({
            "skillsSummary": [{"text": "면접 기록 저장 · 조회 API 구현", "evidenceIds": ["E1"]}],
            "projects": [{"itemId": 1, "role": "백엔드·인프라", "bullets": [{"text": "화면·API·배포 담당", "evidenceIds": ["E2"]}]}],
            "coverLetter": [{"text": "정산 서버와 금액 대사 기능 개발에 기여하겠습니다.", "evidenceIds": ["E3"]}],
        })
        result = drop_middle_dots(draft)
        self.assertEqual(result.skills_summary[0].text, "면접 기록 저장, 조회 API 구현")
        self.assertEqual(result.projects[0].bullets[0].text, "화면, API, 배포 담당")
        self.assertEqual(result.projects[0].role, "백엔드, 인프라")
        self.assertIs(result.cover_letter[0], draft.cover_letter[0])  # 가운데점이 없으면 그대로
        self.assertEqual(result.skills_summary[0].evidence_ids, ["E1"])


class TidyParagraphsTest(unittest.TestCase):
    def numbers(self, *given):
        sentences = [Sentence(sentence_id=f"cl-{i}", text="문장", source="AI", paragraph=p) for i, p in enumerate(given)]
        return [s.paragraph for s in tidy_paragraphs(sentences)]

    def test_renumbers_gaps_and_decreases(self):
        self.assertEqual(self.numbers(1, 3, 3, 2, 5), [1, 2, 2, 2, 3])

    def test_missing_joins_previous(self):
        self.assertEqual(self.numbers(None, 1, None, 2), [1, 1, 1, 2])

    def test_all_missing_stays_null(self):
        self.assertEqual(self.numbers(None, None), [None, None])

    def test_skills_have_no_paragraph(self):
        draft = {**DRAFT, "skillsSummary": [{**DRAFT["skillsSummary"][0], "paragraph": 2}],
                 "coverLetter": [{**DRAFT["coverLetter"][0], "paragraph": 2}]}
        document = build(load_request(), draft)
        self.assertIsNone(section(document, "SKILLS")["summary"][0]["paragraph"])
        self.assertEqual(section(document, "COVER_LETTER")["sentences"][0]["paragraph"], 1)

    def test_fallback_uses_blank_lines(self):
        req = load_request()
        req = req.model_copy(update={"student": req.student.model_copy(update={
            "cover_letter": req.student.cover_letter.model_copy(update={"content": "첫 문단입니다.\n\n둘째 문단입니다."})})})
        draft = {**DRAFT, "coverLetter": [{"text": "근거 없는 문장", "evidenceIds": []}]}
        sentences = section(build(req, draft), "COVER_LETTER")["sentences"]
        self.assertEqual([s["paragraph"] for s in sentences], [1, 2])


if __name__ == "__main__":
    unittest.main()
