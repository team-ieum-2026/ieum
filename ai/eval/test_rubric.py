# 주인: AI-A
"""rubric.py 와 실제 · 일반 공고 변환(postings/external.to_posting) 테스트.

실행: cd ai && .venv/bin/python -m unittest eval.test_rubric -v
"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))  # cross_label.py 처럼 rubric 을 바로 import 한다

from rubric import score, violations  # noqa: E402

from eval.postings.external import to_posting  # noqa: E402

STUDENT = {
    "desired_job": "백엔드 개발",
    "preferred_region": "서울",
    "education": [{"degree": "BACHELOR"}],
    "skills": ["Java", "Spring Boot", "MySQL"],
}


def posting(**changes) -> dict:
    base = {"family": "BE", "career": "신입", "education": "학력무관", "location": "서울 구로구",
            "required": ["Java", "Spring"]}
    return {**base, **changes}


class RubricTest(unittest.TestCase):
    def test_generated_posting_unchanged(self):
        # 생성 공고 형식(직무군 하나, | 없는 기술)은 기존과 같게 판정한다
        self.assertEqual(score(STUDENT, posting()), 2)
        self.assertEqual(violations(STUDENT, posting(required=["Python"])), ["SKILL"])

    def test_skill_options_need_only_one(self):
        self.assertEqual(score(STUDENT, posting(required=["Python|Java"])), 2)
        self.assertEqual(violations(STUDENT, posting(required=["Python|Go"])), ["SKILL"])

    def test_skill_option_uses_alias(self):
        # 묶음 안에서도 동의어(sql → MySQL)를 인정한다
        self.assertEqual(score(STUDENT, posting(required=["Oracle|SQL"])), 2)

    def test_two_families(self):
        self.assertEqual(score(STUDENT, posting(family=["EMBEDDED", "BE"])), 2)
        self.assertIn("JOB", violations(STUDENT, posting(family=["EMBEDDED", "FE"])))


class ToPostingTest(unittest.TestCase):
    def record(self, **extracted) -> dict:
        base = {"jobFamilies": ["BE"], "familyQuote": "", "bodyCareer": "적혀 있지 않음",
                "bodyEducation": "적혀 있지 않음",
                "requiredSkills": [{"original": "ESP32 or Aduino", "options": ["ESP32", "Arduino"], "quote": ""}]}
        return {"fields": {"career": "관계없음", "education": "학력무관", "location": "경기 성남시"},
                "extracted": {**base, **extracted}}

    def test_fields_used_when_body_silent(self):
        p = to_posting(self.record())
        self.assertEqual((p["career"], p["education"], p["location"]), ("관계없음", "학력무관", "경기 성남시"))
        self.assertEqual(p["required"], ["ESP32|Arduino"])

    def test_body_overrides_fields(self):
        p = to_posting(self.record(bodyEducation="대졸(4년)", bodyCareer="경력"))
        self.assertEqual((p["career"], p["education"]), ("경력", "대졸(4년)"))


if __name__ == "__main__":
    unittest.main()
