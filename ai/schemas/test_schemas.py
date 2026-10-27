# 주인: AI-A
"""schemas 테스트: 예시 JSON 이 형식대로 읽히는지, AI-B 프로필 추출 요청과 호환되는지.

실행: cd ai && .venv/bin/python -m unittest schemas.test_schemas -v
"""

import json
import unittest
from pathlib import Path

from pydantic import ValidationError

from schemas import CompanyInfo, DraftRequest, Evidence, EvidenceRef, StudentMaterial

AI_DIR = Path(__file__).resolve().parent.parent
FIXTURES = AI_DIR / "eval" / "fixtures"


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


class DraftRequestTest(unittest.TestCase):
    def setUp(self):
        self.example = load(FIXTURES / "draft_request.example.json")

    def test_example_is_valid(self):
        req = DraftRequest.model_validate(self.example)
        self.assertEqual(req.job.job_posting_id, 501)
        self.assertEqual(req.company_info.status, "FOUND")
        self.assertEqual([p.item_id for p in req.student.projects], [11, 12])
        self.assertIn("1.8s → 0.4s", req.student.projects[0].readme)

    def test_round_trip_keeps_camel_case(self):
        dumped = DraftRequest.model_validate(self.example).model_dump(by_alias=True)
        self.assertIn("applicationId", dumped)
        self.assertIn("jobPostingId", dumped["job"])
        self.assertIn("companyInfoId", dumped["companyInfo"])
        self.assertIn("roleNote", dumped["student"]["projects"][0])

    def test_profile_and_company_info_can_be_null(self):
        self.example["profile"] = None
        self.example["companyInfo"] = None
        req = DraftRequest.model_validate(self.example)
        self.assertIsNone(req.profile)
        self.assertIsNone(req.company_info)

    def test_unknown_fields_are_ignored(self):
        self.example["job"]["rawPayload"] = {"anything": 1}
        self.example["student"]["newFieldFromBe"] = "x"
        DraftRequest.model_validate(self.example)

    def test_missing_job_is_rejected(self):
        del self.example["job"]
        with self.assertRaises(ValidationError):
            DraftRequest.model_validate(self.example)


class StudentMaterialTest(unittest.TestCase):
    def test_ai_b_profile_extract_request_is_compatible(self):
        """BE가 프로필 추출에 보내는 객체를 서류 생성 student 로 그대로 쓸 수 있어야 한다"""
        sample = load(AI_DIR / "data" / "samples" / "profile_extract_request.json")
        student = StudentMaterial.model_validate(sample)
        self.assertEqual(student.student_id, sample["studentId"])


class CompanyInfoTest(unittest.TestCase):
    def setUp(self):
        self.examples = load(FIXTURES / "company_info.examples.json")

    def test_three_statuses(self):
        for status in ("FOUND", "PARTIAL", "NOT_FOUND"):
            info = CompanyInfo.model_validate(self.examples[status])
            self.assertEqual(info.status, status)

    def test_not_found_has_nothing(self):
        info = CompanyInfo.model_validate(self.examples["NOT_FOUND"])
        self.assertEqual((info.talent_keywords, info.core_values, info.recent_issue, info.sources), ([], [], None, []))

    def test_collect_result_without_db_id(self):
        """AI-B 수집 결과는 DB 저장 전이라 companyInfoId 가 없다"""
        data = dict(self.examples["PARTIAL"])
        del data["companyInfoId"]
        self.assertIsNone(CompanyInfo.model_validate(data).company_info_id)

    def test_unknown_status_is_rejected(self):
        data = dict(self.examples["FOUND"], status="MAYBE")
        with self.assertRaises(ValidationError):
            CompanyInfo.model_validate(data)


class RefTypeTest(unittest.TestCase):
    def test_career_and_job_posting(self):
        self.assertEqual(EvidenceRef.model_validate({"refType": "CAREER", "refId": 3}).ref_type, "CAREER")
        ev = Evidence.model_validate(
            {"refType": "JOB_POSTING", "refId": 501, "label": "공고 주요 업무", "quote": "REST API 개발"}
        )
        self.assertEqual(ev.model_dump(by_alias=True)["refType"], "JOB_POSTING")

    def test_unknown_ref_type_is_rejected(self):
        with self.assertRaises(ValidationError):
            EvidenceRef.model_validate({"refType": "SKILL", "refId": 1})


if __name__ == "__main__":
    unittest.main()
