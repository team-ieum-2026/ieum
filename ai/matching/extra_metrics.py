# 주인: AI-B
"""매칭 평가 보조 지표 (METRICS.md 1장 "함께 보고할 항목"): 칸 필터 기준선 대비 차이 · 함정 진입률.

실행 (ai 폴더에서):
    python -m matching.extra_metrics ai/eval/runs/matching/<날짜> --labels ai/eval/gold/pair_labels.csv --split dev
    python -m matching.extra_metrics <폴더> --labels <pair_labels.csv> --split dev --pool-index ai/data/index/eval_real138.json

- 칸 필터 기준선: 직무 · 지역 · 경력 · 학력 칸만 맞추고 무작위로 골랐을 때 2점일 비율.
  맞는 공고가 있는 수료생(NO_MATCH 제외)의 쌍 중 violations 에 JOB · CAREER · EDU · REGION 이 없는 쌍(SKILL 위반은 허용)에서 2점 비율.
  수료생 평균(student: 수료생별 비율의 평균)이 METRICS.md 의 dev 62% · test 68% 와 같은 정의다 (11/3 재현 확인).
  쌍 단위(pair)는 참고로 같이 낸다. --pool-index 를 주면 그 색인에 있는 공고 쌍만 본다.
- 기준선 대비 차이 = Precision@3 − 기준선 (백분율 점).
- 함정 진입률: 대상 함정 = origin 이 TARGET 이고 target_label 이 TRAP 인 쌍. 수료생마다 그 공고가 Top-3 에 든 비율, trap_type 별 합계.
- 정답표는 코드로만 읽고 행을 출력하지 않는다. test 는 합계만 출력한다 (수료생별 출력은 막는다).
"""

import argparse
import csv
import json
from dataclasses import dataclass
from pathlib import Path

AI_ROOT = Path(__file__).resolve().parent.parent
SPLIT_FILE = AI_ROOT / "eval" / "split.json"
# 맞는 공고가 없는 수료생 (ai/eval/score_matching.py NO_MATCH 와 같게)
NO_MATCH = {"test": [10], "dev": [13]}
FIELD_CONDITIONS = {"JOB", "CAREER", "EDU", "REGION"}
# 함정 유형 출력 순서: 칸 필터로 걸러지지 않는 SKILL · LOOKALIKE 를 앞에 (METRICS.md)
TRAP_ORDER = ["SKILL", "LOOKALIKE", "CAREER", "REGION", "EDU"]


@dataclass(frozen=True)
class Label:
    score: int
    violations: frozenset[str]
    origin: str
    target_label: str
    trap_type: str


