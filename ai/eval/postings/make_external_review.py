# 주인: AI-A
"""실제 공고, 일반 공고 중 모델 3개의 판정이 갈린 공고의 검수 시트 (LABELING.md 4장).

실행: cd ai && .venv/bin/python eval/postings/make_external_review.py
출력: ai/eval/gold/external_review.md (정답 정보가 들어 있어 gold/ 에 둔다. AI-B 는 열지 않는다)

- 1부: 2점인지 아닌지가 모델끼리 갈린 공고 (Precision@3 에 걸림, 우선)
- 2부: 1점과 0점 사이만 갈린 공고 (명백한 부적합률에 걸림, 시간이 되면)
- 이미 사람이 정한 공고(external_fixes.json)는 판정을 함께 보여 준다. 판정은 external_fixes.json 에 옮겨 라벨에 반영한다.
"""

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[1]))
sys.path.insert(0, str(Path(__file__).parent))
from external import load_by_posting, load_fixes  # noqa: E402
from rubric import score  # noqa: E402

HERE = Path(__file__).parent
AI_DIR = HERE.parents[1]
OUT = HERE.parent / "gold" / "external_review.md"
JOB_FILES = [AI_DIR / "data" / "eval" / "ieum_jobs_20261028.json", AI_DIR / "data" / "eval" / "jobs_general.json"]
CONDITIONS_DIR = HERE / "out" / "conditions"
SHORT = {"gemini-3.8-flash": "flash", "gemini-4-argon": "argon", "gpt-6.1-sol": "gpt"}
DEGREE = {"HIGH_SCHOOL": "고졸", "ASSOCIATE": "전문학사", "BACHELOR": "학사", "MASTER": "석사", "DOCTOR": "박사"}


def load_students() -> list[dict]:
    students = []
    for path in sorted((HERE.parent / "personas" / "students").glob("batch_*.json")):
        students.extend(json.loads(path.read_text(encoding="utf-8")))
    return sorted(students, key=lambda s: s["id"])


def load_jobs() -> dict[int, dict]:
    jobs = {}
    for path in JOB_FILES:
        data = json.loads(path.read_text(encoding="utf-8"))
        for j in next(v for v in data.values() if isinstance(v, list)):
            jobs[j["jobPostingId"]] = j
    return jobs


def load_raw() -> dict[str, dict]:
    """모델별 원래 추출 결과 (인용 포함)"""
    return {SHORT.get(p.stem, p.stem): json.loads(p.read_text(encoding="utf-8")) for p in sorted(CONDITIONS_DIR.glob("*.json"))}


def student_line(s: dict) -> str:
    degrees = "/".join(DEGREE.get(e["degree"], e["degree"]) for e in s["education"]) or "없음"
    return f"{s['id']}번 {s['desired_job']}, 희망 {s['preferred_region']}, {degrees}, 기술 {', '.join(s['skills'])}"


def posting_block(n: int, pid: int, entry: dict, job: dict, raw: dict, students: list[dict], fixes: dict) -> list[str]:
    lines = [f"### {n}. 공고 {pid} ({'실제' if entry['source'] == 'REAL' else '일반'}) {job['title']}", ""]
    lines.append(f"- 직종 칸: {job['jobCategory']} / 경력 칸: {job['career']} / 학력 칸: {job['education']} / 근무지: {job['location']}")
    lines += ["", "<details>", "<summary>공고 본문</summary>", "", "```", (job["description"] or "").strip(), "```", "", "</details>", ""]

    lines += ["| 모델 | 직무군 | 필수 기술 (하나만 있으면 되는 것은 \\|) | 본문 경력 | 본문 학력 |", "| --- | --- | --- | --- | --- |"]
    for model, records in raw.items():
        e = records[str(pid)]["extracted"]
        skills = ", ".join("\\|".join(s["options"]) for s in e["requiredSkills"]) or "없음"
        lines.append(f"| {model} | {', '.join(e['jobFamilies'])} | {skills} | {e['bodyCareer']} | {e['bodyEducation']} |")
    lines.append("")

    lines += ["점수가 갈린 수료생", ""]
    for s in students:
        scores = {SHORT.get(m, m): score(s, p) for m, p in entry["models"].items()}
        if len(set(scores.values())) > 1:
            lines.append(f"- {student_line(s)} → " + ", ".join(f"{m} {v}점" for m, v in scores.items()))
    lines.append("")

    fix = fixes.get(str(pid))
    decided = f"정함: {json.dumps({k: v for k, v in fix.items()}, ensure_ascii=False)}" if fix else "(아직 안 정함)"
    lines += [f"판정: {decided}", "", "- 맞는 모델: ", "- 직접 고칠 값 (직무군, 필수 기술, 경력, 학력): ", "- 메모: ", ""]
    return lines


def main() -> None:
    students = load_students()
    by_posting = load_by_posting()
    fixes = load_fixes()
    jobs = load_jobs()
    raw = load_raw()

    two, low = [], []
    for pid, entry in sorted(by_posting.items()):
        if entry["source"] == "GENERATED":
            continue
        kinds = set()
        for s in students:
            scores = [score(s, p) for p in entry["models"].values()]
            if len(set(scores)) > 1:
                kinds.add("two" if len({x == 2 for x in scores}) > 1 else "low")
        if "two" in kinds:
            two.append(pid)
        elif "low" in kinds:
            low.append(pid)

    lines = [
        "# 실제 공고, 일반 공고 검수 시트",
        "",
        "모델 3개(flash, argon, gpt)의 판정이 갈린 공고만 모았다. 만든 스크립트: `postings/make_external_review.py`",
        "",
        "보는 법: 공고 본문을 읽고, 표에서 맞는 모델을 고른다. 셋 다 틀리면 직접 고칠 값을 적는다.",
        "적은 판정은 `postings/external_fixes.json`에 옮겨 라벨에 반영한다. 정한 공고는 다시 만들 때 \"판정: 정함\"으로 보인다.",
        "",
        f"## 1부. 2점인지 아닌지가 갈린 공고 ({len(two)}건, 우선)",
        "",
        "Precision@3 에 걸린다. 갈린 수료생에게 이 공고가 적합한지가 핵심이다.",
        "",
    ]
    for n, pid in enumerate(two, start=1):
        lines += posting_block(n, pid, by_posting[pid], jobs[pid], raw, students, fixes)
    lines += [f"## 2부. 1점과 0점 사이만 갈린 공고 ({len(low)}건, 시간이 되면)", "",
              "명백한 부적합률에 걸린다. 검수하지 않으면 모델 3개의 다수결(중앙값)을 그대로 쓴다.", ""]
    for n, pid in enumerate(low, start=1):
        lines += posting_block(n, pid, by_posting[pid], jobs[pid], raw, students, fixes)

    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"검수 시트: 1부 {len(two)}건, 2부 {len(low)}건 → {OUT}")


if __name__ == "__main__":
    main()
