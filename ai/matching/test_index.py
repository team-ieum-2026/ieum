# 주인: AI-B
"""공고 색인 테스트. 임베딩은 가짜 함수로 바꿔 실제 모델 · API 를 부르지 않는다. 저장 파일은 임시 폴더에 쓴다.

실행: cd ai && .venv/Scripts/python -m unittest matching.test_index -v
"""

import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi.testclient import TestClient

from main import app
from matching import indexer, router
from matching.indexer import run_index
from matching.models import IndexJob, IndexRequest
from matching.preprocess import MAX_MATCH_CHARS, PREPROCESS_VERSION, career_required, match_text, preprocess
from matching.store import IndexStore

SAMPLE = Path(__file__).parent.parent / "data" / "samples" / "matching_index_request.json"
MODEL = "fake-embedding"

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
        self.assertIsNone(saved["minEducation"])
        self.assertIsNone(saved["region"])

    def test_min_education_is_saved(self):
        self.index([{**JOB, "minEducation": "학력무관"}])
        self.assertEqual(self.store.load()["1"]["minEducation"], "학력무관")

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
        self.assertEqual(self.store.load(), {})

    def test_empty_result_fails_that_job(self):
        result = self.index([JOB], embed=lambda texts: [])
        self.assertEqual(result.failed_job_posting_ids, [1])

    def test_embedding_error_fails_only_that_job(self):
        def flaky(texts):
            if "실패" in texts[0]:
                raise RuntimeError("boom")
            return fake_embed(texts)

        result = self.index([{**JOB, "jobPostingId": 2, "title": "실패 공고"}, JOB], embed=flaky)
        self.assertEqual((result.indexed_count, result.failed_job_posting_ids), (1, [2]))

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

    def test_real_common_embedding_is_not_ready(self):
        """지금 common.embedding.embed_texts 는 NotImplementedError → 서버가 멈추지 않고 NOT_READY"""
        result = run_index(IndexRequest.model_validate({"jobs": [JOB]}), self.store)
        self.assertEqual((result.indexed_count, result.embedding_status), (1, "NOT_READY"))


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
            "failedJobPostingIds": [], "embeddingStatus": "OK",
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


if __name__ == "__main__":
    unittest.main()
