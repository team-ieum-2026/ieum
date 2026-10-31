# 주인: AI-A (작성 AI-B)
"""매칭 추천 채점: 실행 결과(ai/eval/runs/matching/<날짜>/student_<id>.json) × 정답표 → Precision@3 · 명백 부적합률.

실행:
    python ai/eval/score_matching.py ai/eval/runs/matching/<날짜> --labels <pair_labels.csv> --split dev
    python ai/eval/score_matching.py ai/eval/runs/matching/<날짜> --labels <pair_labels.csv> --split test \
        [--human-labels <사람 판정.csv>]

- 정답표 · 사람 판정 칸: student_id, job_posting_id, score (0/1/2). 경로는 인자로만 받는다. 이 스크립트는 정답표 행을 출력하지 않는다.
- --human-labels 없음 → 1차 수치: 정답표에 없는 카드는 "채점 불가"로 분모에서 빼고 개수만 따로 센다.
  --human-labels 있음 → 최종 수치: 정답표에 없는 카드를 사람 판정 점수로 채점해 분모에 넣는다. 같은 쌍이 둘 다 있으면 정답표 우선.
- Precision@3 = 2점 카드 / 채점된 카드 · 명백 부적합률 = 0점 카드 / 채점된 카드. 비율마다 95% 신뢰구간(Wilson).
- 맞는 공고가 없는 수료생(NO_MATCH)은 2점이 아닌 카드가 1장이라도 나오면 실패 (METRICS.md). 카드 0장이면 통과.
  test 는 통과 수만, dev 는 수료생별 통과 · 실패까지 출력한다.
  점수를 모르는 카드(채점 불가)는 2점으로 확인되지 않았으므로 실패로 본다.
- dev: 수료생별 Top-3 · 카드별 점수 · 틀린 이유 후보(지역 시 · 도 · 학력 · 경력 칸, 그 외는 직무 · 기술)까지 출력.
- test: 위 세 숫자만 출력. 수료생별 결과는 출력하지 않는다.
"""

import argparse
import csv
import json
import math
from collections import Counter
from collections.abc import Callable
from pathlib import Path

from rubric import POSTING_EDUCATION, max_degree, preferred_regions

HERE = Path(__file__).parent
# 맞는 공고가 없는 수료생. 2점이 아닌 카드가 하나라도 나오면 실패 (METRICS.md · AI-A 확정)
NO_MATCH = {"test": [10, 17], "dev": [13]}


def load_split() -> dict[str, list[int]]:
    data = json.loads((HERE / "split.json").read_text(encoding="utf-8"))
    return {"dev": data["dev"], "test": data["test"]}


def load_labels(path: Path, students: set[int]) -> dict[tuple[int, int], int]:
    """{ (student_id, job_posting_id): score }. 이 묶음 수료생의 행만 메모리에 둔다."""
    labels = {}
    with path.open(encoding="utf-8-sig", newline="") as f:
        for row in csv.DictReader(f):
            sid = int(row["student_id"])
            if sid in students:
                labels[(sid, int(row["job_posting_id"]))] = int(row["score"])
    return labels


def load_runs(runs_dir: Path, students: set[int]) -> dict[int, dict]:
    runs = {}
    for path in sorted(runs_dir.glob("student_*.json")):
        run = json.loads(path.read_text(encoding="utf-8"))
        if int(run["studentId"]) in students:
            runs[int(run["studentId"])] = run
    return runs


def wilson(hit: int, n: int, z: float = 1.96) -> str:
    if n == 0:
        return "표본 없음"
    p = hit / n
    center = (p + z * z / (2 * n)) / (1 + z * z / n)
    half = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / (1 + z * z / n)
    return f"{hit}/{n} = {p:.0%} (95% 구간 {max(0, center - half):.0%}~{min(1, center + half):.0%})"


def condition_flags(student: dict, card: dict) -> list[str]:
    """루브릭 기준으로 결과 파일만 보고 알 수 있는 불충족 조건 (직무 · 필수 기술은 공고 명세가 필요해 여기서 못 본다)"""
    flags = []
    regions = preferred_regions({"preferred_region": student.get("preferredRegion")})
    if card.get("sido") and regions and card["sido"] not in regions:
        flags.append("지역(시·도)")
    if POSTING_EDUCATION.get(card.get("education") or "", 0) > max_degree(student.get("education") or []):
        flags.append("학력")
    if card.get("career") == "경력":
        flags.append("경력")
    return flags


