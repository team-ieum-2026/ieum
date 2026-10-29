# 주인: AI-A
"""Gemini · OpenAI 모델 비교 실행.

실행 (ai 폴더에서):
  .venv/bin/python -m eval.model_compare.run --dry               # 호출 없이 과제 · 호출 수 · 예상 입력 크기만
  .venv/bin/python -m eval.model_compare.run --limit 1           # 모델마다 과제당 1건씩 (연결 확인)
  .venv/bin/python -m eval.model_compare.run                     # 전체 (과제 A 31건 + B 5건) × 모델 6개
  .venv/bin/python -m eval.model_compare.run --models gpt-6-luna gemini-3.8-flash --tasks A

결과: eval/results/model_compare/<시각>.jsonl (커밋 안 됨) + 화면에 요약표.
"""

import argparse
import json
import statistics
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from pathlib import Path

from eval.model_compare.providers import MODELS, call
from eval.model_compare.tasks import TASKS

OUT = Path(__file__).resolve().parents[1] / "results" / "model_compare"


def run_one(model: str, task: str, item: dict) -> dict:
    _, schema, system, scorer = TASKS[task]
    res = call(model, system, item["prompt"], schema)
    return {
        "model": model, "task": task, "item": item["id"], "error": res.error,
        "score": scorer(res.parsed, item["gold"]) if res.parsed else None,
        "latency_s": round(res.latency_s, 2), "input_tokens": res.input_tokens,
        "output_tokens": res.output_tokens, "cost_usd": round(res.cost_usd, 6), "raw": res.raw,
    }


def p95(values: list[float]) -> float:
    values = sorted(values)
    return values[min(len(values) - 1, int(round(0.95 * (len(values) - 1))))] if values else 0.0


def summarize(rows: list[dict]) -> None:
    for task in sorted({r["task"] for r in rows}):
        print(f"\n## 과제 {task}")
        keys = ["family", "career", "education", "skills_f1", "all_correct"] if task == "A" \
            else ["grounded_rate", "kept_rate", "sentences", "cover_chars"]
        print("| 모델 | 등급 | " + " | ".join(keys) + " | 형식 실패 | 지연 p50 / p95 (초) | 비용 합계 ($) |")
        print("| --- " * (len(keys) + 6) + "|")
        for model in MODELS:
            mine = [r for r in rows if r["task"] == task and r["model"] == model]
            if not mine:
                continue
            ok = [r["score"] for r in mine if r["score"]]
            vals = [f"{statistics.mean(float(s[k]) for s in ok):.2f}" if ok else "-" for k in keys]
            lat = [r["latency_s"] for r in mine]
            fail = sum(1 for r in mine if r["error"])
            print(f"| {model} | {MODELS[model][1]} | " + " | ".join(vals)
                  + f" | {fail}/{len(mine)} | {statistics.median(lat):.1f} / {p95(lat):.1f} | {sum(r['cost_usd'] for r in mine):.4f} |")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--models", nargs="*", default=list(MODELS))
    ap.add_argument("--tasks", nargs="*", default=list(TASKS))
    ap.add_argument("--limit", type=int, default=None, help="과제당 앞에서 N건만")
    ap.add_argument("--dry", action="store_true", help="호출 없이 계획만 출력")
    args = ap.parse_args()

    jobs = [(m, t, item) for t in args.tasks for item in TASKS[t][0](args.limit) for m in args.models]
    if args.dry:
        for t in args.tasks:
            items = TASKS[t][0](args.limit)
            print(f"과제 {t}: {len(items)}건 × 모델 {len(args.models)}개 · 프롬프트 평균 {statistics.mean(len(i['prompt']) for i in items):.0f}자")
        print(f"총 호출 {len(jobs)}회")
        return

    with ThreadPoolExecutor(max_workers=6) as pool:
        rows = list(pool.map(lambda j: run_one(*j), jobs))
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / f"{datetime.now():%Y%m%d-%H%M%S}.jsonl"
    path.write_text("\n".join(json.dumps(r, ensure_ascii=False) for r in rows) + "\n", encoding="utf-8")
    for r in rows:
        if r["error"]:
            print(f"오류 {r['model']} {r['task']} {r['item']}: {r['error']}")
    summarize(rows)
    print(f"\n결과 원본: {path}")


if __name__ == "__main__":
    main()
