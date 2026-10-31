# 주인: AI-A
"""common/llm.py 테스트. Gemini를 실제로 부르지 않는다 (키 · 비용 없음).

실행: cd ai && .venv/bin/python -m unittest common.test_llm -v
"""

import unittest
from unittest.mock import MagicMock, patch

from common import llm
from common.config import Settings
from schemas import CompetencyProfile

VALID = '{"roles": ["백엔드 개발"], "desiredJob": "백엔드 개발"}'
BROKEN = '{"roles": '


def fake_client(*texts: str) -> MagicMock:
    """texts 를 차례대로 돌려주는 가짜 Gemini 클라이언트"""
    client = MagicMock()
    client.models.generate_content.side_effect = [MagicMock(text=t, usage_metadata=None) for t in texts]
    return client


class GenerateJsonTest(unittest.TestCase):
    def setUp(self):
        settings = patch.object(llm, "get_settings", return_value=Settings(gemini_api_key="test", llm_model="test-model"))
        settings.start()
        self.addCleanup(settings.stop)

    def use(self, client: MagicMock) -> None:
        p = patch.object(llm, "_client", return_value=client)
        p.start()
        self.addCleanup(p.stop)

    def test_valid_json(self):
        client = fake_client(VALID)
        self.use(client)
        profile = llm.generate_json("프롬프트", CompetencyProfile)
        self.assertIsInstance(profile, CompetencyProfile)
        self.assertEqual(profile.desired_job, "백엔드 개발")
        self.assertEqual(client.models.generate_content.call_count, 1)

    def test_retry_once_then_success(self):
        client = fake_client(BROKEN, VALID)
        self.use(client)
        profile = llm.generate_json("프롬프트", CompetencyProfile)
        self.assertEqual(profile.roles, ["백엔드 개발"])
        self.assertEqual(client.models.generate_content.call_count, 2)

    def test_invalid_twice_raises(self):
        client = fake_client(BROKEN, BROKEN)
        self.use(client)
        with self.assertRaises(llm.LLMError):
            llm.generate_json("프롬프트", CompetencyProfile)
        self.assertEqual(client.models.generate_content.call_count, 1 + llm.MAX_RETRIES)

    def test_sends_camel_case_schema_and_disables_afc(self):
        client = fake_client(VALID)
        self.use(client)
        llm.generate_json("프롬프트", CompetencyProfile)
        config = client.models.generate_content.call_args.kwargs["config"]
        self.assertEqual(config.response_mime_type, "application/json")
        self.assertIn("desiredJob", config.response_json_schema["properties"])
        self.assertTrue(config.automatic_function_calling.disable)

    def test_default_model_is_llm_model(self):
        client = fake_client(VALID)
        self.use(client)
        llm.generate_json("프롬프트", CompetencyProfile)
        self.assertEqual(client.models.generate_content.call_args.kwargs["model"], "test-model")

    def test_model_override(self):
        client = fake_client(VALID)
        self.use(client)
        llm.generate_json("프롬프트", CompetencyProfile, model="doc-model")
        self.assertEqual(client.models.generate_content.call_args.kwargs["model"], "doc-model")

    def test_empty_override_falls_back(self):
        client = fake_client(VALID)
        self.use(client)
        llm.generate_json("프롬프트", CompetencyProfile, model="")
        self.assertEqual(client.models.generate_content.call_args.kwargs["model"], "test-model")

    def test_api_error_raises_llm_error(self):
        client = MagicMock()
        client.models.generate_content.side_effect = RuntimeError("503")
        self.use(client)
        with self.assertRaises(llm.LLMError):
            llm.generate_json("프롬프트", CompetencyProfile)

    def test_empty_response_raises(self):
        self.use(fake_client(""))
        with self.assertRaises(llm.LLMError):
            llm.generate_text("프롬프트")


class SettingsCheckTest(unittest.TestCase):
    def test_empty_model_raises(self):
        with patch.object(llm, "get_settings", return_value=Settings(gemini_api_key="test", llm_model="")):
            with self.assertRaises(llm.LLMError):
                llm._model()

    def test_empty_key_raises(self):
        llm._client.cache_clear()
        self.addCleanup(llm._client.cache_clear)
        with patch.object(llm, "get_settings", return_value=Settings(gemini_api_key="", llm_model="test-model")):
            with self.assertRaises(llm.LLMError):
                llm._client()

    def test_client_has_timeout(self):
        llm._client.cache_clear()
        self.addCleanup(llm._client.cache_clear)
        settings = Settings(gemini_api_key="test", llm_model="test-model", llm_timeout_sec=30)
        with patch.object(llm, "get_settings", return_value=settings), patch.object(llm.genai, "Client") as client:
            llm._client()
        self.assertEqual(client.call_args.kwargs["http_options"].timeout, 30_000)


if __name__ == "__main__":
    unittest.main()
