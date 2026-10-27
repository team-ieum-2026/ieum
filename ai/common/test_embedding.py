# 주인: AI-A
"""common/embedding.py 테스트. Gemini를 실제로 부르지 않는다 (키 · 비용 없음).

실행: cd ai && .venv/bin/python -m unittest common.test_embedding -v
"""

import unittest
from unittest.mock import MagicMock, patch

from common import embedding
from common.config import Settings


def fake_client() -> MagicMock:
    """입력 문장마다 [문장 길이, 0.0] 벡터를 돌려주는 가짜 Gemini 클라이언트"""
    client = MagicMock()

    def embed_content(*, model, contents, config):
        return MagicMock(embeddings=[MagicMock(values=[float(len(t)), 0.0]) for t in contents])

    client.models.embed_content.side_effect = embed_content
    return client


class EmbedTextsTest(unittest.TestCase):
    def setUp(self):
        self.settings = Settings(gemini_api_key="test", embedding_model="test-embedding")
        settings = patch.object(embedding, "get_settings", return_value=self.settings)
        settings.start()
        self.addCleanup(settings.stop)
        self.client = fake_client()
        client = patch.object(embedding, "_client", return_value=self.client)
        client.start()
        self.addCleanup(client.stop)

    def test_vectors_keep_input_order(self):
        self.assertEqual(embedding.embed_texts(["a", "bbb"]), [[1.0, 0.0], [3.0, 0.0]])

    def test_empty_input_does_not_call(self):
        self.assertEqual(embedding.embed_texts([]), [])
        self.client.models.embed_content.assert_not_called()

    def test_splits_into_batches(self):
        texts = ["x"] * (embedding.BATCH_SIZE * 2 + 1)
        self.assertEqual(len(embedding.embed_texts(texts)), len(texts))
        sizes = [len(c.kwargs["contents"]) for c in self.client.models.embed_content.call_args_list]
        self.assertEqual(sizes, [embedding.BATCH_SIZE, embedding.BATCH_SIZE, 1])

    def test_task_type_is_passed(self):
        embedding.embed_texts(["프로필"], task_type="RETRIEVAL_QUERY")
        config = self.client.models.embed_content.call_args.kwargs["config"]
        self.assertEqual(config.task_type, "RETRIEVAL_QUERY")

    def test_api_failure_raises_embedding_error(self):
        self.client.models.embed_content.side_effect = RuntimeError("boom")
        with self.assertRaises(embedding.EmbeddingError):
            embedding.embed_texts(["a"])

    def test_count_mismatch_raises(self):
        self.client.models.embed_content.side_effect = None
        self.client.models.embed_content.return_value = MagicMock(embeddings=[])
        with self.assertRaises(embedding.EmbeddingError):
            embedding.embed_texts(["a"])

    def test_missing_model_raises(self):
        self.settings.embedding_model = ""
        with self.assertRaises(embedding.EmbeddingError):
            embedding.embed_texts(["a"])


if __name__ == "__main__":
    unittest.main()
