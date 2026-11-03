# 주인: AI-B
"""칸 필터 기준선 · 함정 진입률 테스트. 가짜 정답표 · 가짜 실행 결과만 쓴다 (실제 정답표를 읽지 않음).

실행: cd ai && .venv/Scripts/python -m unittest matching.test_extra_metrics -v
"""

import json
import sys
import tempfile
import unittest
from pathlib import Path

from matching import extra_metrics as em

HEADER = "student_id,job_posting_id,score,violations,origin,target_label,trap_type\n"
# 수료생 1: 칸 통과 쌍 4개 중 2점 2개 (50%) · 수료생 2: 칸 통과 1개 중 2점 1개 (100%) · 수료생 9: 맞는 공고 없음
ROWS = [
    "1,101,2,,TARGET,GOLD,",
    "1,102,1,SKILL,TARGET,TRAP,SKILL",
    "1,103,0,JOB,TARGET,TRAP,LOOKALIKE",
    "1,104,1,CAREER,TARGET,TRAP,CAREER",
    "1,105,2,,RULE,GOLD,",
    "1,106,1,SKILL,REAL,,",
    "2,101,0,JOB|SKILL,RULE,GOLD,",
    "2,201,2,,TARGET,GOLD,",
    "2,202,0,REGION,TARGET,TRAP,REGION",
    "9,101,0,REGION,RULE,GOLD,",
    "7,101,2,,TARGET,GOLD,",  # 명단에 없는 수료생 (test 쪽 역할) — dev 계산에서 읽히면 안 됨
]
SPLIT = {"dev": [1, 2, 9], "test": [7]}
NO_MATCH = {"dev": [9], "test": []}


class ExtraMetricsTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        self.labels_path = self.dir / "pair_labels.csv"
        self.labels_path.write_text(HEADER + "\n".join(ROWS) + "\n", encoding="utf-8")
        self.runs = self.dir / "runs"
        self.runs.mkdir()
        cards = {1: [101, 102, 105], 2: [201], 9: []}
        for sid, ids in cards.items():
            (self.runs / f"student_{sid}.json").write_text(json.dumps({"items": [{"jobPostingId": j} for j in ids]}),
                                                         encoding="utf-8")

    def labels(self, students=(1, 2, 9)):
        return em.load_labels(self.labels_path, set(students))

    def test_only_listed_students_are_read(self):
        self.assertNotIn((7, 101), self.labels())
        self.assertEqual(self.labels()[(2, 101)].violations, frozenset({"JOB", "SKILL"}))

    def test_field_baseline_pair_and_student_mean(self):
        b = em.field_baseline(self.labels(), [1, 2])
        self.assertEqual(b["per_student"], {1: (2, 4), 2: (1, 1)})  # 1: 101 · 102 · 105 · 106 (SKILL 위반은 허용), 103 · 104 는 칸 위반
        self.assertAlmostEqual(b["pair"], 3 / 5)
        self.assertAlmostEqual(b["student"], (2 / 4 + 1) / 2)

    def test_field_baseline_without_skill(self):
        b = em.field_baseline(self.labels(), [1, 2], allow_skill=False)
        self.assertEqual(b["per_student"], {1: (2, 2), 2: (1, 1)})

    def test_field_baseline_pool(self):
        b = em.field_baseline(self.labels(), [1, 2], pool={101, 102})
        self.assertEqual(b["per_student"], {1: (1, 2)})  # 수료생 2는 풀 안에 칸 통과 쌍이 없음

    def test_trap_entry_by_student_and_type(self):
        cards = em.load_cards(self.runs, [1, 2, 9])
        t = em.trap_entry(cards, self.labels(), [1, 2, 9])
        self.assertEqual(t["per_student"], {1: (1, 3), 2: (0, 1)})  # RULE 의 TRAP 은 대상이 아님
        self.assertEqual(t["by_type"], {"SKILL": (1, 1), "LOOKALIKE": (0, 1), "CAREER": (0, 1), "REGION": (0, 1)})
        self.assertEqual(em.type_order(t["by_type"]), ["SKILL", "LOOKALIKE", "CAREER", "REGION"])

    def test_precision_at_3(self):
        self.assertEqual(em.precision_at_3(em.load_cards(self.runs, [1, 2, 9]), self.labels()), (3, 4))

    def test_report_dev_has_per_student_lines(self):
        lines = em.report(self.runs, self.labels_path, "dev", split=SPLIT, no_match=NO_MATCH)
        text = "\n".join(lines)
        self.assertIn("Precision@3: 3/4 = 75%", text)
        self.assertIn("[1] 함정 1/3", text)
        self.assertIn("수료생 평균 · METRICS 값과 같은 정의): 전체 풀 75.0% (차이 +0.0점)", text)
        self.assertIn("쌍 단위 · 참고): 전체 풀 60.0% (차이 +15.0점)", text)
        self.assertIn("[9] 함정 대상 없음", text)

    def test_report_test_refuses_per_student(self):
        with self.assertRaises(ValueError):
            em.report(self.runs, self.labels_path, "test", per_student=True, split=SPLIT, no_match=NO_MATCH)
        lines = em.report(self.runs, self.labels_path, "test", split=SPLIT, no_match=NO_MATCH)
        self.assertFalse([line for line in lines if line.startswith("    [")])  # 수료생별 줄 없음

    def test_no_match_matches_score_matching(self):
        sys.path.insert(0, str(em.AI_ROOT / "eval"))
        try:
            from score_matching import NO_MATCH as scoring_no_match
        finally:
            sys.path.remove(str(em.AI_ROOT / "eval"))
        self.assertEqual(em.NO_MATCH, scoring_no_match)


if __name__ == "__main__":
    unittest.main()
