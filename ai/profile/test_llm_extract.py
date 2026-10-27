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
from profile.evidence_check import check_llm_items
from profile.llm_models import LlmItem, LlmProfile
from profile.models import ExtractRequest
from profile.router import extract_profile
from profile.rules import merge_skills
from profile.skill_aliases import SKILL_ALIASES, canonical_skill
from profile.sources import MAX_CANDIDATES, MAX_CHARS, MAX_CHUNK_CHARS, build_candidates, render_candidates, split_chunks
from schemas import EvidenceRef

SAMPLE = Path(__file__).parent.parent / "data" / "samples" / "profile_extract_request.json"
PROJECT = {"refType": "PROJECT", "refId": 9401}
README_SENTENCE = "Spring Boot와 MySQL로 REST API 5개를 구현했습니다."
ROLE_NOTE = "백엔드 API 구현"
LETTER = "2026-10-26T12:00:00+09:00"


def unique_text(n: int) -> str:
    """서로 다른 한글 n 글자 (같은 조각이 합쳐지지 않게)"""
    return "".join(chr(0xAC00 + i) for i in range(n))


def request(**overrides) -> ExtractRequest:
    data = json.loads(SAMPLE.read_text(encoding="utf-8"))
    data.update(overrides)
    return ExtractRequest.model_validate(data)


def eid(quote: str, req: ExtractRequest | None = None) -> str:
    """샘플 요청의 근거 후보 중 quote 가 같은 후보의 번호"""
    return next(c.eid for c in build_candidates(req or request()) if c.quote == quote)


def item(text, *eids) -> LlmItem:
    return LlmItem.model_validate({"text": text, "evidenceIds": list(eids)})


def fake(roles=(), achievements=()):
    return patch.object(llm_extract, "generate_json", return_value=LlmProfile(roles=list(roles), achievements=list(achievements)))


class CandidatesTest(unittest.TestCase):
    def test_sample_candidates(self):
        cands = build_candidates(request())
        self.assertEqual([c.eid for c in cands], [f"E{i}" for i in range(1, len(cands) + 1)])
        rows = [(c.ref_type, c.ref_id, c.label, c.quote) for c in cands]
        self.assertEqual(rows, [
            ("PROJECT", 9401, "프로젝트 '모의 프로젝트' 제목", "모의 프로젝트"),
            ("PROJECT", 9401, "프로젝트 '모의 프로젝트' 역할", ROLE_NOTE),
            # README 첫 줄 "# 모의 프로젝트" 는 제목과 같아서 한 번만
            ("PROJECT", 9401, "프로젝트 '모의 프로젝트' README", README_SENTENCE),
            ("COVER_LETTER", 9001, "자기소개서", "모의 자기소개서입니다."),
            ("COVER_LETTER", 9001, "자기소개서", "모의 프로젝트에서 REST API 5개를 구현했습니다."),
            ("CERTIFICATE", 9301, "자격증", "모의 자격증 / 모의 발급 기관"),
            ("EDUCATION", 9101, "학력", "모의대학교 컴퓨터공학"),
            ("COURSE", 9002, "교육 과정", "모의 백엔드 과정"),
        ])
        self.assertIn(f"E3 | 프로젝트 '모의 프로젝트' README | {README_SENTENCE}", render_candidates(cands))

    def test_split_by_sentence_line_and_bullet(self):
        text = "## 주요 기능\n- 로그인 API 구현. 목록 조회 1.8s → 0.4s 개선!\n* 배포 자동화\n1. 테스트 작성"
        self.assertEqual(split_chunks(text), ["주요 기능", "로그인 API 구현.", "목록 조회 1.8s → 0.4s 개선!", "배포 자동화", "테스트 작성"])

    def test_short_chunks_are_dropped(self):
        self.assertEqual(split_chunks("#\n---\n- \n1.\nOK.\n가나\n성능 개선"), ["성능 개선"])

    def test_chunk_max_length(self):
        chunks = split_chunks(unique_text(MAX_CHUNK_CHARS * 2 + 10))
        self.assertEqual([len(c) for c in chunks], [MAX_CHUNK_CHARS, MAX_CHUNK_CHARS, 10])

    def test_personal_info_excluded_and_contacts_masked(self):
        req = request(coverLetter={"content": "연락은 a.b@test.co.kr 또는 010-1234-5678 로 주세요. API 5개 구현", "updatedAt": LETTER})
        req.basic.name, req.basic.email, req.basic.github_url = "홍길동", "hong@example.com", "github.com/hong"
        text = render_candidates(build_candidates(req))
        for secret in ["홍길동", "hong@example.com", "github.com/hong", "a.b@test.co.kr", "5678"]:
            self.assertNotIn(secret, text)
        self.assertIn("[연락처]", text)
        self.assertIn("API 5개 구현", text)

    def test_long_source_is_truncated_and_logged(self):
        letter = {"content": unique_text(MAX_CHARS + 500), "updatedAt": LETTER}
        with self.assertLogs("ieum.profile", level="INFO") as logs:
            cands = build_candidates(request(coverLetter=letter))
        cover = [c for c in cands if c.ref_type == "COVER_LETTER"]
        self.assertEqual(sum(len(c.quote) for c in cover), MAX_CHARS)
        self.assertTrue(any("truncated" in line and "COVER_LETTER" in line for line in logs.output))

    def test_too_many_candidates_are_cut(self):
        letter = {"content": "\n".join(f"문장 번호 {i} 입니다" for i in range(200)), "updatedAt": LETTER}
        with self.assertLogs("ieum.profile", level="INFO") as logs:
            cands = build_candidates(request(coverLetter=letter))
        self.assertEqual(len(cands), MAX_CANDIDATES)
        self.assertTrue(any("candidates truncated" in line for line in logs.output))


