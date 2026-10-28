# 주인: AI-B (AI-A 에서 넘겨받음)
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


class TextScoreModel:
    """후보 글마다 정해진 점수를 돌려주는 가짜 모델. predict 에 들어온 묶음을 기록한다."""

    def __init__(self, scores: dict[str, float]):
        self.scores = scores
        self.calls: list[dict] = []

    def predict(self, pairs, batch_size=None, show_progress_bar=None):
        self.calls.append({"pairs": list(pairs), "batch_size": batch_size})
        return [self.scores[c] for _, c in pairs]


def varied_candidates(n: int) -> list[str]:
    """길이가 섞인 후보 n 개 (같은 길이도 일부 있음)"""
    return [f"공고{i}-" + "가" * ((i * 7) % 11) for i in range(n)]


class BatchTest(unittest.TestCase):
    def use(self, model) -> None:
        p = patch.object(reranker, "_load_model", return_value=model)
        p.start()
        self.addCleanup(p.stop)

    def run_case(self, n: int):
        cands = varied_candidates(n)
        model = TextScoreModel({c: round(0.01 * ((i * 37) % 97), 4) for i, c in enumerate(cands)})
        self.use(model)
        result = reranker.rerank("프로필", cands, top_k=n)
        return cands, model, result

    def test_batch_size_is_8(self):
        self.assertEqual(reranker.BATCH_SIZE, 8)

    def test_scores_return_to_original_order(self):
        for n in (20, 3):
            with self.subTest(n=n):
                cands, model, result = self.run_case(n)
                # 정렬 · 묶음을 거쳐도 후보 i 의 점수는 정확히 후보 i 의 것
                self.assertEqual(dict(result), {i: model.scores[c] for i, c in enumerate(cands)})
                expected = sorted(range(n), key=lambda i: (-model.scores[cands[i]], i))
                self.assertEqual([i for i, _ in result], expected)

    def test_batches_of_8_sorted_by_length(self):
        for n, sizes in ((20, [8, 8, 4]), (3, [3]), (8, [8]), (9, [8, 1])):
            with self.subTest(n=n):
                cands, model, _ = self.run_case(n)
                self.assertEqual([len(c["pairs"]) for c in model.calls], sizes)
                self.assertTrue(all(c["batch_size"] == 8 for c in model.calls))
                lengths = [len(q) + len(d) for c in model.calls for q, d in c["pairs"]]
                self.assertEqual(lengths, sorted(lengths, reverse=True))
                # 모든 후보가 정확히 한 번씩
                self.assertEqual(sorted(d for c in model.calls for _, d in c["pairs"]), sorted(cands))

    def test_zero_candidates(self):
        with patch.object(reranker, "_load_model") as load:
            self.assertEqual(reranker.rerank("q", []), [])
            load.assert_not_called()

    def test_top_k_cut_after_all_batches(self):
        cands, model, _ = self.run_case(20)
        top = reranker.rerank("프로필", cands, top_k=3)
        best = sorted(range(20), key=lambda i: (-model.scores[cands[i]], i))[:3]
        self.assertEqual([i for i, _ in top], best)

    def test_score_count_mismatch_raises(self):
        model = MagicMock()
        model.predict.return_value = [0.5]  # 2쌍에 점수 1개
        self.use(model)
        with self.assertRaises(reranker.RerankerError):
            reranker.rerank("q", ["a", "bb"])


class WarmupTest(unittest.TestCase):
    """warmup() 은 모델을 한 번만 만든다. 가짜 torch · sentence_transformers 로 실제 모델을 불러오지 않는다."""

    def test_loads_model_once(self):
        import sys
        import types

        created = []

        class FakeCrossEncoder:
            def __init__(self, name, **kwargs):
                created.append(name)
                self.device = "cpu"

            def predict(self, pairs, batch_size=None, show_progress_bar=None):
                return [0.5] * len(pairs)

        torch = types.ModuleType("torch")
        torch.cuda = types.SimpleNamespace(is_available=lambda: False)
        torch.backends = types.SimpleNamespace(mps=types.SimpleNamespace(is_available=lambda: False))
        torch.nn = types.SimpleNamespace(Sigmoid=lambda: "sigmoid")
        st = types.ModuleType("sentence_transformers")
        st.CrossEncoder = FakeCrossEncoder

        reranker._load_model.cache_clear()
        self.addCleanup(reranker._load_model.cache_clear)
        with patch.dict(sys.modules, {"torch": torch, "sentence_transformers": st}):
            self.assertIsNone(reranker.warmup())
            reranker.warmup()
            reranker.rerank("q", ["a", "b"])
        self.assertEqual(len(created), 1)

    def test_warmup_failure_raises_reranker_error(self):
        with patch.object(reranker, "_load_model", side_effect=reranker.RerankerError("로드 실패")):
            with self.assertRaises(reranker.RerankerError):
                reranker.warmup()


class StdlibProfileTest(unittest.TestCase):
    """ai/profile/ (AI-B) 이 표준 profile 을 가려도 torch import 동안만 표준 모듈로 바뀌고 다시 돌아오는지"""

    def test_swaps_only_inside_and_restores(self):
        import sys
        import types

        fake_ai_b = types.ModuleType("profile")  # run 이 없는 AI-B 패키지 흉내
        original = sys.modules.get("profile")
        sys.modules["profile"] = fake_ai_b
        try:
            with reranker._stdlib_profile():
                self.assertTrue(hasattr(sys.modules["profile"], "run"))
            self.assertIs(sys.modules["profile"], fake_ai_b)
        finally:
            if original is None:
                sys.modules.pop("profile", None)
            else:
                sys.modules["profile"] = original


if __name__ == "__main__":
    unittest.main()
