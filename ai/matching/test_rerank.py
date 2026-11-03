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
        # 임베딩 유사도도 리랭커 점수와 같은 순서로 둔다 (지역 규칙만 보려고 · 정렬은 두 순위를 합친다)
        self.put({
            1: entry("seoul-low", similarity=0.2, region="서울 마포구"),
            2: entry("gyeonggi-high", similarity=0.9, region="경기 성남시"),
            3: entry("incheon-mid", similarity=0.6, region="인천 연수구"),
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


class RegionPolicyTest(RecommendCase):
    """희망 지역 여러 개 · 수도권 밖 (METRO)"""

    def setUp(self):
        super().setUp()
        self.put({  # 유사도도 리랭커 점수와 같은 순서 (지역 규칙만 본다)
            1: entry("seoul", similarity=0.2, region="서울 마포구"),
            2: entry("gyeonggi", similarity=0.9, region="경기 성남시"),
            3: entry("incheon", similarity=0.6, region="인천 연수구"),
            4: entry("busan", similarity=0.95, region="부산 해운대구"),
        })
        self.scores = {"seoul": 0.2, "gyeonggi": 0.9, "incheon": 0.6, "busan": 0.95}
        self.policy = patch.object(config, "REGION_POLICY", "METRO")
        self.policy.start()

    def tearDown(self):
        self.policy.stop()
        super().tearDown()

    def rec(self, region, top_k=4, calls=None):
        return self.run_rec(self.scores, profile={**PROFILE, "preferredRegion": region}, top_k=top_k, calls=calls)

    def test_two_preferred_regions_are_both_first(self):
        # 서울 · 경기 둘 다 희망 지역 → 둘이 먼저(점수 순), 그다음 인천. 부산은 후보 아님
        self.assertEqual(self.ids(self.rec("서울, 경기")), [2, 1, 3])
        self.assertEqual(self.ids(self.rec("서울특별시,경기도")), [2, 1, 3])

    def test_capital_preference_widens_to_capital_area_only(self):
        calls = []
        self.assertEqual(self.ids(self.rec("서울", calls=calls)), [1, 2, 3])
        self.assertEqual(sorted(calls[0]["candidates"]), ["gyeonggi", "incheon", "seoul"])

    def test_outside_capital_keeps_only_that_sido(self):
        self.assertEqual(self.ids(self.rec("부산")), [4])

    def test_outside_capital_without_jobs_is_no_candidates(self):
        self.store.save({k: v for k, v in self.store.load().items() if k != "4"})
        with self.assertLogs("ieum.matching", level="INFO") as logs:
            self.assertEqual(self.rec("부산"), [])
        self.assertTrue(any("reason=NO_CANDIDATES" in line for line in logs.output), logs.output)

    def test_no_preferred_region_keeps_all(self):
        for region in (None, "", "강남구"):
            with self.subTest(region=region):
                self.assertEqual(self.ids(self.rec(region)), [4, 2, 3, 1])

    def test_fallback_uses_same_region_rule(self):
        self.put({1: entry("[공고] 백엔드", region="서울 마포구"), 2: entry("[공고] 백엔드", region="부산 해운대구"),
                  3: entry("[공고] 백엔드", region="경기 성남시")})

        def fallback(region):
            req = self.req(profile={**PROFILE, "preferredRegion": region})
            return self.ids(recommend_from_index(req, self.store).model_dump(by_alias=True)["items"])

        self.assertEqual(fallback("서울"), [1, 3])
        self.assertEqual(fallback("부산"), [2])
        self.assertEqual(fallback("경기, 서울"), [1, 3])
        self.assertEqual(fallback(None), [1, 2, 3])


class RrfOrderTest(RecommendCase):
    """Top-3 정렬 = 1/(60 + 리랭커 순위) + 1/(60 + 임베딩 순위) 내림차순. 순위는 상위 K 안에서 1부터"""

    def test_rrf_order_differs_from_rerank_only(self):
        # 임베딩 순위 A1 · B2 · C3, 리랭커 순위 B1 · C2 · A3
        # → A 1/63+1/61 · B 1/61+1/62 · C 1/62+1/63 → B · A · C (리랭커 점수만이면 B · C · A)
        self.put({1: entry("A", 0.9), 2: entry("B", 0.8), 3: entry("C", 0.7)})
        self.assertEqual(self.ids(self.run_rec({"A": 0.3, "B": 0.9, "C": 0.5})), [2, 1, 3])

    def test_tie_goes_to_better_embedding_rank(self):
        # X: 임베딩 1위 · 리랭커 2위, Y: 임베딩 2위 · 리랭커 1위 → 합친 점수가 같음 → 임베딩 순위가 앞선 X 먼저
        self.put({1: entry("Y", 0.8), 2: entry("X", 0.9)})
        self.assertEqual(self.ids(self.run_rec({"X": 0.4, "Y": 0.9})), [2, 1])

    def test_below_threshold_is_excluded_but_still_ranked(self):
        # D 는 임베딩 1위지만 리랭커 0.01 이라 빠진다. 순위는 D 를 포함해 매긴다 (A 임베딩 2위 · 리랭커 2위, B 3위 · 1위)
        # → B 1/61+1/63 (0.032266) > A 1/62+1/62 (0.032258). D 를 빼고 매기면 A · B 가 동점이라 A 가 먼저였을 것
        self.put({1: entry("D", 0.95), 2: entry("A", 0.9), 3: entry("B", 0.8)})
        items = self.run_rec({"D": 0.01, "A": 0.5, "B": 0.6})
        self.assertEqual(self.ids(items), [3, 2])

    def test_one_candidate(self):
        self.put({1: entry("A", 0.5)})
        self.assertEqual(self.ids(self.run_rec({"A": 0.3})), [1])

    def test_two_candidates(self):
        self.put({1: entry("A", 0.9), 2: entry("B", 0.5)})
        self.assertEqual(self.ids(self.run_rec({"A": 0.2, "B": 0.95})), [1, 2])  # 동점(1/61+1/62) → 임베딩 1위 A

    def test_region_and_missing_skill_still_come_first(self):
        # 희망 시 · 도 밖(경기)은 합친 점수가 높아도 뒤로
        self.put({1: entry("far", 0.95, region="경기 성남시"), 2: entry("near", 0.5, region="서울 구로구")})
        self.assertEqual(self.ids(self.run_rec({"far": 0.95, "near": 0.3})), [2, 1])


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

    def test_skill_without_evidence_reason_keeps_cards(self):
        # 근거 없는 기술이 있어도 카드 순서는 같고, 이유만 근거 있는 기술 → 근거 없는 기술 → 희망 직무 순
        text_1, text_2 = "[공고] 백엔드 개발자\n[내용] MySQL, Python", "[공고] 백엔드 개발자\n[내용] Spring Boot"
        self.put({1: entry(text_1), 2: entry(text_2)})
        scores = {text_1: 0.6, text_2: 0.9}
        before = self.run_rec(scores)
        profile = {**PROFILE, "skills": [*PROFILE["skills"], {"name": "Python", "evidence": []}]}
        items = self.run_rec(scores, profile=profile)
        self.assertEqual(self.ids(items), self.ids(before))
        reasons = next(i for i in items if i["jobPostingId"] == 1)["reasons"]
        self.assertEqual([r["text"] for r in reasons], [
            "보유 기술 MySQL이(가) 공고에 나옵니다",
            "프로필의 기술 Python이(가) 공고에도 나옵니다",
            "희망 직무 '백엔드'와 공고 직무가 맞습니다",
        ])
        self.assertEqual(reasons[1]["evidence"], [{"refType": "JOB_POSTING", "refId": 1}])
        self.assertTrue(all(r["evidence"] for i in items for r in i["reasons"]))


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