def load_split(path: Path = SPLIT_FILE) -> dict[str, list[int]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    return {"dev": data["dev"], "test": data["test"]}


def load_labels(path: Path, students: set[int]) -> dict[tuple[int, int], Label]:
    """students 의 쌍만 읽는다. 다른 수료생 행은 첫 칸만 보고 버린다 (파싱 · 저장하지 않음)"""
    labels: dict[tuple[int, int], Label] = {}
    with path.open(encoding="utf-8-sig", newline="") as f:
        header = next(csv.reader([f.readline()]))
        col = {name: header.index(name) for name in header}
        for line in f:
            first = line.split(",", 1)[0].strip()
            if not first.isdigit() or int(first) not in students:
                continue
            c = next(csv.reader([line]))
            get = lambda name: c[col[name]].strip() if name in col and col[name] < len(c) else ""  # noqa: E731
            labels[(int(get("student_id")), int(get("job_posting_id")))] = Label(
                score=int(get("score")),
                violations=frozenset(v for v in get("violations").split("|") if v),
                origin=get("origin"),
                target_label=get("target_label"),
                trap_type=get("trap_type"),
            )
    return labels


def category_values(labels: dict[tuple[int, int], Label]) -> dict[str, list[str]]:
    """범주 칸의 distinct 값 (행은 내지 않는다)"""
    return {
        "origin": sorted({lb.origin for lb in labels.values()}),
        "target_label": sorted({lb.target_label for lb in labels.values()}),
        "trap_type": sorted({lb.trap_type for lb in labels.values()}),
        "violations": sorted({v for lb in labels.values() for v in lb.violations}),
    }


def field_baseline(labels: dict[tuple[int, int], Label], students: list[int], *, allow_skill: bool = True,
                   pool: set[int] | None = None) -> dict:
    """칸 필터 기준선. 반환: pair(쌍 단위) · student(수료생 평균) · per_student {sid: (2점 수, 칸 통과 쌍 수)}"""
    blocked = FIELD_CONDITIONS if allow_skill else FIELD_CONDITIONS | {"SKILL"}
    per: dict[int, tuple[int, int]] = {}
    for sid in students:
        passed = [lb for (s, j), lb in labels.items() if s == sid and (pool is None or j in pool) and not lb.violations & blocked]
        if passed:
            per[sid] = (sum(lb.score == 2 for lb in passed), len(passed))
    hits, total = sum(h for h, _ in per.values()), sum(n for _, n in per.values())
    return {
        "pair": hits / total if total else None,
        "student": sum(h / n for h, n in per.values()) / len(per) if per else None,
        "per_student": per,
    }


def load_cards(runs_dir: Path, students: list[int]) -> dict[int, list[int]]:
    """실행 결과의 Top-3 카드 공고 id. 결과 파일이 없는 수료생은 뺀다"""
    cards = {}
    for sid in students:
        path = runs_dir / f"student_{sid}.json"
        if path.exists():
            cards[sid] = [int(it["jobPostingId"]) for it in json.loads(path.read_text(encoding="utf-8"))["items"]]
    return cards


def precision_at_3(cards: dict[int, list[int]], labels: dict[tuple[int, int], Label]) -> tuple[int, int]:
    """(2점 카드 수, 채점된 카드 수). 정답표에 없는 카드는 분모에서 뺀다 (score_matching 1차 수치와 같게)"""
    scored = [labels[(sid, j)].score for sid, ids in cards.items() for j in ids if (sid, j) in labels]
    return sum(s == 2 for s in scored), len(scored)


def trap_entry(cards: dict[int, list[int]], labels: dict[tuple[int, int], Label], students: list[int],
               pool: set[int] | None = None) -> dict:
    """대상 함정이 Top-3 에 든 비율. 반환: per_student {sid: (든 수, 대상 함정 수)} · by_type {유형: (든 수, 대상 수)}"""
    per: dict[int, tuple[int, int]] = {}
    by_type: dict[str, list[int]] = {}
    for sid in students:
        traps = [(j, lb.trap_type) for (s, j), lb in labels.items()
                 if s == sid and lb.origin == "TARGET" and lb.target_label == "TRAP" and (pool is None or j in pool)]
        if not traps:
            continue
        shown = set(cards.get(sid, []))
        per[sid] = (sum(j in shown for j, _ in traps), len(traps))
        for j, kind in traps:
            t = by_type.setdefault(kind, [0, 0])
            t[0] += j in shown
            t[1] += 1
    return {"per_student": per, "by_type": {k: tuple(v) for k, v in by_type.items()}}


def type_order(kinds) -> list[str]:
    return [k for k in TRAP_ORDER if k in kinds] + sorted(k for k in kinds if k not in TRAP_ORDER)


def pct(hit: int, n: int) -> str:
    return f"{hit}/{n} = {hit / n:.0%}" if n else "0/0"


def report(runs_dir: Path, labels_path: Path, split_name: str, *, pool_index: Path | None = None,
           per_student: bool | None = None, split: dict[str, list[int]] | None = None,
           no_match: dict[str, list[int]] | None = None) -> list[str]:
    """출력할 줄 목록. test 는 수료생별 줄을 낼 수 없다 (per_student=True 면 ValueError). split · no_match 는 테스트용"""
    if per_student is None:
        per_student = split_name == "dev"
    if split_name == "test" and per_student:
        raise ValueError("test 수료생별 결과는 출력하지 않는다")
    students = (split or load_split())[split_name]
    labels = load_labels(labels_path, set(students))
    pool = {int(k) for k in json.loads(pool_index.read_text(encoding="utf-8"))} if pool_index else None
    scored = [s for s in students if s not in (no_match or NO_MATCH)[split_name]]
    cards = load_cards(runs_dir, students)

    lines = [f"[{split_name}] {runs_dir.name} · 수료생 {len(cards)}명" + (f" · 기준선 풀 {pool_index.name}({len(pool)}건)" if pool else "")]
    hit, n = precision_at_3(cards, labels)
    p3 = hit / n if n else None
    lines.append(f"  Precision@3: {pct(hit, n)}")
    # SKILL 위반까지 빼면 남는 쌍은 위반이 없어 늘 2점(100%)이라 기준선으로 쓰지 않는다
    all_pool = field_baseline(labels, scored)
    inside = field_baseline(labels, scored, pool=pool) if pool else None
    for kind, label in (("student", "수료생 평균 · METRICS 값과 같은 정의"), ("pair", "쌍 단위 · 참고")):
        line = f"  칸 필터 기준선({label}): 전체 풀 {all_pool[kind]:.1%}"
        if p3 is not None:
            line += f" (차이 {(p3 - all_pool[kind]) * 100:+.1f}점)"
        if inside and inside[kind] is not None:
            line += f" · 이 풀 안 {inside[kind]:.1%}" + (f" (차이 {(p3 - inside[kind]) * 100:+.1f}점)" if p3 is not None else "")
        lines.append(line)
    traps = trap_entry(cards, labels, students, pool=pool)
    total_hit = sum(h for h, _ in traps["by_type"].values())
    total_n = sum(m for _, m in traps["by_type"].values())
    lines.append(f"  함정 진입률: {pct(total_hit, total_n)}" + (" (이 풀 안의 함정만)" if pool else ""))
    for kind in type_order(traps["by_type"]):
        lines.append(f"    {kind}: {pct(*traps['by_type'][kind])}")
    if per_student:
        lines.append("  수료생별 (든 함정 / 대상 함정 · 칸 필터 기준선 쌍 단위)")
        base = field_baseline(labels, scored, pool=pool)["per_student"]
        for sid in students:
            t = traps["per_student"].get(sid)
            b = base.get(sid)
            lines.append(f"    [{sid}] 함정 {pct(*t) if t else '대상 없음'} · 기준선 {pct(*b) if b else '대상 아님'}")
    return lines


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="python -m matching.extra_metrics")
    parser.add_argument("runs_dir", type=Path)
    parser.add_argument("--labels", type=Path, required=True)
    parser.add_argument("--split", choices=["dev", "test"], required=True)
    parser.add_argument("--pool-index", type=Path, help="이 색인에 있는 공고 쌍만으로 기준선 · 함정을 따로 계산")
    args = parser.parse_args(argv)
    print("\n".join(report(args.runs_dir, args.labels, args.split, pool_index=args.pool_index)))


if __name__ == "__main__":
    main()
