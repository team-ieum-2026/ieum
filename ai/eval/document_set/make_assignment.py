# 주인: AI-A
"""서류 평가 입력 배정표: 수료생마다 어떤 공고로 서류를 만들고, 기업 정보는 무엇을 보낼지.

실행: python ai/eval/document_set/make_assignment.py
출력: document_set/assignment.json (결과를 보기 전에 커밋해서 입력을 고정한다)

묶음
- test: 주 지표. test 수료생 중 맞는 평가용 공고가 있는 10명 (10 · 17번은 맞는 공고가 없어 뺀다).
  기업 정보 상태를 PARTIAL 3 · NOT_FOUND 3 · FOUND 4 로 배정한다
- real: 실제 공고 보조 세트 3건 (test 4 · 17 · 20번). 주 지표와 섞지 않는다. 기업 정보는 company_info_real.json
- dev: 개선 실험 7명 (13번은 맞는 공고가 없어 뺀다). 결과를 보며 고친다

공고 고르기
- 평가용 공고: 대상 정답 공고(gold_matching.csv 의 GOLD, 수료생마다 3건) 중 배정 상태의 회사 공고, 번호가 가장 작은 것.
  NOT_FOUND 는 상태와 상관없이 GOLD 중 번호가 가장 작은 것을 고르고 기업 정보를 보내지 않는다
  (서비스에서도 NOT_FOUND 는 company_info 에 그 회사가 없을 때다)
- 실제 공고: 정답표(pair_labels.csv)에서 2점인 실제 공고 중 번호가 가장 작은 것.
  dev 는 보조 세트 공고와 다른 dev 가 고른 공고를 빼고 고른다

test 상태 배정: SEED 로 10명 순서를 섞고, 그 순서대로 PARTIAL 회사 GOLD 가 있는 3명 → PARTIAL.
남은 사람 중 FOUND 회사 GOLD 가 없는 사람 → NOT_FOUND. 남은 NOT_FOUND 자리는 섞은 순서대로. 나머지 → FOUND
"""

import csv
import json
import random
from collections import defaultdict
from pathlib import Path

EVAL = Path(__file__).parents[1]
ROOT = Path(__file__).parents[3]
OUT = Path(__file__).parent / "assignment.json"
SEED = 20261103

TEST_NO_FIT = (10, 17)
DEV_NO_FIT = (13,)
DEV_REAL = (5, 8, 14, 15)
REAL_SET = (4, 17, 20)
QUOTA = {"PARTIAL": 3, "NOT_FOUND": 3, "FOUND": 4}


def load():
    split = json.loads((EVAL / "split.json").read_text())
    eval_jobs = json.loads((EVAL / "postings/out/eval_postings.json").read_text())["jobs"]
    company_of = {str(j["jobPostingId"]): j["companyName"] for j in eval_jobs}
    companies = json.loads((ROOT / "ai/data/eval/companies.json").read_text())["companies"]
    status_of = {c["companyName"]: c["status"] for c in companies}
    real_jobs = json.loads((ROOT / "ai/data/eval/ieum_jobs_20261028.json").read_text())
    real_jobs = real_jobs if isinstance(real_jobs, list) else real_jobs["jobs"]
    real_company_of = {str(j["jobPostingId"]): j["companyName"] for j in real_jobs}

    gold = defaultdict(list)  # 수료생 → [(공고 번호, 회사 상태)], 번호 순
    for r in csv.DictReader(open(EVAL / "gold/gold_matching.csv")):
        if r["label"] == "GOLD":
            gold[int(r["student_id"])].append((r["job_posting_id"], status_of[company_of[r["job_posting_id"]]]))
    for s in gold:
        gold[s].sort(key=lambda g: int(g[0]))

    real_fit = defaultdict(list)  # 수료생 → 2점 실제 공고 번호, 번호 순
    for r in csv.DictReader(open(EVAL / "gold/pair_labels.csv")):
        if r["origin"] == "REAL" and r["score"] == "2":
            real_fit[int(r["student_id"])].append(r["job_posting_id"])
    for s in real_fit:
        real_fit[s].sort(key=int)

    return split, gold, company_of, real_fit, real_company_of