class CheckItemsTest(unittest.TestCase):
    def setUp(self):
        self.by_eid = {c.eid: c for c in build_candidates(request())}

    def test_same_project_eids_are_merged(self):
        title, role, readme = eid("모의 프로젝트"), eid(ROLE_NOTE), eid(README_SENTENCE)
        kept, excluded, _ = check_llm_items("achievements", [item("REST API 5개 구현", title, role, readme)], self.by_eid)
        self.assertEqual(excluded, [])
        self.assertEqual(kept[0].evidence, [EvidenceRef(ref_type="PROJECT", ref_id=9401)])

    def test_different_sources_are_kept_in_order(self):
        letter = eid("모의 프로젝트에서 REST API 5개를 구현했습니다.")
        kept, _, _ = check_llm_items("achievements", [item("REST API 5개 구현", eid(README_SENTENCE), letter)], self.by_eid)
        self.assertEqual([(e.ref_type, e.ref_id) for e in kept[0].evidence], [("PROJECT", 9401), ("COVER_LETTER", 9001)])

    def test_unknown_eid_is_dropped_but_valid_one_keeps_item(self):
        kept, _, _ = check_llm_items("achievements", [item("REST API 5개 구현", "E99", eid(README_SENTENCE).lower())], self.by_eid)
        self.assertEqual(kept[0].evidence, [EvidenceRef(ref_type="PROJECT", ref_id=9401)])

    def test_number_in_any_chosen_quote_is_enough(self):
        kept, _, _ = check_llm_items("achievements", [item("REST API 5개 구현", eid(ROLE_NOTE), eid(README_SENTENCE))], self.by_eid)
        self.assertEqual(len(kept), 1)

    def test_thousands_comma_is_ignored(self):
        req = request(coverLetter={"content": "회원 1000명이 쓰는 서비스를 운영했습니다.", "updatedAt": LETTER})
        by_eid = {c.eid: c for c in build_candidates(req)}
        kept, _, _ = check_llm_items("achievements", [item("회원 1,000명 서비스 운영", eid("회원 1000명이 쓰는 서비스를 운영했습니다.", req))], by_eid)
        self.assertEqual(len(kept), 1)


