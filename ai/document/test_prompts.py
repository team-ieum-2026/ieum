# 주인: AI-A
"""서류 생성 프롬프트(prompts.py) 개인정보 테스트: 이름 · 이메일 · GitHub · 연락처가 LLM 에 가지 않는지.

이름 · 이메일 · GitHub 는 서류 BASIC 섹션에만 옮기고 프롬프트에는 넣지 않는다 (schemas/student.py BasicInfo).
실행: cd ai && .venv/bin/python -m unittest document.test_prompts -v
"""

import json
import unittest
from pathlib import Path

from document.prompts import SYSTEM_INSTRUCTION, build_prompt
from document.sources import build_sources
from schemas.draft import DraftRequest

FIXTURE = Path(__file__).resolve().parent.parent / "eval" / "fixtures" / "draft_request.example.json"


def prompt_for(data: dict) -> str:
    req = DraftRequest.model_validate(data)
    return SYSTEM_INSTRUCTION + build_prompt(req, build_sources(req))


class PromptPrivacyTest(unittest.TestCase):
    def setUp(self):
        self.data = json.loads(FIXTURE.read_text(encoding="utf-8"))

    def test_basic_info_not_in_prompt(self):
        basic = self.data["student"]["basic"]
        prompt = prompt_for(self.data)
        for field in ("name", "email", "githubUrl"):
            self.assertNotIn(basic[field], prompt, field)

    def test_contacts_in_free_text_are_masked(self):
        # 수료생이 README · 역할 기록 · 자기소개서에 연락처를 적어 둔 경우
        email, phone = "someone@example.com", "010-1234-5678"
        project = self.data["student"]["projects"][0]
        project["roleNote"] += f" 문의: {email}"
        project["readme"] += f"\n연락처 {phone}"
        self.data["student"]["coverLetter"]["content"] += f" 연락 주세요 {phone}."
        prompt = prompt_for(self.data)
        self.assertNotIn(email, prompt)
        self.assertNotIn(phone, prompt)
        self.assertIn("[연락처]", prompt)


if __name__ == "__main__":
    unittest.main()
