# 주인: AI-A
"""생성 공고(정답 · 함정 91건) × 평가용 수료생 20명 전체 쌍의 루브릭 점수.

실행: python ai/eval/postings/cross_label.py
출력: ai/eval/gold/pair_labels.csv
- score: 2 적합 · 1 부분 · 0 부적합 (rubric.py)
- origin: TARGET 은 그 수료생을 위해 만든 공고, RULE 은 다른 수료생을 위해 만든 공고를 규칙으로 판정한 것
- target_label: 그 공고를 만든 목적 (GOLD · TRAP)

한 수료생을 위해 만든 공고가 다른 수료생에게도 맞을 수 있다 (예: 백엔드 수료생끼리).
자동 지표가 맞는 추천을 틀렸다고 세지 않도록, 생성 공고는 모든 쌍을 라벨링한다.
실제 공고 · 일반 공고는 구조화된 명세가 없어 여기서 다루지 않는다 (별도 판정 필요).
"""

import csv
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[1]))
from rubric import score, violations  # noqa: E402

HERE = Path(__file__).parent
OUT = HERE.parent / "gold" / "pair_labels.csv"


def main() -> None:
    spec = json.loads((HERE / "spec.json").read_text(encoding="utf-8"))["postings"]
    students = []
    for path in sorted((HERE.parent / "personas" / "students").glob("batch_*.json")):
        students.extend(json.loads(path.read_text(encoding="utf-8")))

    rows, counts = [], {0: 0, 1: 0, 2: 0}
    for s in sorted(students, key=lambda x: x["id"]):
        for p in spec:
            posting = {"family": p["family"], "career": p["career"], "education": p["edu"],
                       "location": p["loc"], "required": p["required"]}
            sc = score(s, posting)
            counts[sc] += 1
            rows.append([s["id"], p["id"], sc, "|".join(violations(s, posting)),
                         "TARGET" if p["sid"] == s["id"] else "RULE", p["label"], p.get("type", "")])

    OUT.parent.mkdir(exist_ok=True)
    with OUT.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["student_id", "job_posting_id", "score", "violations", "origin", "target_label", "trap_type"])
        w.writerows(rows)
    print(f"{len(rows)}쌍 → {OUT}  (적합 {counts[2]} · 부분 {counts[1]} · 부적합 {counts[0]})")


if __name__ == "__main__":
    main()
