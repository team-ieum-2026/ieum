# 주인: AI-A
"""평가용 수료생 20명 × 공고 379건의 루브릭 점수 (생성 91건, 실제 138건, 일반 150건).

실행: python ai/eval/postings/cross_label.py
출력: ai/eval/gold/pair_labels.csv, ai/eval/postings/out/vote_report.json
- score: 2 적합 · 1 부분 · 0 부적합 (rubric.py)
- origin: TARGET 은 그 수료생을 위해 만든 공고, RULE 은 다른 수료생을 위해 만든 공고를 규칙으로 판정한 것,
          REAL · GENERAL 은 실제 공고 · 일반 공고를 모델 3개 투표로 판정한 것
- target_label: 그 공고를 만든 목적 (GOLD · TRAP)

생성 공고는 명세(spec.json)로 판정한다. 모델 3개의 투표 결과와도 비교해, 투표 방식이 정답을 얼마나 맞히는지 잰다.
실제 공고와 일반 공고는 명세가 없다. 모델 3개가 본문에서 뽑은 조건(extract_conditions.py)으로 각각 점수를 매기고,
쌍마다 세 점수의 중앙값을 라벨로 쓴다. 사람이 고친 공고(external_fixes.json)는 사람 값을 쓴다.
2점인지 아닌지가 모델끼리 갈린 공고는 vote_report.json 에 모아 사람이 검수한다.
"""

import csv
import json
import statistics
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[1]))
sys.path.insert(0, str(Path(__file__).parent))
from external import fixed_posting, load_by_posting, load_fixes  # noqa: E402
from rubric import score, violations  # noqa: E402

HERE = Path(__file__).parent
OUT = HERE.parent / "gold" / "pair_labels.csv"
REPORT = HERE / "out" / "vote_report.json"


def vote(student: dict, models: dict[str, dict]) -> tuple[int, list[str], dict[str, int]]:
    """모델별 점수의 중앙값과, 중앙값을 낸 모델의 어긋난 조건"""
    scores = {m: score(student, p) for m, p in models.items()}
    median = int(statistics.median_low(scores.values()))
    chosen = next(m for m, s in scores.items() if s == median)
    return median, violations(student, models[chosen]), scores


def main() -> None:
    spec = json.loads((HERE / "spec.json").read_text(encoding="utf-8"))["postings"]
    students = []
    for path in sorted((HERE.parent / "personas" / "students").glob("batch_*.json")):
        students.extend(json.loads(path.read_text(encoding="utf-8")))
    students.sort(key=lambda x: x["id"])
    by_posting = load_by_posting()
    fixes = load_fixes()

    rows, counts = [], {0: 0, 1: 0, 2: 0}
    gen_check = {"pairs": 0, "same": 0, "spec2_vote2": 0, "spec2_vote_not2": 0, "spec_not2_vote2": 0}
    gen_disagree, ext_disagree = {}, {}

    # 생성 공고: 명세로 판정하고, 모델 투표와 비교만 한다
    for s in students:
        for p in spec:
            posting = {"family": p["family"], "career": p["career"], "education": p["edu"],
                       "location": p["loc"], "required": p["required"]}
            sc = score(s, posting)
            counts[sc] += 1
            rows.append([s["id"], p["id"], sc, "|".join(violations(s, posting)),
                         "TARGET" if p["sid"] == s["id"] else "RULE", p["label"], p.get("type", "")])
            entry = by_posting.get(p["id"])
            if not entry:
                continue
            voted, _, _ = vote(s, entry["models"])
            gen_check["pairs"] += 1
            gen_check["same"] += voted == sc
            if sc == 2:
                gen_check["spec2_vote2" if voted == 2 else "spec2_vote_not2"] += 1
            elif voted == 2:
                gen_check["spec_not2_vote2"] += 1
            if (sc == 2) != (voted == 2):
                gen_disagree.setdefault(p["id"], []).append({"student": s["id"], "spec": sc, "vote": voted})

    # 실제 공고, 일반 공고: 모델 3개 투표 (사람이 고친 공고는 사람 값)
    for pid, entry in sorted(by_posting.items()):
        if entry["source"] == "GENERATED":
            continue
        fixed = fixed_posting(pid, entry, fixes)
        for s in students:
            if fixed:
                sc, viol = score(s, fixed), violations(s, fixed)
            else:
                sc, viol, scores = vote(s, entry["models"])
                if len({v == 2 for v in scores.values()}) > 1:  # 2점인지가 모델끼리 갈림
                    ext_disagree.setdefault(pid, []).append({"student": s["id"], "scores": scores})
            counts[sc] += 1
            rows.append([s["id"], pid, sc, "|".join(viol), entry["source"], "", ""])

    OUT.parent.mkdir(exist_ok=True)
    with OUT.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["student_id", "job_posting_id", "score", "violations", "origin", "target_label", "trap_type"])
        w.writerows(rows)

    models = sorted({m for e in by_posting.values() for m in e["models"]})
    report = {
        "models": models,
        "generatedCheck": gen_check,  # 생성 공고에서 투표가 명세 정답을 맞힌 정도
        "generatedDisagree": {str(k): v for k, v in sorted(gen_disagree.items())},
        "externalDisagree": {str(k): v for k, v in sorted(ext_disagree.items())},  # 사람 검수 대상
        "fixed": sorted(fixes),
    }
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(f"{len(rows)}쌍 → {OUT}  (적합 {counts[2]} · 부분 {counts[1]} · 부적합 {counts[0]})")
    print(f"모델 {models}")
    if gen_check["pairs"]:
        print(f"생성 공고 투표 정확도: 쌍 {gen_check['same']}/{gen_check['pairs']} 일치, "
              f"명세 2점 중 투표 2점 {gen_check['spec2_vote2']}/{gen_check['spec2_vote2'] + gen_check['spec2_vote_not2']}, "
              f"명세 2점 아닌데 투표 2점 {gen_check['spec_not2_vote2']} · 어긋난 공고 {len(gen_disagree)}건")
    print(f"실제 · 일반 공고에서 2점 여부가 모델끼리 갈린 공고 {len(ext_disagree)}건 (사람 검수) → {REPORT}")


if __name__ == "__main__":
    main()
