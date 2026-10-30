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
from profile.evidence_check import allowed_refs, check_evidence, check_llm_items, check_llm_skills
from profile.llm_models import LlmItem, LlmProfile
from profile.models import ExtractRequest
from profile.router import extract_profile
from profile.rules import add_skills, own_skills
from profile.skill_aliases import SKILL_ALIASES, canonical_skill, mentioned_skills, mentions
from profile.sources import MAX_CANDIDATES, MAX_CHARS, MAX_CHUNK_CHARS, build_candidates, render_candidates, split_chunks
from schemas import CompetencyProfile, EvidenceRef
from schemas.profile import ProfileSkill

SAMPLE = Path(__file__).parent.parent / "data" / "samples" / "profile_extract_request.json"
PROJECT = {"refType": "PROJECT", "refId": 9401}
CAREER_SENTENCE = "모의 REST API 유지보수 경험"
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


def fake(roles=(), achievements=(), skills=()):
    result = LlmProfile(roles=list(roles), achievements=list(achievements), skills=list(skills))
    return patch.object(llm_extract, "generate_json", return_value=result)


def with_project(**fields) -> ExtractRequest:
    """샘플 프로젝트의 필드만 바꾼 요청"""
    data = json.loads(SAMPLE.read_text(encoding="utf-8"))
    data["projects"][0].update(fields)
    return ExtractRequest.model_validate(data)


def ref(ref_type: str, ref_id: int) -> EvidenceRef:
    return EvidenceRef(ref_type=ref_type, ref_id=ref_id)


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
            ("CAREER", 9201, "경력 '모의 회사' 회사 · 직무", "모의 회사 · 모의 백엔드 인턴"),
            ("CAREER", 9201, "경력 '모의 회사' 설명", CAREER_SENTENCE),
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
            item("백엔드 API 오류 해결", "E99", role),           # 통과 · 숫자 없음 · 번호 2개 중 1개 유효
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
             request(projects=[], coverLetter=None, careers=[]), "llm=SKIPPED"),
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

    def test_aws_services_map_to_aws(self):
        for raw in ["AWS", "aws", "AWS EC2", "AWS ECS", "aws ecs", "AWS Lambda", "AWS-RDS", "AWS(EC2)", "EC2", "RDS",
                    "s3", "ElastiCache", "Amazon Web Services"]:
            with self.subTest(raw=raw):
                self.assertEqual(canonical_skill(raw), "AWS")
        for raw in ["Lambda", "AWSome", "Docker"]:
            with self.subTest(raw=raw):
                self.assertEqual(canonical_skill(raw), raw)

    def test_aws_services_merge_into_one_skill(self):
        skills = add_skills(
            [ProfileSkill(name="AWS", evidence=[ref("PROJECT", 1)])],
            [ProfileSkill(name="AWS EC2", evidence=[ref("PROJECT", 1), ref("COVER_LETTER", 5)]),
             ProfileSkill(name="AWS ECS", evidence=[ref("PROJECT", 2)]), ProfileSkill(name="RDS", evidence=[])],
        )
        self.assertEqual(
            [(s.name, [(e.ref_type, e.ref_id) for e in s.evidence]) for s in skills],
            [("AWS", [("PROJECT", 1), ("COVER_LETTER", 5), ("PROJECT", 2)])],
        )

    def test_unknown_name_keeps_original(self):
        self.assertEqual(canonical_skill("  Kafka "), "Kafka")
        self.assertEqual(canonical_skill("자바스크립트 엔진"), "자바스크립트 엔진")

    def test_add_skills_uses_canonical_names(self):
        skills = add_skills(
            [ProfileSkill(name="자바", evidence=[ref("PROJECT", 1)]), ProfileSkill(name="Kafka", evidence=[])],
            [ProfileSkill(name="JAVA", evidence=[ref("PROJECT", 1), ref("CAREER", 2)]),
             ProfileSkill(name="스프링 부트", evidence=[ref("PROJECT", 2)]), ProfileSkill(name="kafka", evidence=[])],
        )
        self.assertEqual(
            [(s.name, [(e.ref_type, e.ref_id) for e in s.evidence]) for s in skills],
            [("Java", [("PROJECT", 1), ("CAREER", 2)]), ("Kafka", []), ("Spring Boot", [("PROJECT", 2)])],
        )


