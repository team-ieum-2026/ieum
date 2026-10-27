# 주인: AI-A
"""common/reranker.py 테스트. 실제 모델을 불러오지 않는다 (sentence-transformers 없이도 실행됨).

실행: cd ai && .venv/bin/python -m unittest common.test_reranker -v
"""

import builtins
import unittest
from unittest.mock import MagicMock, patch

from common import reranker


def fake_model(*scores: float) -> MagicMock:
    model = MagicMock()
    model.predict.return_value = list(scores)
    return model


class RerankTest(unittest.TestCase):
    def use(self, model: MagicMock) -> MagicMock:
        p = patch.object(reranker, "_load_model", return_value=model)
        p.start()
        self.addCleanup(p.stop)
        return model

    def test_sorted_by_score_and_cut_to_top_k(self):
        self.use(fake_model(0.4, 0.9, 0.7, 0.1))
        self.assertEqual(reranker.rerank("q", ["a", "b", "c", "d"], top_k=3), [(1, 0.9), (2, 0.7), (0, 0.4)])

    def test_pairs_are_query_and_each_candidate(self):
        model = self.use(fake_model(0.1, 0.2))
        reranker.rerank("프로필", ["공고1", "공고2"])
        self.assertEqual(model.predict.call_args.args[0], [("프로필", "공고1"), ("프로필", "공고2")])

    def test_tie_keeps_original_order(self):
        self.use(fake_model(0.6, 0.6, 0.6))
        self.assertEqual([i for i, _ in reranker.rerank("q", ["a", "b", "c"], top_k=2)], [0, 1])

    def test_empty_candidates_does_not_load_model(self):
        with patch.object(reranker, "_load_model") as load:
            self.assertEqual(reranker.rerank("q", []), [])
            load.assert_not_called()

    def test_predict_failure_raises_reranker_error(self):
        model = self.use(fake_model())
        model.predict.side_effect = RuntimeError("boom")
        with self.assertRaises(reranker.RerankerError):
            reranker.rerank("q", ["a"])

    def test_missing_library_raises_reranker_error(self):
        real_import = builtins.__import__

        def no_sentence_transformers(name, *args, **kwargs):
            if name in ("torch", "sentence_transformers"):
                raise ImportError(name)
            return real_import(name, *args, **kwargs)

        reranker._load_model.cache_clear()
        self.addCleanup(reranker._load_model.cache_clear)
        with patch("builtins.__import__", side_effect=no_sentence_transformers):
            with self.assertRaises(reranker.RerankerError):
                reranker._load_model()


if __name__ == "__main__":
    unittest.main()
