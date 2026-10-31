# 주인: AI-B
"""매칭 평가 실행(eval_run) 테스트. 임베딩 · 리랭커 · LLM 은 가짜로 바꾸고 파일은 임시 폴더에 쓴다.

실행: cd ai && .venv/Scripts/python -m unittest matching.test_eval_run -v
"""

import contextlib
import hashlib
import io
import json
import tempfile
import unittest
from pathlib import Path

from matching import eval_run
from matching.store import DEFAULT_PATH, IndexStore

PROFILE = {
    "roles": ["백엔드 API 구현"],
    "skills": [{"name": "Java", "evidence": [{"refType": "PROJECT", "refId": 11}]},
               {"name": "Spring Boot", "evidence": [{"refType": "PROJECT", "refId": 11}]}],
    "achievements": [],
    "certificates": [],
    "desiredJob": "백엔드 개발",
    "preferredRegion": "서울",
}
MATERIAL = {"studentId": 1, "education": [{"degree": "BACHELOR", "status": "GRADUATED"}], "careers": []}
POSTINGS = {
    900201: {"title": "백엔드 개발자", "companyName": "(주)가", "career": "신입", "education": "대졸(4년)",
             "location": "서울특별시 성동구", "sourceFile": "gold"},
    900202: {"title": "자바 개발자", "companyName": "(주)나", "career": "관계없음", "education": "학력무관",
             "location": "경기 성남시", "sourceFile": "gold"},
}


def entry(text: str, region: str) -> dict:
    return {"contentHash": "h", "embeddingModel": "fake", "matchText": text, "careerRequired": False,
            "region": region, "education": "학력무관", "vector": [1.0, 0.0]}


class EvalRunTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = Path(tempfile.mkdtemp())
        self.index_path = self.tmp / "index.json"
        IndexStore(self.index_path).save({
            "900201": {**entry("[공고] 백엔드 개발자 Java Spring Boot", "서울 성동구"), "companyName": "(주)가", "title": "백엔드 개발자"},
            "900202": {**entry("[공고] 자바 개발자 Java", "경기 성남시"), "companyName": "(주)나", "title": "자바 개발자"},
        })
        profiles = self.tmp / "profiles"
        profiles.mkdir()
        for sid in (1, 4):
            (profiles / f"student_{sid}.json").write_text(json.dumps({"studentId": sid, "profile": PROFILE}), encoding="utf-8")
        self.profiles = profiles

    def run_split(self, split: str, students: list[int]) -> tuple[Path, str]:
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            out_dir = eval_run.cmd_run(
                split, "gold91", run_date="2026-10-31", students=students, index_path=self.index_path,
                profiles_dir=self.profiles, runs_dir=self.tmp / "runs", postings=POSTINGS,
                materials={1: MATERIAL, 4: {**MATERIAL, "studentId": 4}},
                embed_query=lambda texts: [[1.0, 0.0]],
                rerank_fn=lambda query, cands, top_k: [(i, 0.9 - i * 0.1) for i in range(len(cands))],
                model_name="fake", warmup=lambda: None,
            )
        return out_dir, out.getvalue()

    def test_demo_index_is_refused(self) -> None:
        with self.assertRaises(SystemExit):
            eval_run.ensure_not_demo(DEFAULT_PATH)
        with self.assertRaises(SystemExit):
            eval_run.cmd_run("dev", "gold91", students=[1], index_path=DEFAULT_PATH, warmup=None)

    def test_index_jobs_recomputes_content_hash(self) -> None:
        jobs = eval_run.index_jobs({7: {"description": "본문", "contentHash": "wrong", "title": "t"}})
        self.assertEqual(jobs[0]["jobPostingId"], 7)
        self.assertEqual(jobs[0]["contentHash"], hashlib.sha256("본문".encode("utf-8")).hexdigest())

    def test_dev_run_saves_cards_with_posting_conditions(self) -> None:
        out_dir, printed = self.run_split("dev", [1])
        result = json.loads((out_dir / "student_1.json").read_text(encoding="utf-8"))
        self.assertEqual(result["path"], "OK")
        self.assertEqual([c["jobPostingId"] for c in result["items"]], [900201, 900202])
        first = result["items"][0]
        self.assertEqual((first["education"], first["sido"], first["career"]), ("대졸(4년)", "서울", "신입"))
        self.assertEqual(result["items"][1]["sido"], "경기")
        self.assertAlmostEqual(first["rerankScore"], 0.9)
        self.assertEqual(result["student"]["education"][0]["degree"], "BACHELOR")
        self.assertIsNotNone(result["timing"]["totalMs"])
        summary = json.loads((out_dir / "_run_dev.json").read_text(encoding="utf-8"))
        self.assertEqual(summary["students"], 1)
        self.assertEqual(summary["paths"]["OK"], 1)
        self.assertIn("900201", printed)  # dev 는 카드까지 출력

    def test_test_split_prints_counts_only(self) -> None:
        out_dir, printed = self.run_split("test", [4])
        self.assertTrue((out_dir / "student_4.json").exists())
        self.assertNotIn("9002", printed)
        self.assertNotIn("백엔드 개발자", printed)
        self.assertIn("1명 저장", printed)

    def test_parse_logs(self) -> None:
        parsed = eval_run.parse_logs([
            "recommend done studentId=1 usable=2 candidates=2 reranked=2 passed=2 picked=2 "
            "embed_ms=12 rerank_ms=34 total_ms=50 top=[(900201, 0.9), (900202, 0.8)]",
        ])
        self.assertEqual((parsed["path"], parsed["embedMs"], parsed["rerankMs"]), ("OK", 12, 34))
        self.assertEqual(parsed["scores"], {"900201": 0.9, "900202": 0.8})
        empty = eval_run.parse_logs(["recommend empty studentId=13 reason=NO_CANDIDATES usable=91"])
        self.assertEqual((empty["path"], empty["emptyReason"]), ("EMPTY", "NO_CANDIDATES"))
        fallback = eval_run.parse_logs(["recommend fallback to rules studentId=1 reason=rerank failed: X"])
        self.assertEqual(fallback["path"], "FALLBACK")


if __name__ == "__main__":
    unittest.main()
