# 주인: AI-B
"""공고 색인 테스트. 임베딩은 가짜 함수로 바꿔 실제 모델 · API 를 부르지 않는다. 저장 파일은 임시 폴더에 쓴다.
안전장치: 모듈 전체에서 실제 Gemini 클라이언트(common.embedding._client)를 부르면 바로 실패한다 (setUpModule).

실행: cd ai && .venv/Scripts/python -m unittest matching.test_index -v
"""

import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi.testclient import TestClient

from common import embedding
from main import app
from matching import indexer, router
from matching.indexer import run_index
from matching.models import IndexJob, IndexRequest
from matching.preprocess import MAX_MATCH_CHARS, PREPROCESS_VERSION, career_required, match_text, preprocess
from matching.store import IndexStore

SAMPLE = Path(__file__).parent.parent / "data" / "samples" / "matching_index_request.json"
MODEL = "fake-embedding"


class RealApiCalled(BaseException):
    """실제 Gemini 클라이언트를 부르면 던진다. indexer 는 Exception 만 잡으므로 실패로 삼켜지지 않고 테스트가 깨진다."""


def _no_real_client(*args, **kwargs):
    raise RealApiCalled("테스트에서 실제 Gemini 클라이언트를 불렀어요. 가짜 임베딩을 넘기거나 default_embedder 를 바꾸세요.")


_guards = [patch.object(embedding, "_client", side_effect=_no_real_client), patch("common.llm._client", side_effect=_no_real_client)]


def setUpModule():
    for guard in _guards:
        guard.start()


def tearDownModule():
    for guard in _guards:
        guard.stop()

JOB = {
    "jobPostingId": 1,
    "contentHash": "h1",
    "companyName": "모의회사",
    "title": "백엔드 개발자",
    "jobCategory": "응용 소프트웨어 개발자",
    "location": "서울 구로구",
    "career": "신입",
    "description": "[직무] 응용 소프트웨어 개발자\n[내용] Spring Boot API 개발",
}


def fake_embed(texts):
    """텍스트마다 벡터 1개"""
    return [[float(len(t)), 1.0] for t in texts]


def not_ready_embed(texts):
    raise NotImplementedError("AI-A-01: 임베딩 모듈 구현 전")