class MentionTest(unittest.TestCase):
    def test_mentions(self):
        cases = [
            ("Java로 배치를 고쳤습니다", "Java", True),
            ("JavaScript 화면", "Java", False),
            ("자바스크립트 화면", "Java", False),
            ("자바와 자바스크립트를 썼다", "Java", True),
            ("SpringBoot 서버", "Spring Boot", True),
            ("스프링부트로 API", "Spring Boot", True),
            ("C# 게임", "C", False),
            ("C와 Arduino", "C", True),
            ("MySQL 인덱스", "SQL", False),
            ("Gemini API 호출부 구현", "Gemini API", True),
            ("AWS(EC2, RDS) 배포", "AWS ECS", True),
            ("EC2 · RDS 배포 담당", "AWS", True),
            ("lambda 함수로 정렬", "AWS", False),
        ]
        for text, name, expected in cases:
            with self.subTest(text=text, name=name):
                self.assertEqual(mentions(text, name), expected)

    def test_mentioned_skills_keeps_order_and_dedupes(self):
        self.assertEqual(
            mentioned_skills("자바와 MySQL, java 로 구현", ["MySQL", "Java", "JAVA", "Redis"]), ["MySQL", "Java"]
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
        self.assertEqual(llm.call_args.kwargs["prompt_version"], "profile-extract-v3")

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
        # 직접 적은 기술(Java · Spring Boot · MySQL). 역할 기록 · 경력에 기술 이름이 없어 근거는 빈 배열
        self.assertEqual(profile["skills"], [{"name": n, "evidence": []} for n in ["Java", "Spring Boot", "MySQL"]])
        self.assertNotIn("quote", json.dumps(profile))
        self.assertNotIn("evidenceIds", json.dumps(profile))

    def test_no_material_skips_llm(self):
        data = json.loads(SAMPLE.read_text(encoding="utf-8"))
        for p in data["projects"]:
            p["readme"], p["roleNote"] = None, "  "
        req = request(projects=data["projects"], coverLetter=None, careers=[])
        with patch.object(llm_extract, "generate_json") as llm:
            profile = asyncio.run(extract_profile(req))["data"]["profile"]
        llm.assert_not_called()
        self.assertEqual((profile["roles"], profile["achievements"]), ([], []))
        self.assertEqual(profile["certificates"], ["모의 자격증"])
        self.assertEqual([s["name"] for s in profile["skills"]], ["Java", "Spring Boot", "MySQL"])

    def test_llm_error_falls_back_to_rules_only(self):
        with patch.object(llm_extract, "generate_json", side_effect=LLMError("fail")):
            profile = asyncio.run(extract_profile(request()))["data"]["profile"]
        self.assertEqual(profile["roles"], [])
        self.assertEqual(profile["achievements"], [])
        self.assertEqual(profile["certificates"], ["모의 자격증"])
        self.assertEqual(len(profile["skills"]), 3)


class CareerTest(unittest.TestCase):
    def test_career_is_allowed_ref(self):
        self.assertIn(("CAREER", 9201), allowed_refs(request()))
        self.assertNotIn(("CAREER", 9201), allowed_refs(request(careers=[])))

    def test_achievement_from_career_keeps_career_evidence(self):
        sentence = "조회 쿼리를 고쳐 화면 응답을 3초에서 1초로 줄였습니다."
        career = {**request().careers[0].model_dump(by_alias=True), "description": sentence}
        req = request(careers=[career])
        with fake(achievements=[item("조회 응답 3초 → 1초 단축", eid(sentence, req))]):
            profile = asyncio.run(extract_profile(req))["data"]["profile"]
        self.assertEqual(profile["achievements"][0]["evidence"], [{"refType": "CAREER", "refId": 9201}])

    def test_career_only_material_calls_llm(self):
        with fake() as llm:
            result = llm_extract.extract_by_llm(request(projects=[], coverLetter=None))
        llm.assert_called_once()
        self.assertEqual(result.status, "OK")
        self.assertIn(CAREER_SENTENCE, llm.call_args.args[0])

    def test_unknown_career_id_is_removed(self):
        profile = CompetencyProfile(skills=[ProfileSkill(name="Java", evidence=[ref("CAREER", 9999)])])
        self.assertEqual(check_evidence(profile, allowed_refs(request())).skills, [])


class OwnSkillsTest(unittest.TestCase):
    def test_team_stack_is_not_copied(self):
        # 직접 적은 기술은 Java · Spring Boot · MySQL. 팀 스택의 Redis 는 본인 근거가 없어 빠진다
        req = with_project(techStack=["Spring Boot", "MySQL", "Redis"], roleNote="백엔드 API 구현")
        skills = own_skills(req)
        self.assertEqual([s.name for s in skills], ["Java", "Spring Boot", "MySQL"])
        self.assertTrue(all(s.evidence == [] for s in skills))

    def test_role_note_and_career_mentions_add_evidence(self):
        req = with_project(techStack=["Spring Boot", "Redis"], roleNote="Redis 캐시와 스프링부트 API 담당")
        req.careers[0].description = "Java 배치 오류 대응"
        skills = {s.name: [(e.ref_type, e.ref_id) for e in s.evidence] for s in own_skills(req)}
        self.assertEqual(skills, {
            "Java": [("CAREER", 9201)], "Spring Boot": [("PROJECT", 9401)], "MySQL": [], "Redis": [("PROJECT", 9401)],
        })

    def test_self_entered_skill_without_evidence_survives_check(self):
        profile = CompetencyProfile(skills=[
            ProfileSkill(name="Java", evidence=[]),                   # 직접 적은 기술 → 남김
            ProfileSkill(name="Kafka", evidence=[ref("PROJECT", 1)]),  # 근거가 있었는데 다 지워짐 → 뺌
        ])
        self.assertEqual([s.name for s in check_evidence(profile, allowed_refs(request())).skills], ["Java"])


class LlmSkillsTest(unittest.TestCase):
    README = "# 모의 프로젝트\n## 내가 한 일\n- 자바로 정산 API 구현\n## 기술\n- Java, Spring Boot, Redis"

    def setUp(self):
        self.req = with_project(readme=self.README)
        self.by_eid = {c.eid: c for c in build_candidates(self.req)}
        self.own = eid("자바로 정산 API 구현", self.req)

    def test_aws_service_skill_becomes_aws(self):
        req = with_project(readme="# 모의 프로젝트\n- AWS ECS 배포 환경 구축")
        by_eid = {c.eid: c for c in build_candidates(req)}
        kept, _, _ = check_llm_skills([item("AWS ECS", eid("AWS ECS 배포 환경 구축", req))], by_eid)
        self.assertEqual([s.name for s in kept], ["AWS"])

    def test_skill_needs_its_name_in_chosen_quote(self):
        kept, excluded, m = check_llm_skills([item("자바", self.own), item("Kafka", self.own)], self.by_eid)
        self.assertEqual([(s.name, s.evidence) for s in kept], [("Java", [ref("PROJECT", 9401)])])
        self.assertEqual([(x.text, x.reason) for x in excluded], [("Kafka", "기술 이름이 본인 작업 근거에 없음")])
        self.assertEqual((m.generated, m.excluded), (2, 1))

    def test_team_tech_list_line_is_not_evidence(self):
        kept, excluded, _ = check_llm_skills([item("Redis", eid("Java, Spring Boot, Redis", self.req))], self.by_eid)
        self.assertEqual(kept, [])
        self.assertEqual(excluded[0].reason, "기술 이름이 본인 작업 근거에 없음")

    def test_llm_skills_are_merged_into_rule_skills(self):
        with fake(skills=[item("Java", self.own), item("Redis", "E99")]):
            profile = asyncio.run(extract_profile(self.req))["data"]["profile"]
        self.assertEqual(profile["skills"], [
            {"name": "Java", "evidence": [PROJECT]},
            {"name": "Spring Boot", "evidence": []},
            {"name": "MySQL", "evidence": []},
        ])


class WordingTest(unittest.TestCase):
    ROLE_NOTE = "백엔드 리드.\n메시지 유실 원인 분석 참여."
    README = "# 모의 프로젝트\n- 팀원과 함께 Redis 중계 구조 도입, 메시지 유실 0건 확인\n- 알림 설정"
    TOGETHER = "팀원과 함께 Redis 중계 구조 도입, 메시지 유실 0건 확인"

    def setUp(self):
        self.req = with_project(roleNote=self.ROLE_NOTE, readme=self.README)
        self.by_eid = {c.eid: c for c in build_candidates(self.req)}

    def reason(self, kind, text, *quotes):
        _, excluded, _ = check_llm_items(kind, [item(text, *(eid(q, self.req) for q in quotes))], self.by_eid)
        return excluded[0].reason if excluded else None

    def test_strong_word_must_be_in_source(self):
        self.assertIsNone(self.reason("roles", "백엔드 리드", "백엔드 리드."))
        self.assertEqual(self.reason("roles", "메시지 유실 원인 분석 주도", "메시지 유실 원인 분석 참여."),
                         "원문에 없는 강한 표현 주도")

    def test_hedge_word_must_stay_in_achievement(self):
        self.assertIsNone(self.reason("achievements", "팀원과 함께 Redis 도입, 메시지 유실 0건", self.TOGETHER))
        self.assertEqual(self.reason("achievements", "Redis 도입으로 메시지 유실 0건", self.TOGETHER),
                         "원문의 참여 표현 함께 가 빠짐")

    def test_task_only_achievement_is_excluded_but_role_is_kept(self):
        self.assertEqual(self.reason("achievements", "알림 설정", "알림 설정"), "결과 · 수치 없이 작업만 있음")
        self.assertEqual(self.reason("achievements", "감정 일기 챗봇 제작", "모의 프로젝트"), "결과 · 수치 없이 작업만 있음")
        self.assertIsNone(self.reason("roles", "알림 설정", "알림 설정"))


if __name__ == "__main__":
    unittest.main()
