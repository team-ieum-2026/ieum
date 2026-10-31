# 주인: AI-A (작성 AI-B)
"""score_matching.py 테스트. 정답표 · 실행 결과는 모두 가짜로 임시 폴더에 만든다 (실제 gold 파일은 쓰지 않음).

실행: cd ai && .venv/Scripts/python -m unittest eval.test_score_matching -v
"""

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))  # score_matching.py 는 같은 폴더의 rubric 을 바로 import 한다

import score_matching  # noqa: E402

STUDENT = {"desiredJob": "백엔드 개발", "preferredRegion": "서울",
           "education": [{"degree": "ASSOCIATE", "status": "GRADUATED"}], "careers": 0}


def card(jid: int, rank: int, *, sido: str = "서울", education: str = "학력무관", career: str = "신입") -> dict:
    return {"jobPostingId": jid, "rank": rank, "title": f"공고{jid}", "sido": sido, "education": education,
            "career": career, "sourceFile": "gold", "reasons": []}


class ScoreTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = Path(tempfile.mkdtemp())
        self.runs = self.tmp / "runs"
        self.runs.mkdir()
        self.write_run(1, [card(101, 1), card(102, 2, sido="경기"), card(103, 3, education="대졸(4년)")])
        self.write_run(2, [card(201, 1), card(299, 2)])  # 299 는 정답표에 없음
        self.write_run(10, [card(301, 1)])  # 맞는 공고가 없어야 하는 수료생
        self.labels = self.tmp / "labels.csv"
        self.labels.write_text(
            "﻿student_id,job_posting_id,score\n1,101,2\n1,102,1\n1,103,0\n2,201,2\n10,301,1\n9,101,0\n",
            encoding="utf-8",
        )

    def write_run(self, sid: int, items: list[dict]) -> None:
        run = {"studentId": sid, "student": STUDENT, "path": "OK", "items": items}
        (self.runs / f"student_{sid}.json").write_text(json.dumps(run, ensure_ascii=False), encoding="utf-8")

    def run_score(self, split: str, members: list[int], human: str | None = None) -> tuple[dict, str]:
        human_path = None
        if human is not None:
            human_path = self.tmp / "human.csv"
            human_path.write_text("student_id,job_posting_id,score\n" + human, encoding="utf-8")
        lines: list[str] = []
        result = score_matching.score(self.runs, self.labels, split, members=members, out=lines.append,
                                      human_labels_path=human_path)
        return result, "\n".join(lines)

    def test_unscored_cards_are_left_out_of_denominator(self) -> None:
        result, printed = self.run_score("dev", [1, 2])
        self.assertEqual(result["mode"], "FIRST")
        self.assertIn("1차 수치", printed)
        self.assertEqual((result["cards"], result["scored"], result["unscored"]), (5, 4, 1))
        self.assertEqual((result["score2"], result["score1"], result["score0"]), (2, 1, 1))

    def test_human_labels_fill_unscored_cards_into_denominator(self) -> None:
        result, printed = self.run_score("dev", [1, 2], human="2,299,2\n")
        self.assertEqual(result["mode"], "FINAL")
        self.assertIn("최종 수치", printed)
        self.assertEqual((result["scored"], result["unscored"], result["humanScored"]), (5, 0, 1))
        self.assertIn("3/5 = 60%", printed)  # Precision@3: 사람 판정 2점 카드가 분모 · 분자에 들어감
        self.assertIn("[2·사람] 299", printed)

    def test_gold_label_wins_over_human_label(self) -> None:
        result, _ = self.run_score("dev", [1, 2], human="1,101,0\n2,299,1\n")
        self.assertEqual((result["score2"], result["score1"], result["score0"]), (2, 2, 1))  # 1,101 은 정답표 2점
        self.assertEqual(result["humanScored"], 1)

    def test_dev_prints_cards_and_condition_flags(self) -> None:
        _, printed = self.run_score("dev", [1, 2])
        self.assertIn("2/4 = 50%", printed)  # Precision@3
        self.assertIn("1/4 = 25%", printed)  # 0점 비율
        self.assertIn("[1] 102 공고102 · 경기", printed)
        self.assertIn("지역(시·도)", printed)
        self.assertIn("학력", printed)  # 전문학사 수료생 × 대졸(4년) 공고
        self.assertIn("채점 불가", printed)

    def test_test_prints_only_totals(self) -> None:
        result, printed = self.run_score("test", [2, 10])
        self.assertFalse(result["noMatchPassed"][10])  # 10번 카드가 1점이라 실패
        self.assertIn("맞는 공고 없는 수료생 통과 0/1명", printed)
        self.assertNotIn("10번", printed)  # test 는 통과 수만
        self.assertNotIn("공고2", printed)
        self.assertNotIn("[2]", printed)
        self.assertNotIn("201", printed)

    def test_test_with_human_labels_still_prints_only_totals(self) -> None:
        result, printed = self.run_score("test", [2, 10], human="2,299,0\n")
        self.assertEqual(result["mode"], "FINAL")
        self.assertNotIn("299", printed)
        self.assertNotIn("·사람", printed)

    def test_no_match_student_passes_without_cards(self) -> None:
        self.write_run(10, [])
        result, _ = self.run_score("test", [10])
        self.assertTrue(result["noMatchPassed"][10])

    def test_no_match_student_passes_when_every_card_is_2(self) -> None:
        self.labels.write_text("student_id,job_posting_id,score\n10,301,2\n", encoding="utf-8")
        result, printed = self.run_score("test", [10])
        self.assertTrue(result["noMatchPassed"][10])  # METRICS.md: 2점이 아닌 카드가 나와야 실패
        self.assertIn("통과 1/1명", printed)

    def test_no_match_17_with_partial_card_fails(self) -> None:
        self.write_run(10, [])
        self.write_run(17, [card(701, 1), card(702, 2)])  # 701 은 부분 적합(1점)
        self.labels.write_text("student_id,job_posting_id,score\n17,701,1\n17,702,2\n", encoding="utf-8")
        result, printed = self.run_score("test", [10, 17])
        self.assertEqual(result["noMatchPassed"], {10: True, 17: False})
        self.assertIn("맞는 공고 없는 수료생 통과 1/2명", printed)
        self.assertNotIn("17번", printed)

    def test_dev_shows_no_match_student_13(self) -> None:
        self.write_run(13, [])
        result, printed = self.run_score("dev", [1, 13])
        self.assertEqual(result["noMatchPassed"], {13: True})
        self.assertIn("맞는 공고 없는 수료생 통과 1/1명", printed)
        self.assertIn("13번: 통과 (카드 0장", printed)

    def test_no_match_student_fails_on_unscored_card(self) -> None:
        self.write_run(10, [card(301, 1), card(399, 2)])  # 399 는 점수를 모름 → 2점으로 확인 안 됨
        self.labels.write_text("student_id,job_posting_id,score\n10,301,2\n", encoding="utf-8")
        result, _ = self.run_score("test", [10])
        self.assertFalse(result["noMatchPassed"][10])
        result, _ = self.run_score("test", [10], human="10,399,2\n")
        self.assertTrue(result["noMatchPassed"][10])

    def test_labels_of_other_students_are_not_loaded(self) -> None:
        labels = score_matching.load_labels(self.labels, {1})
        self.assertEqual(set(labels), {(1, 101), (1, 102), (1, 103)})


if __name__ == "__main__":
    unittest.main()
