# 주인: AI-B
"""임베딩 · 리랭커 추천(matching/recommend.py) 테스트. 가짜 임베딩 · 가짜 리랭커만 쓴다.
안전장치: 실제 Gemini 클라이언트 · 리랭커 모델 로드를 부르면 바로 실패한다 (setUpModule).

실행: cd ai && .venv/Scripts/python -m unittest matching.test_rerank -v
"""

import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi.testclient import TestClient

from common import embedding, reranker
from main import app
from matching import config, recommend, router
from matching.fallback import recommend_from_index
from matching.models import RecommendRequest
from matching.recommend import GENERIC_REASON, build_query, recommend_jobs
from matching.store import IndexStore
from schemas import CompetencyProfile

MODEL = "fake-embedding"
QUERY_VECTOR = [1.0, 0.0]

PROFILE = {
    "roles": ["백엔드 API 구현"],
    "skills": [
        {"name": "Spring Boot", "evidence": [{"refType": "PROJECT", "refId": 11}]},
        {"name": "MySQL", "evidence": [{"refType": "PROJECT", "refId": 12}]},
    ],
    "achievements": [{"text": "REST API 5개 구현", "evidence": [{"refType": "PROJECT", "refId": 11}]}],
    "certificates": ["정보처리기사"],
    "desiredJob": "백엔드 개발",
    "preferredRegion": "서울",
}


class RealCallBlocked(BaseException):
    """recommend 는 Exception 만 잡아 대체 경로로 넘기므로, 실제 호출은 BaseException 으로 막아 테스트가 깨지게 한다."""


def _blocked(*args, **kwargs):
    raise RealCallBlocked("테스트에서 실제 Gemini · 리랭커 모델을 불렀어요. 가짜를 넘기세요.")


_guards = [
    patch.object(embedding, "_client", side_effect=_blocked),
    patch("common.llm._client", side_effect=_blocked),
    patch.object(reranker, "_load_model", side_effect=_blocked),
]


def setUpModule():
    for guard in _guards:
        guard.start()


def tearDownModule():
    for guard in _guards:
        guard.stop()


def entry(text, similarity=1.0, region="서울 구로구", career_required=False, model=MODEL, **extra) -> dict:
    """similarity = 검색어 벡터 [1, 0] 과의 코사인 값이 되도록 벡터를 만든다"""
    vector = [similarity, (1 - similarity ** 2) ** 0.5] if model is not None else None
    return {"matchText": text, "region": region, "careerRequired": career_required,
            "vector": vector, "embeddingModel": model, **extra}


def fake_embed_query(texts):
    return [QUERY_VECTOR for _ in texts]


def fake_reranker(scores: dict[str, float], calls: list | None = None):
    """matchText → 점수. 공용 rerank 처럼 (후보 인덱스, 점수) 를 점수 내림차순으로"""
    def rerank(query, candidates, *, top_k=3):
        if calls is not None:
            calls.append({"query": query, "candidates": list(candidates), "top_k": top_k})
        ranked = sorted(range(len(candidates)), key=lambda i: (-scores.get(candidates[i], 0.0), i))
        return [(i, scores.get(candidates[i], 0.0)) for i in ranked[:top_k]]
    return rerank


