# 주인: AI-A
"""매칭 정답표(pair_labels.csv) 사람 검수용 표본 시트.

실행: python ai/eval/review/make_sample.py
출력
- review/sample_sheet.md: 수료생 · 공고 카드 (규칙 판정은 가림, 순서 섞음)
- review/sample_answers.csv: 검수자가 채우는 칸 (my_score · memo). 검수자마다 복사해서 채운다
- review/sample_key.csv: 규칙 판정 (검수가 끝난 뒤에 열기)
비교: python ai/eval/review/compare_sample.py 검수자1.csv [검수자2.csv]

표본: 규칙이 다른 수료생 쌍에 준 2점은 전부 (정밀도 채점을 직접 좌우),
1점 · 0점은 어긋난 조건별로 몇 쌍씩 (2점을 놓친 판정이 없는지 = 재현율).
대상 정답 · 함정(TARGET)은 설계대로인지 check_postings.py 가 이미 검사하므로 넣지 않는다.
"""

import csv
import json
import random
from pathlib import Path

EVAL = Path(__file__).parents[1]
OUT = Path(__file__).parent
SEED = 20261029

# (이름, 조건, 뽑을 수 — None 이면 전부)
def rule(score: str, check=lambda v: True):
    return lambda r: r["origin"] == "RULE" and r["score"] == score and check(r["violations"].split("|"))


STRATA = [
    ("규칙 2점 (전수)", rule("2"), None),
    ("규칙 1점 · 경력", rule("1", lambda v: v == ["CAREER"]), 4),
    ("규칙 1점 · 학력", rule("1", lambda v: v == ["EDU"]), 4),
    ("규칙 1점 · 지역", rule("1", lambda v: v == ["REGION"]), 4),
    ("규칙 1점 · 기술", rule("1", lambda v: v == ["SKILL"]), 4),
    ("규칙 0점 · 같은 직무군", rule("0", lambda v: "JOB" not in v), 4),
    ("규칙 0점 · 직무 다름", rule("0", lambda v: "JOB" in v), 2),
]

DEGREE = {"HIGH_SCHOOL": "고졸", "ASSOCIATE": "전문대졸", "BACHELOR": "학사", "MASTER": "석사"}


def main() -> None:
    rng = random.Random(SEED)
    jobs = {j["jobPostingId"]: j for j in
            json.loads((EVAL / "postings/out/eval_postings.json").read_text(encoding="utf-8"))["jobs"]}
    students = {}
    for path in sorted((EVAL / "personas/students").glob("batch_*.json")):
        for s in json.loads(path.read_text(encoding="utf-8")):
            students[s["id"]] = s
    rows = list(csv.DictReader((EVAL / "gold/pair_labels.csv").open(encoding="utf-8")))

    picked = []
    for name, cond, n in STRATA:
        pool = [r for r in rows if cond(r)]
        chosen = pool if n is None or len(pool) <= n else rng.sample(pool, n)
        picked += [(name, r) for r in chosen]
    rng.shuffle(picked)

    md = [
        "# 매칭 정답표 검수 시트",
        "",
        "각 쌍을 읽고 `sample_answers.csv` 의 `my_score` 에 점수를 적어 주세요. **`sample_key.csv` 는 다 적은 뒤에 여세요.**",
        "",
        "엑셀로 작업하면 저장할 때 형식을 **CSV UTF-8 (쉼표로 분리)** 로 골라 주세요. 검수자마다 `sample_answers.csv` 를 복사해(예: `answers_소민.csv`) 각자 채우고, **서로 답을 보지 마세요** (사람끼리 일치도를 잽니다).",
        "",
        "| 점수 | 기준 |",
        "| --- | --- |",
        "| 2 적합 | 이 수료생에게 추천해도 된다: 직무 일치 · 신입/경력무관 · 학력 충족 · 희망 지역 안 · **필수 기술을 모두 갖춤** (다른 표현이어도 같은 기술이면 인정) |",
        "| 1 부분 | 직무는 맞는데 한 가지가 부족하다 (경력 · 학력 · 지역 중 하나, 또는 필수 기술 일부) |",
        "| 0 부적합 | 추천하면 안 된다 (직무가 다르거나 두 가지 이상 부족, 필수 기술 대부분이 없음) |",
        "",
        "우대 조건은 판정에 넣지 않습니다. 규칙이 아니라 **코디네이터라면 이 공고를 추천하겠는가**로 판단해 주세요. 규칙과 다르게 판단한 이유는 `memo` 에 적어 주시면 규칙을 고치는 데 씁니다.",
        "",
    ]
    answers = []
    key = []
    for i, (name, r) in enumerate(picked, 1):
        s, j = students[int(r["student_id"])], jobs[int(r["job_posting_id"])]
        degree = max((e["degree"] for e in s["education"]), key=lambda d: list(DEGREE).index(d))
        careers = ", ".join(f"{c['position']}({c['company_name']})" for c in s["careers"]) or "없음"
        projects = " / ".join(f"{p['title']}: {', '.join(p['tech_stack'])}" for p in s["projects"])
        md += [
            f"## {i}. 수료생 {s['id']} × 공고 {j['jobPostingId']}",
            "",
            f"**수료생** 희망 직무 {s['desired_job']} · 희망 지역 {s['preferred_region']} · 학력 {DEGREE[degree]} · 이전 경력 {careers}",
            f"- 기술: {', '.join(s['skills'])}",
            f"- 프로젝트: {projects}",
            "",
            f"**공고** {j['title']} · {j['companyName']} · {j['location']} · 경력 {j['career']} · 학력 {j['education']}",
            "",
            "```",
            j["description"].replace("\r\n", "\n"),
            "```",
            "",
        ]
        answers.append([i, s["id"], j["jobPostingId"], "", ""])
        key.append([i, s["id"], j["jobPostingId"], r["score"], r["violations"], name])

    (OUT / "sample_sheet.md").write_text("\n".join(md), encoding="utf-8")
    # 검수자가 엑셀로 열 수 있도록 BOM 을 붙인다 (없으면 엑셀이 한글을 깨뜨림)
    with (OUT / "sample_answers.csv").open("w", encoding="utf-8-sig", newline="") as f:
        csv.writer(f, lineterminator="\n").writerows([["no", "student_id", "job_posting_id", "my_score", "memo"], *answers])
    with (OUT / "sample_key.csv").open("w", encoding="utf-8-sig", newline="") as f:
        csv.writer(f, lineterminator="\n").writerows([["no", "student_id", "job_posting_id", "rule_score", "violations", "stratum"], *key])
    print(f"{len(picked)}쌍 → {OUT / 'sample_sheet.md'}")


if __name__ == "__main__":
    main()