class MetricsTest(unittest.TestCase):
    def setUp(self):
        self.by_eid = {c.eid: c for c in build_candidates(request())}

    def test_counts(self):
        readme, role = eid(README_SENTENCE), eid(ROLE_NOTE)
        items = [
            item("REST API 5개 구현", readme, readme.lower()),  # 통과 · 숫자 있음 · 같은 번호는 1개로 셈
            item("백엔드 API 구현", "E99", role),                # 통과 · 숫자 없음 · 번호 2개 중 1개 유효
            item("데이터 분석", "E98"),                          # 제외: 번호가 후보에 없음
            item("REST API 50개 구현", readme),                 # 제외: 숫자 50 없음
        ]
        kept, excluded, m = check_llm_items("achievements", items, self.by_eid)
        self.assertEqual((len(kept), len(excluded)), (2, 2))
        self.assertEqual(
            (m.generated, m.evidence_ids, m.valid_evidence_ids, m.with_numbers, m.numbers_passed, m.excluded),
            (4, 5, 3, 2, 1, 2),
        )
        self.assertEqual((m.valid_evidence_ratio, m.number_pass_ratio), (0.6, 0.5))

    def test_empty_items_have_no_ratio(self):
        _, _, m = check_llm_items("roles", [], self.by_eid)
        self.assertEqual((m.generated, m.valid_evidence_ratio, m.number_pass_ratio), (0, None, None))

    def test_roles_and_achievements_are_summed(self):
        with fake([item("백엔드 API 구현", eid(ROLE_NOTE))], [item("REST API 5개 구현", eid(README_SENTENCE)), item("x", "E99")]):
            result = llm_extract.extract_by_llm(request())
        m = result.metrics
        self.assertEqual((result.status, m.generated, m.evidence_ids, m.valid_evidence_ids, m.excluded), ("OK", 3, 3, 2, 1))

    def test_router_logs_metrics_but_response_has_none(self):
        with fake([item("백엔드 API 구현", eid(ROLE_NOTE))], [item("REST API 5개 구현", eid(README_SENTENCE))]), \
             self.assertLogs("ieum.profile", level="INFO") as logs:
            body = asyncio.run(extract_profile(request()))
        line = next(line for line in logs.output if "profile metrics" in line)
        for part in ["studentId=9001", "llm=OK", "generated=2", "validEvidenceRatio=1.0", "numberPassRatio=1.0", "excluded=0"]:
            self.assertIn(part, line)
        self.assertNotIn("REST API 5개 구현", line)
        self.assertEqual(set(body["data"]), {"profile"})
        dumped = json.dumps(body)
        for key in ["generated", "validEvidence", "numberPass", "metrics"]:
            self.assertNotIn(key, dumped)

    def test_router_logs_status_when_llm_not_used(self):
        cases = [
            (patch.object(llm_extract, "generate_json", side_effect=LLMError("fail")), request(), "llm=FAILED"),
            (patch.object(llm_extract, "generate_json"),
             request(projects=[], coverLetter=None), "llm=SKIPPED"),
        ]
        for mock, req, expected in cases:
            with self.subTest(expected=expected), mock, self.assertLogs("ieum.profile", level="INFO") as logs:
                asyncio.run(extract_profile(req))
            self.assertTrue(any("profile metrics" in line and expected in line for line in logs.output))


class SkillAliasTest(unittest.TestCase):
    def test_aliases_map_to_canonical(self):
        cases = {
            "JAVA": "Java", "자바": "Java", "java": "Java", "파이썬": "Python", "mysql": "MySQL", "MSSQL": "MS SQL",
            "ms sql": "MS SQL", ".net": ".NET", "리눅스": "Linux", "포토샵": "Photoshop", "일러스트": "Illustrator",
            "일러스트레이터": "Illustrator", "피그마": "Figma", "JS": "JavaScript", "자바스크립트": "JavaScript",
            "TS": "TypeScript", "리액트": "React", "스프링부트": "Spring Boot", "springboot": "Spring Boot",
            "스프링 부트": "Spring Boot", " spring boot ": "Spring Boot",
        }
        for raw, expected in cases.items():
            with self.subTest(raw=raw):
                self.assertEqual(canonical_skill(raw), expected)

    def test_every_canonical_name_maps_to_itself(self):
        for name in SKILL_ALIASES:
            self.assertEqual(canonical_skill(name), name)

    def test_unknown_name_keeps_original(self):
        self.assertEqual(canonical_skill("  Kafka "), "Kafka")
        self.assertEqual(canonical_skill("자바스크립트 엔진"), "자바스크립트 엔진")

    def test_merge_uses_canonical_names(self):
        data = json.loads(SAMPLE.read_text(encoding="utf-8"))
        base = data["projects"][0]
        data["projects"] = [
            {**base, "itemId": 1, "techStack": ["자바", "springboot", "mysql", "Kafka"]},
            {**base, "itemId": 2, "techStack": ["JAVA", "스프링 부트", "MySQL", "kafka", "JS"]},
            {**base, "itemId": 3, "techStack": None},
        ]
        skills = merge_skills(ExtractRequest.model_validate(data))
        self.assertEqual(
            [(s.name, [e.ref_id for e in s.evidence]) for s in skills],
            [("Java", [1, 2]), ("Spring Boot", [1, 2]), ("MySQL", [1, 2]), ("Kafka", [1, 2]), ("JavaScript", [2])],
        )