def score(runs_dir: Path, labels_path: Path, split_name: str, *, members: list[int] | None = None,
          out: Callable[[str], None] = print, human_labels_path: Path | None = None) -> dict:
    students = set(members if members is not None else load_split()[split_name])
    runs = load_runs(runs_dir, students)
    gold = load_labels(labels_path, students)
    human = load_labels(human_labels_path, students) if human_labels_path else {}
    labels = {**human, **gold}  # 같은 쌍이면 정답표 우선
    mode = "최종 수치 (정답표 + 사람 판정)" if human_labels_path else "1차 수치 (정답표만, 채점 불가는 분모에서 뺌)"

    counts: Counter = Counter()
    flag_counts: Counter = Counter()
    for sid, run in runs.items():
        for card in run["items"]:
            key = (sid, card["jobPostingId"])
            label = labels.get(key)
            if label is None:
                counts["unscored"] += 1
                continue
            counts["scored"] += 1
            counts["human"] += key not in gold
            counts[f"score{label}"] += 1
            if label < 2:
                for flag in condition_flags(run["student"], card):
                    flag_counts[flag] += 1

    # 2점이 아닌 카드(점수를 모르는 카드 포함) 수. 0이면 통과
    no_match = {sid: sum(labels.get((sid, c["jobPostingId"])) != 2 for c in runs[sid]["items"])
                for sid in NO_MATCH.get(split_name, []) if sid in runs}
    result = {
        "split": split_name,
        "mode": "FINAL" if human_labels_path else "FIRST",
        "students": len(runs),
        "missingRuns": sorted(students - set(runs)),
        "cards": counts["scored"] + counts["unscored"],
        "scored": counts["scored"],
        "unscored": counts["unscored"],
        "humanScored": counts["human"],
        "score2": counts["score2"],
        "score1": counts["score1"],
        "score0": counts["score0"],
        "noMatchPassed": {sid: n == 0 for sid, n in no_match.items()},
    }

    out(f"[{split_name}] {mode}")
    out(f"  수료생 {len(runs)}명 · 카드 {result['cards']}장 (채점 {result['scored']}"
        + (f", 그중 사람 판정 {counts['human']}" if human_labels_path else "") + f" · 채점 불가 {result['unscored']})")
    if result["missingRuns"]:
        out(f"  결과 파일 없는 수료생 {len(result['missingRuns'])}명")
    out(f"  Precision@3: {wilson(counts['score2'], counts['scored'])}")
    out(f"  명백 부적합률(0점): {wilson(counts['score0'], counts['scored'])}")
    if no_match:
        passed_count = sum(result["noMatchPassed"].values())
        out(f"  맞는 공고 없는 수료생 통과 {passed_count}/{len(no_match)}명")
    if split_name == "dev":
        for sid, passed in result["noMatchPassed"].items():
            out(f"    {sid}번: {'통과' if passed else '실패'} (카드 {len(runs[sid]['items'])}장 · 2점 아닌 카드 {no_match[sid]}장)")

    if split_name == "dev":
        out(f"  2점 아닌 카드의 불충족 조건(결과 파일 기준): {dict(flag_counts) or '없음'}")
        for sid in sorted(runs):
            print_student(runs[sid], labels, set(gold), out)
    return result


DEGREE_NAME = {"HIGH_SCHOOL": "고졸", "ASSOCIATE": "전문학사", "BACHELOR": "학사", "MASTER": "석사", "DOCTOR": "박사"}


def degree_text(student: dict) -> str:
    degrees = [DEGREE_NAME.get(e.get("degree"), e.get("degree")) for e in student.get("education") or []]
    return "/".join(d for d in degrees if d) or "없음"


def print_student(run: dict, labels: dict[tuple[int, int], int], gold_keys: set[tuple[int, int]],
                  out: Callable[[str], None]) -> None:
    s = run["student"]
    out(f"\n  [{run['studentId']}] {s.get('desiredJob')} · 희망 {s.get('preferredRegion')} · 학력 {degree_text(s)} · {run.get('path')}"
        + (f" ({run.get('emptyReason') or run.get('fallbackReason')})" if run.get("path") != "OK" else "")
        + (" · 카드 없음" if not run["items"] else ""))
    for card in run["items"]:
        label = labels.get((run["studentId"], card["jobPostingId"]))
        flags = condition_flags(s, card)
        if label is None:
            why = "채점 불가 (정답표에 없음)"
        elif label == 2:
            why = ""
        else:
            why = ("틀린 이유 후보: " + (", ".join(flags) if flags else "직무 · 필수 기술 (지역 · 학력 · 경력 칸은 충족)")
                   + f" [공고 학력 {card.get('education')} · 시·도 {card.get('sido')} / 수료생 학력 {degree_text(s)}"
                   f" · 희망 {s.get('preferredRegion')}]")
        human = label is not None and (run["studentId"], card["jobPostingId"]) not in gold_keys
        shown = "-" if label is None else f"{label}{'·사람' if human else ''}"
        out(f"     {card['rank']}. [{shown}] {card['jobPostingId']} {card.get('title')} · {card.get('sido')} · "
            f"{card.get('career')} · {card.get('education')} · {card.get('sourceFile')}" + (f"  → {why}" if why else ""))


def main() -> None:
    parser = argparse.ArgumentParser(prog="score_matching.py")
    parser.add_argument("runs_dir", type=Path)
    parser.add_argument("--labels", type=Path, required=True)
    parser.add_argument("--split", choices=["dev", "test"], required=True)
    parser.add_argument("--human-labels", type=Path, help="정답표에 없는 카드의 사람 판정 (주면 최종 수치)")
    args = parser.parse_args()
    score(args.runs_dir, args.labels, args.split, human_labels_path=args.human_labels)


if __name__ == "__main__":
    main()
