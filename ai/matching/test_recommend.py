# 주인: AI-B
"""임시 추천(fallback) 테스트. 색인 저장소는 임시 폴더에 직접 써 둔다. 임베딩 · LLM 은 쓰지 않는다.

실행: cd ai && .venv/Scripts/python -m unittest matching.test_recommend -v
"""

import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi.testclient import TestClient

from main import app
from matching import router
from matching.fallback import job_terms, recommend_from_index, region_tier
from matching.models import RecommendRequest
from matching.store import IndexStore

# 모의 프로필 (docs/API.md 3-1 형식)
PROFILE = {
    "roles": ["백엔드 API 구현"],
    "skills": [
        {"name": "Spring Boot", "evidence": [{"refType": "PROJECT", "refId": 11}]},
        {"name": "MySQL", "evidence": [{"refType": "PROJECT", "refId": 11}, {"refType": "PROJECT", "refId": 12}]},
        {"name": "Java", "evidence": [{"refType": "PROJECT", "refId": 12}]},
    ],
    "achievements": [],
    "certificates": ["정보처리기사"],
    "desiredJob": "백엔드 개발",
    "preferredRegion": "서울",
}


def entry(text: str, region: str = "서울 구로구", career_required: bool = False, **extra) -> dict:
    return {"matchText": text, "region": region, "careerRequired": career_required, "vector": None, **extra}