class ExtractByLlmTest(unittest.TestCase):
    def test_valid_response_maps_eids_to_refs(self):
        with fake([item("백엔드 API 구현", eid(ROLE_NOTE))], [item("REST API 5개 구현", eid(README_SENTENCE))]):
            result = llm_extract.extract_by_llm(request())
        self.assertEqual(result.roles, ["백엔드 API 구현"])
        self.assertEqual([a.text for a in result.achievements], ["REST API 5개 구현"])
        self.assertEqual(result.achievements[0].evidence, [EvidenceRef(ref_type="PROJECT", ref_id=9401)])
        self.assertEqual(result.excluded, [])

    def test_unknown_eid_only_is_excluded(self):
        with fake(achievements=[item("REST API 5개 구현", "E99")]):
            result = llm_extract.extract_by_llm(request())
        self.assertEqual(result.achievements, [])
        self.assertEqual(result.excluded[0].reason, "근거 번호가 후보에 없음")

    def test_no_evidence_is_excluded(self):
        with fake(roles=[item("데이터 분석")]):
            result = llm_extract.extract_by_llm(request())
        self.assertEqual(result.roles, [])
        self.assertEqual(result.excluded[0].reason, "근거 없음")

    def test_number_not_in_chosen_evidence_is_excluded(self):
        # 50 은 어느 원문에도 없고, 5 가 있는 README 를 골라도 50 은 없다
        with fake(achievements=[item("REST API 50개 구현", eid(README_SENTENCE))]):
            result = llm_extract.extract_by_llm(request())
        self.assertEqual(result.achievements, [])
        self.assertIn("50", result.excluded[0].reason)

    def test_number_only_in_unchosen_evidence_is_excluded(self):
        # 5 는 README 에 있지만 고른 근거(역할 기록)에는 없다
        with fake(achievements=[item("REST API 5개 구현", eid(ROLE_NOTE))]):
            result = llm_extract.extract_by_llm(request())
        self.assertEqual(result.achievements, [])
        self.assertEqual(result.excluded[0].reason, "text 의 숫자 5 가 고른 근거에 없음")

    def test_excluded_text_is_not_logged(self):
        with fake(achievements=[item("비밀 성과 문장 50개", eid(README_SENTENCE))]), \
             self.assertLogs("ieum.profile", level="INFO") as logs:
            llm_extract.extract_by_llm(request())
        self.assertTrue(any("excludedCount=1" in line for line in logs.output))
        self.assertFalse(any("비밀 성과" in line for line in logs.output))

    def test_llm_error_returns_empty(self):
        with patch.object(llm_extract, "generate_json", side_effect=LLMError("fail")):
            result = llm_extract.extract_by_llm(request())
        self.assertEqual((result.roles, result.achievements, result.excluded), ([], [], []))

    def test_prompt_shows_numbered_candidates(self):
        with fake() as llm:
            llm_extract.extract_by_llm(request())
        prompt = llm.call_args.args[0]
        self.assertIn(f"{eid(README_SENTENCE)} | 프로젝트 '모의 프로젝트' README | {README_SENTENCE}", prompt)
        self.assertEqual(llm.call_args.kwargs["prompt_version"], "profile-extract-v2")

    def test_temperature_is_zero(self):
        with fake() as llm:
            llm_extract.extract_by_llm(request())
        self.assertEqual(llm.call_args.kwargs["temperature"], 0.0)


class RouterTest(unittest.TestCase):
    def test_merges_llm_result_with_rules(self):
        with fake([item("백엔드 API 구현", eid(ROLE_NOTE))], [item("REST API 5개 구현", eid(README_SENTENCE))]):
            profile = asyncio.run(extract_profile(request()))["data"]["profile"]
        self.assertEqual(profile["roles"], ["백엔드 API 구현"])
        self.assertEqual(profile["achievements"], [{"text": "REST API 5개 구현", "evidence": [PROJECT]}])
        self.assertEqual(profile["certificates"], ["모의 자격증"])
        self.assertEqual([s["name"] for s in profile["skills"]], ["Spring Boot", "MySQL"])
        self.assertNotIn("quote", json.dumps(profile))
        self.assertNotIn("evidenceIds", json.dumps(profile))

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


if __name__ == "__main__":
    unittest.main()