def eval_row(group, student, gold, company_of, status):
    """평가용 공고 한 줄. status 가 NOT_FOUND 면 GOLD 첫 공고 + 기업 정보 없음"""
    if status == "NOT_FOUND":
        job_id = gold[student][0][0]
    else:
        job_id = next(j for j, s in gold[student] if s == status)
    return {
        "group": group,
        "studentId": student,
        "jobPostingId": int(job_id),
        "postingOrigin": "EVAL",
        "companyName": company_of[job_id],
        "companyInfoStatus": status,
        "companyInfoFrom": None if status == "NOT_FOUND" else "ai/data/eval/companies.json",
    }


def real_row(group, student, real_fit, real_company_of, real_info, used=()):
    job_id = next(j for j in real_fit[student] if j not in used)
    name = real_company_of[job_id]
    status = real_info.get(name, "NOT_FOUND")
    return {
        "group": group,
        "studentId": student,
        "jobPostingId": int(job_id),
        "postingOrigin": "REAL",
        "companyName": name,
        "companyInfoStatus": status,
        "companyInfoFrom": None if status == "NOT_FOUND" else "ai/eval/document_set/company_info_real.json",
    }


def assign_test(students, gold):
    order = students[:]
    random.Random(SEED).shuffle(order)
    has = {s: {st for _, st in gold[s]} for s in students}
    status = {}
    for s in order:
        if len([v for v in status.values() if v == "PARTIAL"]) < QUOTA["PARTIAL"] and "PARTIAL" in has[s]:
            status[s] = "PARTIAL"
    for s in order:
        if s not in status and "FOUND" not in has[s]:
            status[s] = "NOT_FOUND"
    for s in order:
        if s not in status and len([v for v in status.values() if v == "NOT_FOUND"]) < QUOTA["NOT_FOUND"]:
            status[s] = "NOT_FOUND"
    for s in order:
        status.setdefault(s, "FOUND")
    return order, status


def main():
    split, gold, company_of, real_fit, real_company_of = load()
    real_info = json.loads((Path(__file__).parent / "company_info_real.json").read_text())
    real_info = {c["companyName"]: c["status"] for c in real_info["companies"]}

    test = [s for s in split["test"] if s not in TEST_NO_FIT]
    order, status = assign_test(test, gold)
    counts = {k: list(status.values()).count(k) for k in QUOTA}
    assert counts == QUOTA, counts

    rows = [eval_row("test", s, gold, company_of, status[s]) for s in test]
    rows += [real_row("real", s, real_fit, real_company_of, real_info) for s in REAL_SET]
    # dev 는 보조 세트 공고를 쓰지 않는다 (dev 로 고치면 보조 세트가 처음 보는 입력이 아니게 된다). dev 끼리도 겹치지 않게
    used = {str(r["jobPostingId"]) for r in rows if r["group"] == "real"}
    for s in split["dev"]:
        if s in DEV_NO_FIT:
            continue
        if s in DEV_REAL:
            rows.append(real_row("dev", s, real_fit, real_company_of, real_info, used))
            used.add(str(rows[-1]["jobPostingId"]))
        else:
            rows.append(eval_row("dev", s, gold, company_of, gold[s][0][1]))

    OUT.write_text(json.dumps({
        "_meta": {
            "madeOn": "2026-11-03",
            "seed": SEED,
            "testOrder": order,
            "rule": "make_assignment.py 첫 주석 참고. 결과를 보기 전에 커밋한다",
        },
        "rows": rows,
    }, ensure_ascii=False, indent=2) + "\n")
    for r in rows:
        print(r["group"], r["studentId"], r["jobPostingId"], r["postingOrigin"], r["companyInfoStatus"], r["companyName"])


if __name__ == "__main__":
    main()