class FallbackCase(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.store = IndexStore(Path(self.tmp.name) / "jobs_index.json")

    def tearDown(self):
        self.tmp.cleanup()

    def put(self, entries: dict[int, dict]) -> None:
        self.store.save({str(k): v for k, v in entries.items()})

    def recommend(self, profile=None, exclude=(), top_k=3) -> list[dict]:
        req = RecommendRequest.model_validate({
            "studentId": 1, "profile": PROFILE if profile is None else profile,
            "excludeJobPostingIds": list(exclude), "topK": top_k,
        })
        return recommend_from_index(req, self.store).model_dump(by_alias=True)["items"]

    def ids(self, items: list[dict]) -> list[int]:
        return [i["jobPostingId"] for i in items]


class FilterTest(FallbackCase):
    def test_exclude_list(self):
        self.put({1: entry("[공고] 백엔드 Java"), 2: entry("[공고] 백엔드 Java"), 3: entry("[공고] 백엔드 Java")})
        self.assertEqual(self.ids(self.recommend(exclude=[2])), [1, 3])

    def test_career_required_is_excluded(self):
        self.put({1: entry("[공고] 백엔드 Java Spring Boot MySQL", career_required=True), 2: entry("[공고] Java")})
        self.assertEqual(self.ids(self.recommend()), [2])

    def test_zero_score_gives_empty(self):
        self.put({1: entry("[공고] 웹 디자이너\n[내용] Photoshop, Figma"), 2: entry("[공고] 요양 보호사")})
        self.assertEqual(self.recommend(), [])

    def test_zero_score_jobs_are_not_used_to_fill(self):
        self.put({1: entry("[공고] 백엔드"), 2: entry("[공고] 웹 디자이너"), 3: entry("[공고] 데이터 라벨링")})
        self.assertEqual(self.ids(self.recommend()), [1])

    def test_empty_profile_gives_empty(self):
        self.put({1: entry("[공고] 백엔드 Java")})
        self.assertEqual(self.recommend(profile={}), [])

    def test_empty_store_gives_empty_and_warns(self):
        with self.assertLogs("ieum.matching", level="WARNING") as logs:
            self.assertEqual(self.recommend(), [])
        self.assertTrue(any("index store is empty" in line for line in logs.output))

    def test_same_company_and_title_kept_once(self):
        same = {"companyName": "모의소프트", "title": "백엔드 개발자"}
        self.put({
            1: entry("[공고] 백엔드 Java", region="서울 구로구", **same),
            2: entry("[공고] 백엔드 Java", region="서울 강남구", **same),
            3: entry("[공고] 백엔드 Java", companyName="다른회사", title="백엔드 개발자"),
        })
        self.assertEqual(self.ids(self.recommend()), [1, 3])


class OrderTest(FallbackCase):
    def test_score_then_id(self):
        self.put({
            5: entry("[공고] Java"),                              # 1점
            3: entry("[공고] 백엔드\n[내용] Java, Spring Boot"),   # 3점
            4: entry("[공고] Java, MySQL"),                       # 2점
            2: entry("[공고] MySQL"),                             # 1점 (5 와 동점 → id 순)
        })
        self.assertEqual(self.ids(self.recommend(top_k=4)), [3, 4, 2, 5])

    def test_preferred_region_first_then_capital_area(self):
        self.put({
            1: entry("[공고] 백엔드 Java Spring Boot MySQL", region="경기 성남시"),  # 4점 · 수도권
            2: entry("[공고] Java", region="서울 마포구"),                         # 1점 · 서울
            3: entry("[공고] 백엔드 Java Spring Boot MySQL", region="부산 해운대구"),  # 4점 · 수도권 밖
            4: entry("[공고] Java MySQL", region="인천 연수구"),                   # 2점 · 수도권
        })
        self.assertEqual(self.ids(self.recommend(top_k=4)), [2, 1, 4, 3])

    def test_no_preferred_region_orders_by_score(self):
        self.put({1: entry("[공고] Java", region="서울 마포구"), 2: entry("[공고] 백엔드 Java MySQL", region="경기 성남시")})
        self.assertEqual(self.ids(self.recommend(profile={**PROFILE, "preferredRegion": None})), [2, 1])

    def test_region_tier(self):
        self.assertEqual(region_tier("서울특별시", "서울 구로구"), 0)
        self.assertEqual(region_tier("서울", "경기 성남시"), 1)
        self.assertEqual(region_tier("서울", "부산 해운대구"), 2)
        self.assertEqual(region_tier(None, "부산 해운대구"), 0)

    def test_top_k(self):
        self.put({i: entry("[공고] Java") for i in range(1, 6)})
        self.assertEqual(self.ids(self.recommend(top_k=2)), [1, 2])


class MatchTest(FallbackCase):
    def test_java_does_not_match_javascript(self):
        self.put({1: entry("[공고] 프론트엔드\n[내용] JavaScript, TypeScript")})
        self.assertEqual(self.recommend(profile={"skills": [{"name": "Java", "evidence": []}]}), [])

    def test_alias_and_spacing(self):
        self.put({1: entry("[내용] 스프링부트 기반 API"), 2: entry("[내용] spring  boot 경험")})
        items = self.recommend(profile={"skills": [{"name": "springboot", "evidence": [{"refType": "PROJECT", "refId": 1}]}]})
        self.assertEqual(self.ids(items), [1, 2])

    def test_generic_job_words_are_ignored(self):
        self.assertEqual(job_terms("백엔드 개발"), ["백엔드"])
        self.assertEqual(job_terms("데이터 엔지니어 / AI 개발자"), ["데이터", "AI"])
        self.put({1: entry("[공고] 응용 소프트웨어 개발자\n[내용] 개발 업무")})
        self.assertEqual(self.recommend(profile={"desiredJob": "백엔드 개발"}), [])


class ResponseTest(FallbackCase):
    def test_reasons_and_evidence(self):
        self.put({
            1: entry("[공고] 백엔드 개발자\n[내용] Spring Boot, MySQL"),
            2: entry("[공고] 백엔드 서버"),
        })
        items = self.recommend()
        # 기술은 프로필 근거, 희망 직무는 공고 근거(JOB_POSTING)
        self.assertEqual(items[0]["reasons"], [
            {"text": "보유 기술 Spring Boot · MySQL이(가) 공고에 나옵니다",
             "evidence": [{"refType": "PROJECT", "refId": 11}, {"refType": "PROJECT", "refId": 12}]},
            {"text": "희망 직무 '백엔드'와 공고 직무가 맞습니다", "evidence": [{"refType": "JOB_POSTING", "refId": 1}]},
        ])
        self.assertEqual(items[1]["reasons"], [
            {"text": "희망 직무 '백엔드'와 공고 직무가 맞습니다", "evidence": [{"refType": "JOB_POSTING", "refId": 2}]},
        ])
        # 모든 문장에 근거가 하나 이상 (API.md 3-2)
        self.assertTrue(all(r["evidence"] for i in items for r in i["reasons"]))

    def test_http_response_shape_is_unchanged(self):
        self.put({7: entry("[공고] 백엔드 Java"), 8: entry("[공고] Java"), 9: entry("[공고] MySQL")})
        client = TestClient(app)
        with patch.object(router, "store", self.store):
            res = client.post("/ai/matching/recommend", json={
                "studentId": 1, "profile": PROFILE, "excludeJobPostingIds": [9], "topK": 3,
            })
            empty = client.post("/ai/matching/recommend", json={"studentId": 1, "profile": {}})
        self.assertEqual(res.status_code, 200)
        body = res.json()
        self.assertEqual(set(body), {"data"})
        self.assertEqual(set(body["data"]), {"items"})
        self.assertEqual([(i["jobPostingId"], i["rank"]) for i in body["data"]["items"]], [(7, 1), (8, 2)])
        for item in body["data"]["items"]:
            self.assertEqual(set(item), {"jobPostingId", "rank", "reasons"})
            for r in item["reasons"]:
                self.assertEqual(set(r), {"text", "evidence"})
                for ev in r["evidence"]:
                    self.assertEqual(set(ev), {"refType", "refId"})
        self.assertEqual(empty.json(), {"data": {"items": []}})


if __name__ == "__main__":
    unittest.main()
