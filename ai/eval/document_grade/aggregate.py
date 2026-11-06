# 주인: AI-A
"""서류 채점 집계 (METRICS.md 2장, 4장, DOCUMENT_LABELING.md 6장).

실행 (ai 폴더에서):
  .venv/bin/python -m eval.document_grade.aggregate --name v3.9.4-official                     # LLM 채점만 (사람 전)
  .venv/bin/python -m eval.document_grade.aggregate --name v3.9.4-official --human 가.csv 나.csv 다.csv   # 최종 (사람 1~3명)

결과: eval/results/document_grade/<이름>/report.md, report.json
- 최종 점수: 두 LLM 이 같으면 그 점수, 다르면 사람 점수. 사람 점수가 아직 없으면 "미정"으로 센다
- 사람이 여러 명이면 다수결. 다수가 없으면 보수적으로 정한다: 문장은 낮은 점수 (3명이 모두 다르면 가운데 1점),
  반영도는 동등, 기업 정보 조작은 조작으로 본다. 다수가 없던 항목 수를 따로 보고한다
- 근거 충실도 = 2점 / AI 문장, 허위율 = 0점 / AI 문장, 과장률 = 1점 / AI 문장. 비율마다 95% Wilson 구간
- 반영도 = 재작성 우세 서류 / 비교한 서류 (동등 포함)
- 기업 정보 조작: 사람이 4단계에서 확인한 조작 문장 수 (상태별)
- 일치도: 이차 가중 카파. GPT 와 Claude (전체), 사람과 각 LLM, 사람과 LLM 합의 (무작위 30문장)
"""

import argparse
import csv
import json
import math
import statistics
from collections import Counter
from pathlib import Path

from eval.document_set.run_drafts import OUT as RUNS

AI = Path(__file__).parents[2]
GRADE = AI / "eval/results/document_grade"
ITEMS = ("duties", "skills", "preferred", "connection")


def wilson(k: int, n: int, z: float = 1.96) -> tuple[float, float] | None:
    if n == 0:
        return None
    p = k / n
    d = 1 + z * z / n
    c = (p + z * z / (2 * n)) / d
    h = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / d
    return max(0.0, c - h), min(1.0, c + h)


def qwk(a: list[int], b: list[int], k: int = 3) -> float | None:
    """이차 가중 카파. 한쪽 점수가 한 값뿐이면 정의되지 않아 None"""
    n = len(a)
    if n == 0:
        return None
    obs = [[0] * k for _ in range(k)]
    for x, y in zip(a, b):
        obs[x][y] += 1
    ra, rb = Counter(a), Counter(b)
    num = den = 0.0
    for i in range(k):
        for j in range(k):
            w = (i - j) ** 2 / (k - 1) ** 2
            num += w * obs[i][j]
            den += w * ra[i] * rb[j] / n
    return None if den == 0 else 1 - num / den


def confusion(a: list[int], b: list[int]) -> list[list[int]]:
    m = [[0] * 3 for _ in range(3)]
    for x, y in zip(a, b):
        m[x][y] += 1
    return m


def verdict(r: dict | None) -> str | None:
    if not r:
        return None
    diff = sum(r["A"][i] for i in ITEMS) - sum(r["B"][i] for i in ITEMS)
    return "A" if diff >= 2 else "B" if diff <= -2 else "EQUAL"


def load_grader(base: Path, grader: str) -> dict:
    out = {}
    for f in (base / grader).glob("*.json"):
        data = json.loads(f.read_text())
        out[f.stem] = data.get("result", data)
    return out