class TempStoreCase(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.store = IndexStore(Path(self.tmp.name) / "index" / "jobs_index.json")

    def tearDown(self):
        self.tmp.cleanup()

    def index(self, jobs=(), removed=(), embed=fake_embed, model=MODEL):
        req = IndexRequest.model_validate({"jobs": list(jobs), "removedJobPostingIds": list(removed)})
        return run_index(req, self.store, embed=embed, model_name=model)


class IndexFlowTest(TempStoreCase):
    def test_new_then_same_hash_then_changed_then_removed(self):
        first = self.index([JOB])
        self.assertEqual((first.indexed_count, first.skipped_count, first.failed_count), (1, 0, 0))
        saved = self.store.load()["1"]
        self.assertEqual(saved["contentHash"], "h1")
        self.assertEqual(saved["preprocessVersion"], PREPROCESS_VERSION)
        self.assertEqual(saved["embeddingModel"], MODEL)
        self.assertEqual(len(saved["vector"]), 2)

        again = self.index([JOB])
        self.assertEqual((again.indexed_count, again.skipped_count), (0, 1))

        changed = self.index([{**JOB, "contentHash": "h2"}])
        self.assertEqual((changed.indexed_count, changed.skipped_count), (1, 0))
        self.assertEqual(self.store.load()["1"]["contentHash"], "h2")

        gone = self.index(removed=[1, 999])
        self.assertEqual(gone.removed_count, 1)
        self.assertEqual(self.store.load(), {})

    def test_model_change_reindexes(self):
        self.index([JOB])
        result = self.index([JOB], model="other-model")
        self.assertEqual(result.indexed_count, 1)

    def test_optional_fields_missing_and_unknown_fields_ignored(self):
        job = {k: v for k, v in JOB.items() if k not in ("career", "location")} | {"newFieldFromBe": 1}
        result = run_index(IndexRequest.model_validate({"jobs": [job], "somethingElse": True}), self.store,
                           embed=fake_embed, model_name=MODEL)
        self.assertEqual(result.indexed_count, 1)
        saved = self.store.load()["1"]
        self.assertIsNone(saved["education"])
        self.assertIsNone(saved["region"])

    def test_education_is_saved(self):
        self.index([{**JOB, "education": "학력무관"}])
        self.assertEqual(self.store.load()["1"]["education"], "학력무관")
        self.index([{**JOB, "jobPostingId": 2, "education": None}])
        self.assertIsNone(self.store.load()["2"]["education"])

    def test_removed_ids_missing_or_null(self):
        self.index([JOB])
        for body in ({"jobs": []}, {"jobs": [], "removedJobPostingIds": None}, {"jobs": None, "removedJobPostingIds": []}):
            with self.subTest(body=body):
                result = run_index(IndexRequest.model_validate(body), self.store, embed=fake_embed, model_name=MODEL)
                self.assertEqual((result.removed_count, result.failed_count), (0, 0))
        self.assertIn("1", self.store.load())

    def test_reactivated_job_is_indexed_again(self):
        """BE 는 꺼졌다가 다시 켜진 공고를 같은 contentHash 로 다시 보낸다. 지워진 뒤라 새로 색인한다."""
        self.index([JOB])
        self.index(removed=[1])
        result = self.index([JOB])
        self.assertEqual((result.indexed_count, result.skipped_count), (1, 0))

    def test_one_bad_job_does_not_stop_others(self):
        bad = {"jobPostingId": 2, "title": "contentHash 없음"}
        result = self.index([bad, JOB])
        self.assertEqual((result.indexed_count, result.failed_count, result.failed_job_posting_ids), (1, 1, [2]))

    def test_save_leaves_no_temp_file(self):
        self.index([JOB])
        files = sorted(p.name for p in self.store.path.parent.iterdir())
        self.assertEqual(files, ["jobs_index.json"])


class EmbeddingTest(TempStoreCase):
    def test_count_mismatch_fails_that_job(self):
        def one_vector_for_everything(texts):
            return [[0.1, 0.2], [0.3, 0.4]]  # 텍스트 1개에 벡터 2개

        result = self.index([JOB], embed=one_vector_for_everything)
        self.assertEqual((result.indexed_count, result.failed_count, result.failed_job_posting_ids), (0, 1, [1]))
        # 실패해도 전처리는 vector null 로 저장해 두고 다음 /index 때 다시 시도한다
        saved = self.store.load()["1"]
        self.assertIsNone(saved["vector"])
        self.assertIsNotNone(saved["embedAttemptedAt"])
        self.assertIn("[공고] 백엔드 개발자", saved["matchText"])

    def test_empty_result_fails_that_job(self):
        result = self.index([JOB], embed=lambda texts: [])
        self.assertEqual(result.failed_job_posting_ids, [1])

    def test_batch_is_embedded_in_one_call(self):
        calls = []

        def counting_embed(texts):
            calls.append(list(texts))
            return fake_embed(texts)

        jobs = [{**JOB, "jobPostingId": i, "contentHash": f"h{i}"} for i in (1, 2, 3)]
        result = self.index(jobs, embed=counting_embed)
        self.assertEqual(result.indexed_count, 3)
        self.assertEqual(len(calls), 1)  # 3건을 한 번에 (다시 시도할 공고가 없어 두 번째 호출도 없음)
        self.assertEqual(len(calls[0]), 3)
        saved = self.store.load()
        self.assertEqual([saved[str(i)]["vector"][0] for i in (1, 2, 3)], [float(len(t)) for t in calls[0]])

    def test_batch_and_single_errors_save_null_and_retry_next_time(self):
        def broken(texts):
            raise RuntimeError("boom")

        jobs = [{**JOB, "jobPostingId": 2, "title": "다른 공고"}, JOB]
        result = self.index(jobs, embed=broken)
        self.assertEqual((result.indexed_count, sorted(result.failed_job_posting_ids)), (0, [1, 2]))
        saved = self.store.load()
        self.assertTrue(all(saved[k]["vector"] is None and saved[k]["embedAttemptedAt"] for k in ("1", "2")))

        again = self.index([])
        self.assertEqual(again.retried_count, 2)
        self.assertTrue(all(v["vector"] is not None for v in self.store.load().values()))

    def test_not_implemented_saves_preprocess_with_null_vector(self):
        result = self.index([JOB], embed=not_ready_embed)
        self.assertEqual((result.indexed_count, result.failed_count, result.embedding_status), (1, 0, "NOT_READY"))
        saved = self.store.load()["1"]
        self.assertIsNone(saved["vector"])
        self.assertIsNone(saved["embeddingModel"])
        self.assertIn("[공고] 백엔드 개발자", saved["matchText"])

    def test_missing_module_saves_null_vector(self):
        result = self.index([JOB], embed=None)
        self.assertEqual((result.indexed_count, result.embedding_status), (1, "NOT_READY"))
        self.assertIsNone(self.store.load()["1"]["vector"])

    def test_null_vector_is_retried_when_embedding_ready(self):
        self.index([JOB], embed=not_ready_embed)
        result = self.index([JOB])
        self.assertEqual((result.indexed_count, result.skipped_count, result.embedding_status), (1, 0, "OK"))
        self.assertIsNotNone(self.store.load()["1"]["vector"])

    def test_default_embedder_is_common_embed_texts(self):
        """공용 embed_texts(#36)가 구현돼 색인의 기본 임베딩으로 연결된다. 빈 목록은 API 를 부르지 않고 [] 다."""
        self.assertIs(indexer.default_embedder(), embedding.embed_texts)
        self.assertEqual(embedding.embed_texts([]), [])

    def test_default_embedder_errors_fail_only_that_job(self):
        """공용 embed_texts 가 EmbeddingError(키 · 모델 없음, API 실패)를 던지면 그 공고만 failed, vector null 로 저장"""
        def broken_client():
            raise embedding.EmbeddingError("API 실패 흉내")

        with patch.object(embedding, "_client", side_effect=broken_client), \
             patch.object(embedding, "_model", return_value=MODEL):
            result = run_index(IndexRequest.model_validate({"jobs": [JOB]}), self.store, model_name=MODEL)
        self.assertEqual((result.indexed_count, result.failed_job_posting_ids, result.embedding_status), (0, [1], "OK"))
        self.assertIsNone(self.store.load()["1"]["vector"])


class BatchFallbackTest(TempStoreCase):
    """묶음 임베딩이 실패하면 그 묶음만 1건씩 다시 불러, 실패한 공고만 vector null"""

    def flaky(self, calls, bad="실패공고", batch_error=True):
        """여러 건 묶음은 실패(예외 또는 개수 틀림), 1건 호출은 bad 가 들어간 글만 실패"""
        def embed(texts):
            calls.append(list(texts))
            if len(texts) > 1:
                if batch_error:
                    raise RuntimeError("429 흉내")
                return fake_embed(texts)[:-1]
            if bad in texts[0]:
                raise RuntimeError("이 글만 실패")
            return fake_embed(texts)
        return embed

    def jobs(self, titles):
        return [{**JOB, "jobPostingId": i, "contentHash": f"h{i}", "title": t} for i, t in enumerate(titles, start=1)]

    def test_request_batch_failure_falls_back_to_singles(self):
        for batch_error in (True, False):  # 예외 · 결과 개수 틀림
            with self.subTest(batch_error=batch_error):
                self.store.save({})
                calls = []
                result = self.index(self.jobs(["백엔드", "실패공고", "데이터"]), embed=self.flaky(calls, batch_error=batch_error))
                self.assertEqual([len(c) for c in calls], [3, 1, 1, 1])  # 묶음 1번 + 1건씩 3번
                self.assertEqual((result.indexed_count, result.failed_job_posting_ids), (2, [2]))
                saved = self.store.load()
                self.assertIsNotNone(saved["1"]["vector"])
                self.assertIsNone(saved["2"]["vector"])
                self.assertIsNone(saved["2"]["embeddingModel"])
                self.assertIsNotNone(saved["3"]["vector"])
                self.assertEqual(saved["3"]["embeddingModel"], MODEL)
                self.assertEqual(saved["3"]["vector"], fake_embed([saved["3"]["matchText"]])[0])

    def test_retry_batch_failure_falls_back_to_singles(self):
        self.index(self.jobs(["백엔드", "실패공고", "데이터", "프론트"]), embed=not_ready_embed)
        calls = []
        result = self.index([], embed=self.flaky(calls))
        self.assertEqual([len(c) for c in calls], [4, 1, 1, 1, 1])
        self.assertEqual(result.retried_count, 3)
        saved = self.store.load()
        self.assertEqual([k for k in ("1", "2", "3", "4") if saved[k]["vector"] is None], ["2"])
        self.assertTrue(all(saved[k]["embedAttemptedAt"] for k in ("1", "2", "3", "4")))
        # 다음 호출 때 실패한 공고만 다시 시도
        calls.clear()
        again = self.index([], embed=fake_embed)
        self.assertEqual(again.retried_count, 1)

    def test_single_job_batch_is_not_called_twice(self):
        calls = []
        result = self.index(self.jobs(["실패공고"]), embed=self.flaky(calls))
        self.assertEqual((len(calls), result.failed_job_posting_ids), (1, [1]))

    def test_stops_after_consecutive_single_failures(self):
        calls = []

        def always_fails(texts):
            calls.append(list(texts))
            raise RuntimeError("분당 한도 흉내")

        result = self.index(self.jobs([f"공고{i}" for i in range(6)]), embed=always_fails)
        # 묶음 1번 + 1건씩 SINGLE_FAIL_STOP 번 뒤 멈춤
        self.assertEqual([len(c) for c in calls], [6] + [1] * indexer.SINGLE_FAIL_STOP)
        self.assertEqual(sorted(result.failed_job_posting_ids), [1, 2, 3, 4, 5, 6])
        self.assertTrue(all(v["vector"] is None and v["embedAttemptedAt"] for v in self.store.load().values()))

    def test_streak_resets_on_success(self):
        calls = []
        titles = ["실패공고A", "실패공고B", "백엔드", "실패공고C", "실패공고D", "데이터"]
        result = self.index(self.jobs(titles), embed=self.flaky(calls))
        self.assertEqual(len(calls), 1 + 6)  # 연속 실패가 2번에서 끊겨 끝까지 1건씩 부른다
        self.assertEqual(sorted(result.failed_job_posting_ids), [1, 2, 4, 5])


class RetryTest(TempStoreCase):
    """BE 가 다시 보내지 않는 공고(vector null · 다른 모델)를 /index 가 불릴 때마다 최대 20건씩 다시 임베딩한다."""

    def save_without_vectors(self, ids):
        self.index([{**JOB, "jobPostingId": i} for i in ids], embed=not_ready_embed)
        self.assertTrue(all(self.store.load()[str(i)]["vector"] is None for i in ids))

    def test_null_vectors_filled_on_next_empty_index_call(self):
        self.save_without_vectors([1, 2, 3])
        client = TestClient(app)
        with patch.object(router, "store", self.store), \
             patch.object(indexer, "default_embedder", return_value=fake_embed), \
             patch.object(indexer, "default_model_name", return_value=MODEL):
            res = client.post("/ai/matching/index", json={"removedJobPostingIds": [], "jobs": []})
        self.assertEqual(res.status_code, 200)
        data = res.json()["data"]
        self.assertEqual((data["indexedCount"], data["skippedCount"], data["removedCount"], data["retriedCount"]), (0, 0, 0, 3))
        saved = self.store.load()
        for i in ("1", "2", "3"):
            self.assertIsNotNone(saved[i]["vector"])
            self.assertEqual(saved[i]["embeddingModel"], MODEL)

    def test_retry_runs_after_request_jobs(self):
        self.save_without_vectors([1, 2, 3])
        result = self.index([{**JOB, "jobPostingId": 9}])
        self.assertEqual((result.indexed_count, result.retried_count), (1, 3))

    def test_failed_new_job_is_retried_next_time(self):
        """BE 는 실패한 공고를 다시 보내지 않는다. 저장해 둔 전처리로 다음 호출 때 채운다."""
        first = self.index([JOB], embed=lambda texts: [])
        self.assertEqual(first.failed_job_posting_ids, [1])
        second = self.index([])
        self.assertEqual(second.retried_count, 1)
        self.assertIsNotNone(self.store.load()["1"]["vector"])

    def test_at_most_20_per_call(self):
        self.save_without_vectors(range(1, 26))
        calls = []

        def counting_embed(texts):
            calls.append(texts)
            return fake_embed(texts)

        first = self.index([], embed=counting_embed)
        # 20건을 묶음 1번으로
        self.assertEqual((first.retried_count, len(calls), len(calls[0])), (indexer.RETRY_LIMIT, 1, 20))
        missing = [k for k, v in self.store.load().items() if v["vector"] is None]
        self.assertEqual(sorted(missing, key=int), ["21", "22", "23", "24", "25"])

        second = self.index([], embed=counting_embed)
        self.assertEqual(second.retried_count, 5)
        self.assertEqual(self.index([], embed=counting_embed).retried_count, 0)

    def test_failing_jobs_do_not_block_others(self):
        """계속 실패하는 공고가 있어도 시도한 적 없는 공고가 먼저 차례를 받는다."""
        self.save_without_vectors(range(1, 26))
        self.assertEqual(self.index([], embed=lambda texts: []).retried_count, 0)  # 1~20 시도했지만 실패
        result = self.index([], embed=fake_embed)
        saved = self.store.load()
        self.assertEqual(result.retried_count, 20)
        for i in ("21", "22", "23", "24", "25"):
            self.assertIsNotNone(saved[i]["vector"])

    def test_other_model_is_reembedded(self):
        self.index([JOB], model="old-model")
        result = self.index([], model=MODEL)
        self.assertEqual(result.retried_count, 1)
        self.assertEqual(self.store.load()["1"]["embeddingModel"], MODEL)

    def test_skipped_when_module_missing(self):
        self.save_without_vectors([1, 2, 3])
        result = self.index([], embed=None)
        self.assertEqual((result.retried_count, result.failed_count, result.embedding_status), (0, 0, "NOT_READY"))
        self.assertTrue(all(v["vector"] is None for v in self.store.load().values()))

    def test_skipped_when_not_implemented(self):
        self.save_without_vectors([1, 2, 3])
        calls = []

        def not_ready(texts):
            calls.append(texts)
            raise NotImplementedError

        result = self.index([], embed=not_ready)
        self.assertEqual((result.retried_count, result.failed_count, result.embedding_status), (0, 0, "NOT_READY"))
        self.assertEqual(len(calls), 1)  # 한 번 확인하고 멈춘다
        self.assertTrue(all(v["embedAttemptedAt"] is None for v in self.store.load().values()))


class PreprocessTest(unittest.TestCase):
    def job(self, **kw):
        return IndexJob.model_validate({**JOB, **kw})

    def test_contacts_are_masked_and_html_unescaped(self):
        desc = "[내용] 문의 a.b@test.co.kr 또는 02-123-4567, 010-1234-5678 &amp; Spring"
        text = preprocess(self.job(description=desc, title="개발자 &lt;신입&gt;")).match_text
        self.assertNotIn("test.co.kr", text)
        self.assertNotIn("4567", text)
        self.assertNotIn("5678", text)
        self.assertEqual(text.count("[연락처]"), 3)
        self.assertIn("& Spring", text)
        self.assertIn("개발자 <신입>", text)

    def test_career_required(self):
        self.assertTrue(career_required("경력", "[내용] 개발"))
        self.assertTrue(career_required("관계없음", "[내용] 경력 3년 이상"))
        self.assertTrue(career_required("신입", "[내용] 5년 이상의 실무 경력"))
        self.assertFalse(career_required("신입", "[내용] 경력 3년 이상 우대"))
        self.assertFalse(career_required("관계없음", "[내용] 신입 지원 가능, 경력 3년 이상"))
        self.assertFalse(career_required("신입", "[내용] 개발"))

    def test_career_required_notations(self):
        caught = [
            "경력 : 3년이상", "경력:3년 이상", "경력: 2년 이상", "경력 - 3년 이상", "경력사항 : 3년 이상",
            "경력(3년 이상)", "경력 3년차 이상", "경력 3년↑", "경력 최소 3년", "경력 3~5년", "경력 1~3년",
            "3년 이상 백엔드 개발 경력", "3년 이상 경력",
            # 실제 공고 674번 지원자격 문구
            "지원자격\nㆍ경력 : 3년이상 (연구원급)\n\n우대사항\nㆍ공학계열",
        ]
        for text in caught:
            with self.subTest(text=text):
                self.assertTrue(career_required("관계없음", f"[내용] {text}"))
        not_caught = ["경력 3년 이상 우대", "3년 이상 경력자 우대", "경력 0~2년", "신입·경력 모두 가능",
                      "신입·경력 모두 가능, 경력 3년 이상"]
        for text in not_caught:
            with self.subTest(text=text):
                self.assertFalse(career_required("관계없음", f"[내용] {text}"))

    def test_sections_removed_and_reordered(self):
        desc = ("[직무] 웹 개발자\n[내용] 웹 서비스 개발\n[필요 역량] 문서작성 (워드프로세스 활용),표계산 (스프레드시트 활용)\n"
                "[자격증] 정보처리기사\n[우대 조건] AWS 경험\n[근무 시간] 평일 9시\n[전형] 서류 면접")
        text = match_text("웹 개발자 모집", "서울 구로구", desc)
        self.assertEqual(text.splitlines(), [
            "[공고] 웹 개발자 모집", "[지역] 서울 구로구",
            "[직무] 웹 개발자", "[자격증] 정보처리기사", "[우대 조건] AWS 경험", "[내용] 웹 서비스 개발",
        ])

    def test_real_competency_is_kept_first(self):
        desc = "[내용] 개발\n[필요 역량] Java, 문서작성"
        lines = match_text("t", None, desc).splitlines()
        self.assertEqual(lines[1:], ["[필요 역량] Java, 문서작성", "[내용] 개발"])

    def test_region_and_tech_unified(self):
        pre = preprocess(self.job(location="경기도 성남시", description="[내용] JAVA, 자바스크립트, 파이썬, mysql, 리눅스마스터, 피그마"))
        self.assertEqual(pre.region, "경기 성남시")
        self.assertIn("[지역] 경기 성남시", pre.match_text)
        self.assertIn("Java, 자바스크립트, Python, MySQL, 리눅스마스터, Figma", pre.match_text)
        self.assertEqual(preprocess(self.job(location="서울특별시 구로구")).region, "서울 구로구")

    def test_long_text_is_cut(self):
        pre = preprocess(self.job(description="[내용] " + "가" * 2000))
        self.assertEqual(len(pre.match_text), MAX_MATCH_CHARS)


class RouterTest(TempStoreCase):
    def test_be_sample_request(self):
        body = json.loads(SAMPLE.read_text(encoding="utf-8"))
        client = TestClient(app)
        with patch.object(router, "store", self.store), \
             patch.object(indexer, "default_embedder", return_value=fake_embed), \
             patch.object(indexer, "default_model_name", return_value=MODEL):
            first = client.post("/ai/matching/index", json=body)
            again = client.post("/ai/matching/index", json=body)

        self.assertEqual(first.status_code, 200)
        self.assertEqual(first.json(), {"data": {
            "indexedCount": 3, "skippedCount": 0, "removedCount": 0, "failedCount": 0,
            "failedJobPostingIds": [], "retriedCount": 0, "embeddingStatus": "OK",
        }})
        self.assertEqual(again.json()["data"]["skippedCount"], 3)

        saved = self.store.load()
        self.assertEqual(sorted(saved), ["9601", "9602", "9603"])
        backend = saved["9601"]
        self.assertNotIn("example.com", backend["matchText"])
        self.assertNotIn("문서작성", backend["matchText"])
        self.assertNotIn("[근무 시간]", backend["matchText"])
        self.assertIn("[공고] Java 백엔드 개발자 (신입) <정규직>", backend["matchText"])  # 제목도 기술 표기 통일
        self.assertIn("Java, Spring Boot, MySQL", backend["matchText"])
        self.assertEqual(backend["region"], "서울 구로구")
        self.assertFalse(backend["careerRequired"])
        self.assertEqual((saved["9602"]["region"], saved["9602"]["careerRequired"]), ("경기 성남시", True))
        self.assertTrue(saved["9603"]["careerRequired"])
        self.assertEqual([saved[k]["education"] for k in ["9601", "9602", "9603"]], ["대졸(4년)", "학력무관", None])
        # 추천에서 같은 공고를 합칠 때 쓰는 회사명 · 제목 (HTML 문자만 되돌림)
        self.assertEqual((backend["companyName"], backend["title"]), ("모의소프트 & 랩", "자바 백엔드 개발자 (신입) <정규직>"))


# BE JobSyncService 와 같은 값
BE_BATCH_SIZE = 20
BE_READ_KEYS = ("indexedCount", "skippedCount", "removedCount")


def be_job(job_id: int, content_hash: str | None = None) -> dict:
    """BE indexRequest 가 만드는 공고 한 건 (키 9개, 모의 데이터)"""
    return {
        "jobPostingId": job_id,
        "contentHash": content_hash or f"{job_id:064x}",
        "companyName": f"모의회사{job_id}",
        "title": f"백엔드 개발자 {job_id}",
        "jobCategory": "응용 소프트웨어 개발자",
        "location": "서울 구로구",
        "career": "신입",
        "education": "학력무관" if job_id % 2 else None,
        "description": f"[직무] 응용 소프트웨어 개발자\n[내용] Spring Boot API 개발 {job_id}\n[전형] 서류 면접",
    }


def be_batches(jobs: list[dict], removed_ids: list[int]) -> list[dict]:
    """BE indexInBatches 와 같은 방식: 20건씩, removedJobPostingIds 는 첫 묶음에만 (나머지는 [])"""
    count = max(1, -(-len(jobs) // BE_BATCH_SIZE))
    return [
        {"removedJobPostingIds": removed_ids if b == 0 else [], "jobs": jobs[b * BE_BATCH_SIZE:(b + 1) * BE_BATCH_SIZE]}
        for b in range(count)
    ]


class BeBatchTest(TempStoreCase):
    """BE 가 보내는 그대로의 묶음 요청을 /ai/matching/index 로 보내고, BE 처럼 세 키만 읽어 더한다."""

    def post_all(self, bodies: list[dict]) -> list[dict]:
        client = TestClient(app)
        with patch.object(router, "store", self.store), \
             patch.object(indexer, "default_embedder", return_value=fake_embed), \
             patch.object(indexer, "default_model_name", return_value=MODEL):
            responses = [client.post("/ai/matching/index", json=body) for body in bodies]
        for res in responses:
            self.assertEqual(res.status_code, 200)
        return [res.json()["data"] for res in responses]

    def test_batches_with_removed_ids_only_in_first(self):
        # 지난 배치에서 색인된 공고 5개 (9001 ~ 9005)
        self.index([be_job(i) for i in range(9001, 9006)])

        # 이번 배치: 새 공고 44건 + 다시 켜진 9001(내용 같음) = 45건 → 20 · 20 · 5 세 묶음
        # 꺼진 공고 9002 · 9003 과 색인에 없던 7777 은 첫 묶음에만
        jobs = [be_job(i) for i in range(100, 144)] + [be_job(9001)]
        bodies = be_batches(jobs, [9002, 9003, 7777])
        self.assertEqual([len(b["jobs"]) for b in bodies], [20, 20, 5])
        self.assertEqual([b["removedJobPostingIds"] for b in bodies], [[9002, 9003, 7777], [], []])
        del bodies[2]["removedJobPostingIds"]  # 키가 아예 없어도 된다

        data = self.post_all(bodies)

        for d in data:
            for key in BE_READ_KEYS:
                self.assertIsInstance(d[key], int)
        self.assertEqual([d["removedCount"] for d in data], [2, 0, 0])
        total = {key: sum(d.get(key, 0) for d in data) for key in BE_READ_KEYS}
        self.assertEqual(total, {"indexedCount": 44, "skippedCount": 1, "removedCount": 2})
        self.assertEqual(sum(d["failedCount"] for d in data), 0)

        saved = self.store.load()
        self.assertEqual(len(saved), 5 - 2 + 44)
        self.assertNotIn("9002", saved)
        self.assertNotIn("9003", saved)
        self.assertEqual(saved["101"]["education"], "학력무관")
        self.assertIsNone(saved["100"]["education"])

    def test_removed_only_request(self):
        """바뀐 공고는 없고 꺼진 공고만 있을 때 BE 는 jobs: [] 인 요청 한 번을 보낸다."""
        self.index([be_job(9001)])
        bodies = be_batches([], [9001])
        self.assertEqual(bodies, [{"removedJobPostingIds": [9001], "jobs": []}])
        data = self.post_all(bodies)
        self.assertEqual({k: data[0][k] for k in BE_READ_KEYS}, {"indexedCount": 0, "skippedCount": 0, "removedCount": 1})
        self.assertEqual(self.store.load(), {})


if __name__ == "__main__":
    unittest.main()