class RecommendCase(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.store = IndexStore(Path(self.tmp.name) / "jobs_index.json")

    def tearDown(self):
        self.tmp.cleanup()

    def put(self, entries: dict[int, dict]) -> None:
        self.store.save({str(k): v for k, v in entries.items()})

    def req(self, profile=None, exclude=(), top_k=3) -> RecommendRequest:
        return RecommendRequest.model_validate({
            "studentId": 1, "profile": PROFILE if profile is None else profile,
            "excludeJobPostingIds": list(exclude), "topK": top_k,
        })

    def run_rec(self, scores, profile=None, exclude=(), top_k=3, calls=None, **kw) -> list[dict]:
        data = recommend_jobs(self.req(profile, exclude, top_k), self.store, embed_query=kw.get("embed", fake_embed_query),
                              rerank_fn=kw.get("rerank", fake_reranker(scores, calls)), model_name=MODEL)
        return data.model_dump(by_alias=True)["items"]

    def ids(self, items):
        return [i["jobPostingId"] for i in items]


class FilterTest(RecommendCase):
    def test_exclude_career_and_missing_vector(self):
        self.put({
            1: entry("A"), 2: entry("B"), 3: entry("C", career_required=True),
            4: entry("D", model=None),           # vector 없음
            5: entry("E", model="old-model"),    # 다른 모델로 만든 벡터
        })
        calls = []
        items = self.run_rec({"A": 0.9, "B": 0.8, "C": 0.99, "D": 0.99, "E": 0.99}, exclude=[2], calls=calls)
        self.assertEqual(self.ids(items), [1])
        self.assertEqual(calls[0]["candidates"], ["A"])

    def test_top_k(self):
        self.put({i: entry(f"T{i}") for i in range(1, 6)})
        items = self.run_rec({f"T{i}": 0.5 for i in range(1, 6)}, top_k=2)
        self.assertEqual(self.ids(items), [1, 2])

    def test_candidate_k_by_cosine(self):
        self.put({1: entry("low", 0.1), 2: entry("high", 0.9), 3: entry("mid", 0.5)})
        calls = []
        with patch.object(config, "CANDIDATE_K", 2):
            self.run_rec({"low": 0.9, "high": 0.5, "mid": 0.6}, calls=calls)
        # 코사인 상위 2개만 리랭커로, 리랭커는 후보 전부 점수 (top_k = 후보 수)
        self.assertEqual(calls[0]["candidates"], ["high", "mid"])
        self.assertEqual(calls[0]["top_k"], 2)

    def test_threshold(self):
        self.put({1: entry("A"), 2: entry("B"), 3: entry("C")})
        items = self.run_rec({"A": 0.9, "B": 0.049, "C": 0.05})
        self.assertEqual(self.ids(items), [1, 3])

    def test_same_company_and_title_keeps_best_score(self):
        same = {"companyName": "모의소프트", "title": "백엔드 개발자"}
        self.put({1: entry("A", region="서울 구로구", **same), 2: entry("B", region="서울 강남구", **same), 3: entry("C")})
        items = self.run_rec({"A": 0.3, "B": 0.8, "C": 0.5})
        self.assertEqual(self.ids(items), [2, 3])


class RegionTest(RecommendCase):
    def setUp(self):
        super().setUp()
        self.put({
            1: entry("seoul-low", region="서울 마포구"),
            2: entry("gyeonggi-high", region="경기 성남시"),
            3: entry("incheon-mid", region="인천 연수구"),
        })
        self.scores = {"seoul-low": 0.2, "gyeonggi-high": 0.9, "incheon-mid": 0.6}

    def test_metro_prefers_same_sido_then_score(self):
        with patch.object(config, "REGION_POLICY", "METRO"):
            self.assertEqual(self.ids(self.run_rec(self.scores)), [1, 2, 3])

    def test_strict_only_same_sido(self):
        with patch.object(config, "REGION_POLICY", "STRICT"):
            self.assertEqual(self.ids(self.run_rec(self.scores)), [1])

    def test_strict_without_preferred_region_keeps_all(self):
        with patch.object(config, "REGION_POLICY", "STRICT"):
            items = self.run_rec(self.scores, profile={**PROFILE, "preferredRegion": None})
        self.assertEqual(self.ids(items), [2, 3, 1])


class EmptyReasonTest(RecommendCase):
    def assert_empty(self, reason, **kw):
        with self.assertLogs("ieum.matching", level="INFO") as logs:
            self.assertEqual(self.run_rec(kw.pop("scores", {}), **kw), [])
        self.assertTrue(any(f"reason={reason}" in line for line in logs.output), logs.output)

    def test_index_empty(self):
        self.assert_empty("INDEX_EMPTY")

    def test_profile_empty(self):
        self.put({1: entry("A")})
        self.assert_empty("PROFILE_EMPTY", profile={})

    def test_no_candidates(self):
        self.put({1: entry("A", career_required=True), 2: entry("B")})
        self.assert_empty("NO_CANDIDATES", exclude=[2])

    def test_below_threshold(self):
        self.put({1: entry("A"), 2: entry("B")})
        self.assert_empty("BELOW_THRESHOLD", scores={"A": 0.01, "B": 0.0})


class FallbackTest(RecommendCase):
    def setUp(self):
        super().setUp()
        # 규칙 추천도 고를 수 있게 단어가 겹치는 공고
        self.put({1: entry("[공고] 백엔드 Spring Boot"), 2: entry("[공고] MySQL")})
        self.expected = recommend_from_index(self.req(), self.store).model_dump(by_alias=True)["items"]
        self.assertTrue(self.expected)

    def assert_fallback(self, **kw):
        with self.assertLogs("ieum.matching", level="WARNING") as logs:
            items = self.run_rec({"[공고] 백엔드 Spring Boot": 0.9}, **kw)
        self.assertEqual(items, self.expected)
        self.assertTrue(any("fallback to rules" in line for line in logs.output))

    def test_embedding_failure(self):
        def broken(texts):
            raise embedding.EmbeddingError("키 없음")
        self.assert_fallback(embed=broken)

    def test_reranker_failure(self):
        def broken(query, candidates, *, top_k=3):
            raise reranker.RerankerError("모델 로드 실패")
        self.assert_fallback(rerank=broken)

    def test_score_out_of_range(self):
        self.assert_fallback(rerank=lambda q, c, *, top_k=3: [(0, 3.5), (1, -1.0)])

    def test_no_vectors_in_index(self):
        self.put({1: entry("[공고] 백엔드 Spring Boot", model=None), 2: entry("[공고] MySQL", model=None)})
        self.assert_fallback()

    def test_dimension_mismatch(self):
        self.assert_fallback(embed=lambda texts: [[1.0, 0.0, 0.0] for _ in texts])


class ReasonTest(RecommendCase):
    def test_reasons(self):
        self.put({
            1: entry("[공고] 백엔드 개발자\n[내용] Spring Boot, MySQL"),
            2: entry("[공고] 서버 개발자\n[내용] 대용량 트래픽 처리"),
        })
        items = self.run_rec({"[공고] 백엔드 개발자\n[내용] Spring Boot, MySQL": 0.9, "[공고] 서버 개발자\n[내용] 대용량 트래픽 처리": 0.7})
        self.assertEqual(items[0]["reasons"], [
            {"text": "보유 기술 Spring Boot · MySQL이(가) 공고에 나옵니다",
             "evidence": [{"refType": "PROJECT", "refId": 11}, {"refType": "PROJECT", "refId": 12}]},
            {"text": "희망 직무 '백엔드'와 공고 직무가 맞습니다", "evidence": [{"refType": "JOB_POSTING", "refId": 1}]},
        ])
        # 겹친 단어가 없으면 공고 근거의 일반 문장
        self.assertEqual(items[1]["reasons"], [{"text": GENERIC_REASON, "evidence": [{"refType": "JOB_POSTING", "refId": 2}]}])


class QueryTest(unittest.TestCase):
    def test_build_query(self):
        query = build_query(CompetencyProfile.model_validate(PROFILE))
        self.assertEqual(query, "희망 직무: 백엔드 개발. 보유 기술: Spring Boot, MySQL. 맡은 역할: 백엔드 API 구현. "
                                "성과: REST API 5개 구현. 자격증: 정보처리기사")
        self.assertNotIn("서울", query)
        self.assertEqual(build_query(CompetencyProfile()), "")

    def test_default_query_embedder_uses_retrieval_query(self):
        with patch.object(embedding, "embed_texts", return_value=[[1.0]]) as embed:
            recommend.default_query_embedder()(["검색어"])
        embed.assert_called_once_with(["검색어"], task_type="RETRIEVAL_QUERY")

    def test_default_reranker_is_common_rerank(self):
        self.assertIs(recommend.default_reranker(), reranker.rerank)


class HttpTest(RecommendCase):
    def test_response_shape_and_timing_log(self):
        self.put({7: entry("[공고] 백엔드 Java"), 8: entry("[공고] 데이터"), 9: entry("[공고] 기타")})
        client = TestClient(app)
        with patch.object(router, "store", self.store), \
             patch.object(recommend, "default_query_embedder", return_value=fake_embed_query), \
             patch.object(recommend, "default_reranker", return_value=fake_reranker({"[공고] 백엔드 Java": 0.8, "[공고] 데이터": 0.4})), \
             patch.object(recommend, "default_model_name", return_value=MODEL), \
             self.assertLogs("ieum.matching", level="INFO") as logs:
            res = client.post("/ai/matching/recommend", json={"studentId": 1, "profile": PROFILE, "excludeJobPostingIds": [9], "topK": 3})
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
                self.assertTrue(r["evidence"])
                for ev in r["evidence"]:
                    self.assertEqual(set(ev), {"refType", "refId"})
        self.assertEqual(empty.json(), {"data": {"items": []}})
        done = next(line for line in logs.output if "recommend done" in line)
        for part in ("embed_ms=", "rerank_ms=", "total_ms="):
            self.assertIn(part, done)


if __name__ == "__main__":
    unittest.main()