def load_human(path: Path) -> dict:
    """CSV 하나 (human_page.html 내보내기) → {stage: {item_id: row}}"""
    out = {1: {}, 2: {}, 3: {}, 4: {}}
    with path.open(encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            out[int(row["stage"])][row["item_id"]] = row
    return out


def majority(values: list, tie):
    """다수결. 다수가 없으면 tie(values) 로 정하고 (값, 다수 여부) 를 돌려준다"""
    if not values:
        return None, True
    top, n = Counter(values).most_common(1)[0]
    if n * 2 > len(values):
        return top, True
    return tie(values), False


def sentence_tie(values: list[int]) -> int:
    return sorted(values)[len(values) // 2] if len(set(values)) == 3 else min(values)


def pct(k, n):
    return f"{k}/{n} ({k / n:.1%})" if n else "-"


def ci(k, n):
    w = wilson(k, n)
    return f"[{w[0]:.1%}, {w[1]:.1%}]" if w else "-"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--name", default="v3.9.4-official")
    parser.add_argument("--human", type=Path, nargs="*", default=[], help="사람 채점 CSV (1~3명)")
    args = parser.parse_args()
    base = GRADE / args.name
    docs = json.loads((base / "sheet.json").read_text())["documents"]
    key = json.loads((base / "key.json").read_text())
    gpt, claude = load_grader(base, "gpt"), load_grader(base, "claude")
    raters = [load_human(p) for p in args.human]
    per_rater = [{i: int(r["score"]) for s in (1, 2) for i, r in h[s].items() if r.get("score", "") != ""} for h in raters]
    human_score, ties = {}, Counter()
    for i in {i for pr in per_rater for i in pr}:
        human_score[i], ok = majority([pr[i] for pr in per_rater if i in pr], sentence_tie)
        ties["sentence"] += not ok

    rows = []  # 문장마다 (doc, status, gpt, claude, human, final, company)
    for d in docs:
        g = {s["sentenceId"]: s for s in gpt[d["docId"]]["sentences"]}
        c = {s["sentenceId"]: s for s in claude[d["docId"]]["sentences"]}
        for s in d["sentences"]:
            gs, cs, hs = g[s["id"]]["score"], c[s["id"]]["score"], human_score.get(s["id"])
            final = gs if gs == cs else hs
            rows.append({"id": s["id"], "doc": d["docId"], "status": d["companyInfoStatus"], "gpt": gs, "claude": cs,
                         "human": hs, "final": final,
                         "company": g[s["id"]]["companyRelated"] or c[s["id"]]["companyRelated"]
                         or any(e["type"] == "COMPANY_INFO" for e in s["evidence"]),
                         "llmFabricated": g[s["id"]]["fabricated"] or c[s["id"]]["fabricated"]})
    n = len(rows)
    scored = [r for r in rows if r["final"] is not None]
    pending = n - len(scored)
    count = Counter(r["final"] for r in scored)

    # 반영도: 재작성 쪽 기준으로 판정을 바꾼다
    refl = []
    for d in docs:
        if not d["reflection"]:
            continue
        side = key["reflection"][d["docId"]]  # 재작성이 A 인지 B 인지
        to_result = lambda v: None if v is None else "EQUAL" if v == "EQUAL" else ("REWRITE" if v == side else "ORIGINAL")
        gv, cv = to_result(verdict(gpt[d["docId"]]["reflection"])), to_result(verdict(claude[d["docId"]]["reflection"]))
        votes = []
        for hr in raters:
            h = hr[3].get("doc-" + d["docId"])
            if h and all(h.get(f"{x}_{i}", "") != "" for x in "AB" for i in ITEMS):
                votes.append(to_result(verdict({x: {i: int(h[f"{x}_{i}"]) for i in ITEMS} for x in "AB"})))
        hv, ok = majority(votes, lambda v: "EQUAL")
        ties["reflection"] += not ok
        # 참고: 재작성 합계와 원본 합계 (8점 만점). 10건 모두 채점한 두 LLM 기준
        orig = "B" if side == "A" else "A"
        totals = {name: [sum(r["reflection"][x][i] for i in ITEMS) for x in (side, orig)]
                  for name, r in (("gpt", gpt[d["docId"]]), ("claude", claude[d["docId"]]))}
        refl.append({"doc": d["docId"], "gpt": gv, "claude": cv, "human": hv, "final": gv if gv == cv else hv, "totals": totals})
    refl_final = Counter(r["final"] for r in refl)
    refl_done = [r for r in refl if r["final"] is not None]
    refl_avg = {name: [round(sum(r["totals"][name][k] for r in refl) / len(refl), 2) for k in (0, 1)] if refl else None
                for name in ("gpt", "claude")}
    refl_worse = sum(any(r["totals"][name][0] < r["totals"][name][1] for name in ("gpt", "claude")) for r in refl)

    # 기업 정보 조작 (사람 4단계)
    company = [r for r in rows if r["company"]]
    fab = {}
    for i in {i for hr in raters for i in hr[4]}:
        votes = [hr[4][i]["fabricated"] == "true" for hr in raters if hr[4].get(i, {}).get("fabricated", "") != ""]
        if votes:
            fab[i], ok = majority(votes, lambda v: True)
            ties["fabrication"] += not ok
    by_status = {}
    for st in ("FOUND", "PARTIAL", "NOT_FOUND"):
        cs = [r for r in company if r["status"] == st]
        checked = [r for r in cs if r["id"] in fab]
        by_status[st] = {"companySentences": len(cs), "checked": len(checked), "fabricated": sum(fab[r["id"]] for r in checked),
                         "llmFlagged": sum(r["llmFabricated"] for r in cs)}

    # 일치도
    all_g, all_c = [r["gpt"] for r in rows], [r["claude"] for r in rows]
    sample = [r for r in rows if r["id"] in set(key["sample"]) and r["id"] in human_score]
    agree = [r for r in sample if r["gpt"] == r["claude"]]
    kappa = {
        "gptClaude": qwk(all_g, all_c), "gptClaudeAgreeRate": sum(a == b for a, b in zip(all_g, all_c)) / n,
        "humanGpt": qwk([r["human"] for r in sample], [r["gpt"] for r in sample]) if sample else None,
        "humanClaude": qwk([r["human"] for r in sample], [r["claude"] for r in sample]) if sample else None,
        "humanConsensus": qwk([r["human"] for r in agree], [r["gpt"] for r in agree]) if agree else None,
        "sampleScored": len(sample), "consensusInSample": len(agree),
        "consensusScoreCounts": dict(Counter(r["gpt"] for r in agree)),
        "raters": len(raters), "humanTies": dict(ties),
        # 사람끼리 (무작위 30문장, 두 명씩)
        "humanPairs": [
            {"pair": [a, b], "n": len(common), "kappa": qwk([per_rater[a][i] for i in common], [per_rater[b][i] for i in common]),
             # 거의 모두 2점이면 카파가 낮게 나온다 (카파 역설). 같은 점수 비율을 함께 본다
             "agreeRate": sum(per_rater[a][i] == per_rater[b][i] for i in common) / len(common) if common else None}
            for a in range(len(raters)) for b in range(a + 1, len(raters))
            for common in [[i for i in key["sample"] if i in per_rater[a] and i in per_rater[b]]]
        ],
    }

    # 실행 지표 (서류 생성 기록)
    runs = [json.loads(f.read_text()) for run in key["runs"] for f in (RUNS / run).glob("s[0-9]*.json")]
    secs = sorted(r["sec"] for r in runs if r["ok"])
    p95 = secs[min(len(secs) - 1, math.ceil(0.95 * len(secs)) - 1)] if secs else None
    generated = sum(r["generated"] for r in runs if r["ok"])
    kept = sum(r["kept"] for r in runs if r["ok"])

    report = {
        "name": args.name, "documents": len(docs), "aiSentences": n, "pendingHuman": pending,
        "faithfulness": {"k": count[2], "n": len(scored), "ci": wilson(count[2], len(scored))},
        "falsity": {"k": count[0], "n": len(scored), "ci": wilson(count[0], len(scored))},
        "exaggeration": {"k": count[1], "n": len(scored), "ci": wilson(count[1], len(scored))},
        "reflection": {"rewriteWins": refl_final["REWRITE"], "originalWins": refl_final["ORIGINAL"], "equal": refl_final["EQUAL"],
                       "decided": len(refl_done), "compared": len(refl), "pending": len(refl) - len(refl_done),
                       "excluded": len(docs) - len(refl),
                       # 참고 지표 (결과를 본 뒤 추가, 목표 없음)
                       "avgTotals": refl_avg, "rewriteLowerByAnyLlm": refl_worse},
        "companyFabrication": by_status, "kappa": kappa,
        "confusion": {"gptVsClaude": confusion(all_g, all_c),
                      "humanVsGpt": confusion([r["human"] for r in sample], [r["gpt"] for r in sample]),
                      "humanVsClaude": confusion([r["human"] for r in sample], [r["claude"] for r in sample])},
        "generation": {"excluded": generated - kept, "generated": generated, "secMedian": statistics.median(secs) if secs else None,
                       "secP95": p95, "secMax": secs[-1] if secs else None},
        "sentences": rows, "reflectionDocs": refl,
    }
    (base / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=1) + "\n")

    def fmt_k(v):
        return "-" if v is None else f"{v:.2f}"

    md = [
        f"# 서류 평가 집계 ({args.name})", "",
        f"서류 {len(docs)}건, AI 문장 {n}개. 사람 판정을 기다리는 문장 {pending}개, 반영도 {len(refl) - len(refl_done)}건.", "",
        "| 지표 | 결과 | 95% 구간 | 목표 |", "| --- | --- | --- | --- |",
        f"| 근거 충실도 (2점) | {pct(count[2], len(scored))} | {ci(count[2], len(scored))} | 95% 이상 |",
        f"| 허위율 (0점) | {pct(count[0], len(scored))} | {ci(count[0], len(scored))} | 1% 이하 |",
        f"| 과장률 (1점, 참고) | {pct(count[1], len(scored))} | {ci(count[1], len(scored))} | - |",
        f"| 공고 반영도 (재작성 우세) | {pct(refl_final['REWRITE'], len(refl_done))} | {ci(refl_final['REWRITE'], len(refl_done))} | 70% 이상 |",
        f"| 기업 정보 조작 | {sum(v['fabricated'] for v in by_status.values())}건 (확인 {sum(v['checked'] for v in by_status.values())}/{len(company)}문장) | - | 0건 |",
        f"| 응답 시간 p95 | {p95}초 (중간값 {report['generation']['secMedian']}초) | - | 60초 이하 |", "",
        f"반영도: 재작성 우세 {refl_final['REWRITE']}, 원본 우세 {refl_final['ORIGINAL']}, 동등 {refl_final['EQUAL']}. 비교 제외 {len(docs) - len(refl)}건 (원본 자기소개서가 짧음).",
        "",
        "반영도 참고 (결과를 본 뒤 추가한 지표라 목표가 없다. 8점 만점 평균, 재작성 / 원본):",
        *(f"- {label}: {refl_avg[name][0]} / {refl_avg[name][1]} (차이 {refl_avg[name][0] - refl_avg[name][1]:+.1f})"
          for name, label in (("gpt", "GPT"), ("claude", "Claude")) if refl_avg[name]),
        f"- 어느 LLM이든 재작성 점수가 원본보다 낮은 서류: {refl_worse}/{len(refl)}", "",
        "## 기업 정보 상태별", "", "| 상태 | 기업 관련 문장 | 사람 확인 | 조작 | LLM 이 조작으로 표시 |", "| --- | --- | --- | --- | --- |",
        *[f"| {st} | {v['companySentences']} | {v['checked']} | {v['fabricated']} | {v['llmFlagged']} |" for st, v in by_status.items()], "",
        "## 채점 일치도 (이차 가중 카파)", "",
        f"- GPT 와 Claude (전체 {n}문장): {fmt_k(kappa['gptClaude'])}, 같은 점수 비율 {kappa['gptClaudeAgreeRate']:.1%}",
        f"- 사람과 GPT, 사람과 Claude (무작위 {kappa['sampleScored']}문장): {fmt_k(kappa['humanGpt'])}, {fmt_k(kappa['humanClaude'])}",
        f"- 사람과 LLM 합의 ({kappa['consensusInSample']}문장, 점수별 {kappa['consensusScoreCounts']}): {fmt_k(kappa['humanConsensus'])}",
        f"- 사람 {len(raters)}명. 사람끼리 (무작위 문장, 두 명씩): "
        + (", ".join(f"{p['pair'][0] + 1}번과 {p['pair'][1] + 1}번 {fmt_k(p['kappa'])}, 같은 점수 비율 {pct(round(p['agreeRate'] * p['n']), p['n']) if p['n'] else '-'} ({p['n']}문장)" for p in kappa["humanPairs"]) or "-"),
        f"- 사람끼리 다수가 없던 항목 (보수적으로 정함): {dict(ties) or '없음'}", "",
        "GPT 와 Claude 점수표 (행 GPT 0, 1, 2점 / 열 Claude 0, 1, 2점): " + json.dumps(report["confusion"]["gptVsClaude"]), "",
        f"서류 생성: 제외 문장 {generated - kept}/{generated}, 응답 시간 최대 {report['generation']['secMax']}초.",
    ]
    (base / "report.md").write_text("\n".join(md) + "\n")
    print("\n".join(md))


if __name__ == "__main__":
    main()
