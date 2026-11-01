# 주인: AI-B
"""공고 조건(필수 기술 · 직무군) 규칙과 추천 정렬 · 필터 테스트. 가짜 임베딩 · 가짜 리랭커만 쓴다.

실행: cd ai && .venv/Scripts/python -m unittest matching.test_job_conditions -v
"""

import tempfile
import unittest
from pathlib import Path

from matching import job_conditions as jc
from matching.models import RecommendRequest
from matching.recommend import recommend_jobs
from matching.store import IndexStore
from matching.test_rerank import PROFILE, entry, fake_embed_query, fake_reranker, setUpModule, tearDownModule  # noqa: F401

MODEL = "fake-embedding"

# PROFILE: 백엔드 개발 · 서울 · 기술 Spring Boot, MySQL
FULL = "[공고] 백엔드 개발자\n[직무] 응용 소프트웨어 개발자\n[내용] 2. 자격요건\n· Spring Boot · MySQL 사용 가능자\n3. 우대사항\n· Kafka"
MISSING = "[공고] 백엔드 개발자 채용\n[직무] 웹 개발자\n[내용] 자격요건\n· Spring Boot · Redis 사용 가능자"
NEUTRAL = "[공고] 서버 개발자\n[직무] 응용 소프트웨어 개발자\n[내용] 결제 서버 개발"
FRONT = "[공고] 프론트엔드 개발자\n[직무] 웹 개발자\n[내용] 자격요건\n· React"
UNKNOWN = "[공고] 신입 개발자 모집\n[직무] 기타 컴퓨터 시스템 전문가\n[내용] 사내 시스템 개발"


class RequiredSkillsTest(unittest.TestCase):
    def test_only_requirement_section_is_read(self):
        self.assertEqual(jc.required_skills(FULL), ["Spring Boot", "MySQL"])  # 우대(Kafka)는 뺌

    def test_one_line_and_field_formats(self):
        self.assertEqual(jc.required_skills("[내용] 지원 자격 : Java · SQL 사용 가능자, 신입 지원 가능 우대 : Redis"), ["Java", "SQL"])
        self.assertEqual(jc.required_skills("[필요 역량] 문서작성 (워드프로세스 활용),기타(리액트, 타입스크립트 사용 경험)\n[우대 조건] Vue.js"),
                         ["TypeScript", "React"])

    def test_spring_boot_is_not_also_counted_as_spring(self):
        self.assertEqual(jc.required_skills("자격요건\n- Spring Boot"), ["Spring Boot"])

    def test_broad_posting_term_accepts_specific_student_skill_only_one_way(self):
        self.assertFalse(jc.missing_required("자격요건\n- Spring, SQL", ["Spring Boot", "MySQL"]))
        self.assertTrue(jc.missing_required("자격요건\n- Spring Boot", ["Spring"]))

    def test_no_required_skill_is_neutral(self):
        self.assertIsNone(jc.missing_required(NEUTRAL, ["Java"]))
        self.assertIsNone(jc.missing_required("[내용] 우대사항\n- Java", []))  # 우대에만 있으면 필수 아님

    def test_missing_one_skill(self):
        self.assertTrue(jc.missing_required(MISSING, ["Spring Boot", "MySQL"]))


class FamilyTest(unittest.TestCase):
    def test_title_first_then_job_field(self):
        self.assertEqual(jc.posting_family("백엔드 개발자 (AI 상담 서비스팀) 신입", ""), "BE")
        self.assertEqual(jc.posting_family(None, "[공고] AI 학습 챗봇 개발\n[직무] 응용 소프트웨어 개발자"), "AI")
        self.assertEqual(jc.posting_family("신입 모집", "[직무] 웹 디자이너"), "DESIGN")

    def test_unknown_is_allowed(self):
        self.assertIsNone(jc.posting_family("신입 개발자 모집", UNKNOWN))
        self.assertTrue(jc.family_allowed("백엔드 개발", "신입 개발자 모집", UNKNOWN))
        self.assertTrue(jc.family_allowed("마케터", "프론트엔드 개발자", FRONT))  # 희망 직무를 모르면 통과

    def test_different_family_is_not_allowed(self):
        self.assertFalse(jc.family_allowed("백엔드 개발", "프론트엔드 개발자", FRONT))


class RecommendOrderTest(unittest.TestCase):
    def run_recommend(self, entries: dict, scores: dict, profile: dict = PROFILE) -> list[int]:
        store = IndexStore(Path(tempfile.mkdtemp()) / "index.json")
        store.save(entries)
        data = recommend_jobs(RecommendRequest(student_id=1, profile=profile), store,
                              embed_query=fake_embed_query, rerank_fn=fake_reranker(scores), model_name=MODEL)
        return [item.job_posting_id for item in data.items]

    def test_missing_skill_goes_after_full_and_neutral(self):
        ids = self.run_recommend(
            {"1": entry(MISSING), "2": entry(FULL), "3": entry(NEUTRAL)},
            {MISSING: 0.9, FULL: 0.6, NEUTRAL: 0.7},
        )
        self.assertEqual(ids, [3, 2, 1])  # 중립(0.7) · 전부 충족(0.6)은 점수순, 일부 빠짐(0.9)은 뒤로

    def test_neutral_is_not_penalized(self):
        ids = self.run_recommend({"1": entry(NEUTRAL), "2": entry(FULL)}, {NEUTRAL: 0.8, FULL: 0.5})
        self.assertEqual(ids, [1, 2])

    def test_other_family_is_filtered_but_unknown_passes(self):
        ids = self.run_recommend({"1": entry(FRONT), "2": entry(UNKNOWN), "3": entry(FULL)},
                                 {FRONT: 0.95, UNKNOWN: 0.4, FULL: 0.6})
        self.assertEqual(ids, [3, 2])

    def test_empty_when_only_other_family(self):
        self.assertEqual(self.run_recommend({"1": entry(FRONT)}, {FRONT: 0.95}), [])

    def test_threshold_still_applies(self):
        self.assertEqual(self.run_recommend({"1": entry(FULL)}, {FULL: 0.01}), [])

    def test_region_order_still_comes_first(self):
        ids = self.run_recommend(
            {"1": entry(FULL, region="경기 성남시"), "2": entry(MISSING, region="서울 구로구")},
            {FULL: 0.9, MISSING: 0.5},
        )
        self.assertEqual(ids, [2, 1])  # 희망 시 · 도(서울) 우선은 그대로, 그 안에서 기술 정렬


if __name__ == "__main__":
    unittest.main()
