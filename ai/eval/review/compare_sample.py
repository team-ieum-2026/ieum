# 주인: AI-A
"""검수 결과와 규칙 판정(sample_key.csv) 비교.

실행 (첫 인자는 dev 또는 test. 그 묶음의 sample_key_{dev|test}.csv 와 비교한다)
- 검수자 1명: python ai/eval/review/compare_sample.py dev answers_dev_소민.csv
- 검수자 2명: python ai/eval/review/compare_sample.py dev answers_dev_소민.csv answers_dev_영동.csv

검수자가 2명이면 먼저 사람끼리 일치도(Cohen's κ)를 본다. κ 가 0.8 미만이면 규칙이 아니라 루브릭 문구가 모호한 것이므로,
갈린 쌍을 해석 사례집(LABELING.md)에 정리하고 다시 채점한다. 사람끼리 갈린 쌍은 규칙과의 비교에서 뺀다.

규칙과의 비교는 2점(적합) 판정을 중심으로 본다 (Precision@3 채점을 좌우하는 것이 2점이므로).
- 정밀도: 규칙이 2점 준 쌍 중 사람도 2점 준 비율 → 낮으면 정답표가 맞지 않는 공고를 정답으로 셈
- 재현율: 사람이 2점 준 쌍 중 규칙도 2점 준 비율 → 낮으면 맞는 추천을 틀렸다고 셈
수치마다 95% 신뢰구간(Wilson)을 붙인다. 표본이 작으면 구간이 넓다.
"""

import csv
import math
import sys
from collections import Counter
from pathlib import Path

HERE = Path(__file__).parent


def read_rows(path: Path) -> list[dict]:
    """엑셀에서 저장한 파일도 읽는다: UTF-8(BOM 있음 · 없음), 한국어 Windows 기본 인코딩(CP949)."""
    for encoding in ("utf-8-sig", "cp949"):
        try:
            with path.open(encoding=encoding, newline="") as f:
                return list(csv.DictReader(f))
        except UnicodeDecodeError:
            continue
    raise ValueError(f"{path} 인코딩을 읽을 수 없음. 엑셀에서 'CSV UTF-8' 로 다시 저장해 주세요")


def read_scores(path: Path) -> dict[str, tuple[int, str]]:
    return {r["no"]: (int(r["my_score"]), r.get("memo", "")) for r in read_rows(path) if r["my_score"].strip()}


def wilson(hit: int, n: int, z: float = 1.96) -> str:
    if n == 0:
        return "표본 없음"
    p = hit / n
    center = (p + z * z / (2 * n)) / (1 + z * z / n)
    half = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / (1 + z * z / n)
    return f"{hit}/{n} = {p:.0%} (95% 구간 {max(0, center - half):.0%}~{min(1, center + half):.0%})"


def kappa(a: list, b: list) -> float:
    n = len(a)
    observed = sum(x == y for x, y in zip(a, b)) / n
    ca, cb = Counter(a), Counter(b)
    expected = sum(ca[k] * cb[k] for k in set(a) | set(b)) / (n * n)
    return 1.0 if expected == 1 else (observed - expected) / (1 - expected)


def main() -> None:
    if len(sys.argv) < 2 or sys.argv[1] not in ("dev", "test"):
        sys.exit("사용법: compare_sample.py dev|test 검수자1.csv [검수자2.csv]")
    part = sys.argv[1]
    paths = [Path(p) for p in sys.argv[2:]] or [HERE / f"sample_answers_{part}.csv"]
    raters = [read_scores(p) for p in paths]
    key = {r["no"]: r for r in read_rows(HERE / f"sample_key_{part}.csv")}

    common = sorted(set.intersection(*(set(r) for r in raters)), key=int)
    if not common:
        print("채점된 쌍이 없습니다")
        return

    if len(raters) == 2:
        a = [raters[0][n][0] for n in common]
        b = [raters[1][n][0] for n in common]
        print(f"사람끼리 ({len(common)}쌍): 점수 κ = {kappa(a, b):.2f} · 2점 여부 κ = "
              f"{kappa([x == 2 for x in a], [y == 2 for y in b]):.2f}  (0.8 이상이면 루브릭이 충분히 명확)")
        split = [n for n in common if raters[0][n][0] != raters[1][n][0]]
        for n in split:
            print(f"  갈림 {n}. {key[n]['stratum']}: {raters[0][n][0]} vs {raters[1][n][0]}  "
                  f"메모: {raters[0][n][1]} / {raters[1][n][1]}")
        truth = {n: raters[0][n][0] for n in common if n not in split}
        print(f"  → 규칙 비교는 사람끼리 같은 {len(truth)}쌍으로 한다\n")
    else:
        truth = {n: raters[0][n][0] for n in common}

    rule = {n: int(key[n]["rule_score"]) for n in truth}
    rule2 = [n for n in truth if rule[n] == 2]
    human2 = [n for n in truth if truth[n] == 2]
    print(f"규칙 vs 사람 ({len(truth)}쌍)")
    print(f"  2점 정밀도: {wilson(sum(truth[n] == 2 for n in rule2), len(rule2))}")
    print(f"  2점 재현율: {wilson(sum(rule[n] == 2 for n in human2), len(human2))}")
    print(f"  점수 완전 일치: {wilson(sum(truth[n] == rule[n] for n in truth), len(truth))}")

    by = Counter(key[n]["stratum"] for n in truth)
    same = Counter(key[n]["stratum"] for n in truth if truth[n] == rule[n])
    print("\n유형별 일치")
    for stratum in by:
        print(f"  {stratum}: {same[stratum]}/{by[stratum]}")

    diffs = [n for n in truth if truth[n] != rule[n]]
    if diffs:
        print("\n규칙과 다른 쌍 (번호 · 유형 · 사람 → 규칙 · 규칙이 본 어긋난 조건 · 메모)")
        for n in diffs:
            memos = " / ".join(r[n][1] for r in raters if r[n][1])
            print(f"  {n}. {key[n]['stratum']}: {truth[n]} → {rule[n]}  [{key[n]['violations'] or '없음'}]  {memos}")


if __name__ == "__main__":
    main()
