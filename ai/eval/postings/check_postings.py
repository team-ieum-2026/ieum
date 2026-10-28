# 주인: AI-A
"""정답 · 함정 공고 자동 검사.

실행: python ai/eval/postings/check_postings.py  (render_postings.py 실행 뒤)
- 정답(GOLD): 대상 수료생에게 루브릭 조건(직무 · 경력 · 학력 · 지역 · 필수 기술 전부)이 모두 맞는가
- 함정(TRAP): 지정한 조건이 어긋나는가. 그 외 조건은 맞아야 한다 (LOOKALIKE 는 직무가 달라 기술도 다를 수 있어 정보로만 표시)
- 본문: 필수 기술 · 경력 조건 · 학력 조건이 실제로 적혀 있는가, 길이, 수도권, 회사가 50곳 목록에 있는가
- 누출: 대상 수료생의 README · 역할 기록 · 자기소개서와 길게 겹치는 구절이 없는가
실패가 있으면 종료 코드 1.
"""

import json
import sys
from collections import Counter
from difflib import SequenceMatcher
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[1]))
from rubric import violations  # noqa: E402

HERE = Path(__file__).parent
ROOT = HERE.parents[2]
NO_GOLD = {10, 13, 17}  # 정답 공고를 만들지 않는 수료생
TYPE_TO_CONDITION = {"CAREER": "CAREER", "SKILL": "SKILL", "REGION": "REGION", "EDU": "EDU", "LOOKALIKE": "JOB"}
LEAK_FAIL = 15  # 공백을 뺀 글자 수
LENGTH = (120, 700)


def load_students() -> dict[int, dict]:
    students = []
    for path in sorted((HERE.parent / "personas" / "students").glob("batch_*.json")):
        students.extend(json.loads(path.read_text(encoding="utf-8")))
    return {s["id"]: s for s in students}


def strip_terms(text: str, terms: set[str]) -> str:
    """기술 이름 · 문장부호는 겹쳐도 누출이 아니므로 빼고 비교한다."""
    for term in sorted(terms, key=len, reverse=True):
        text = text.replace(term, " ")
    return "".join(ch for ch in text if ch.isalnum() or "가" <= ch <= "힣")


def longest_overlap(a: str, b: str, terms: set[str]) -> str:
    a, b = strip_terms(a, terms), strip_terms(b, terms)
    m = SequenceMatcher(None, a, b, autojunk=False).find_longest_match(0, len(a), 0, len(b))
    return a[m.a:m.a + m.size]


def student_text(s: dict) -> str:
    return "\n".join([s["cover_letter"]] + [(p["readme"] or "") + (p["role_note"] or "") for p in s["projects"]])


def main() -> int:
    spec = json.loads((HERE / "spec.json").read_text(encoding="utf-8"))["postings"]
    jobs = {j["jobPostingId"]: j for j in
            json.loads((HERE / "out" / "eval_postings.json").read_text(encoding="utf-8"))["jobs"]}
    companies = {c["companyName"] for c in
                 json.loads((ROOT / "ai/data/company/companies.json").read_text(encoding="utf-8"))["companies"]}
    students = load_students()
    failures = 0

    def fail(pid, msg):
        nonlocal failures
        failures += 1
        print(f"FAIL {pid}: {msg}")

    ids = [p["id"] for p in spec]
    if ids != list(range(900201, 900201 + len(ids))):
        fail("-", "공고 id 가 900201부터 연속이 아님")

    per_student = Counter((p["sid"], p["label"]) for p in spec)
    for sid in students:
        want_gold = 0 if sid in NO_GOLD else 3
        if per_student[(sid, "GOLD")] != want_gold or per_student[(sid, "TRAP")] != 2:
            fail(f"수료생 {sid}", f"정답 {per_student[(sid, 'GOLD')]} · 함정 {per_student[(sid, 'TRAP')]} (기대 {want_gold} · 2)")

    info = []
    for p in spec:
        pid, s, job = p["id"], students[p["sid"]], jobs[p["id"]]
        posting = {"family": p["family"], "career": p["career"], "education": p["edu"],
                   "location": p["loc"], "required": p["required"]}
        v = set(violations(s, posting))
        if p["label"] == "GOLD" and v:
            fail(pid, f"정답인데 어긋난 조건 {sorted(v)}")
        if p["label"] == "TRAP":
            expected = {TYPE_TO_CONDITION[p["type"]]} | {TYPE_TO_CONDITION[t] for t in p.get("also", [])}
            if not expected <= v:
                fail(pid, f"함정 유형 {p['type']} 인데 그 조건이 맞음 (어긋난 조건 {sorted(v)})")
            extra = v - expected
            if extra and p["type"] != "LOOKALIKE":
                fail(pid, f"함정 {p['type']} 외에 다른 조건도 어긋남 {sorted(extra)} → 너무 쉬운 함정")
            elif extra:
                info.append(f"{pid} LOOKALIKE 추가로 어긋난 조건 {sorted(extra)}")

        desc = job["description"]
        for skill in p["required"]:
            shown = p.get("display", {}).get(skill, skill)
            if shown not in desc:
                fail(pid, f"필수 기술 '{shown}' 이 본문에 없음")
        if p["career"] == "경력" and f"{p['years']}년 이상" not in desc:
            fail(pid, "경력 조건이 본문에 없음")
        if p["edu"] not in ("학력무관",) and not any(k in desc for k in ("졸업 이상", "학위")):
            fail(pid, "학력 조건이 본문에 없음")
        if not LENGTH[0] <= len(desc) <= LENGTH[1]:
            fail(pid, f"본문 {len(desc)}자 (기준 {LENGTH[0]}~{LENGTH[1]})")
        if p["loc"].split()[0] not in ("서울", "경기", "인천"):
            fail(pid, f"수도권 밖 {p['loc']}")
        if p["company"] not in companies:
            fail(pid, f"회사 '{p['company']}' 가 50곳 목록에 없음")

        overlap = longest_overlap(desc, student_text(s), set(s["skills"]) | set(p["required"]))
        if len(overlap) >= LEAK_FAIL:
            fail(pid, f"수료생 {s['id']} 자료와 겹치는 구절 '{overlap}'")

    lengths = sorted(len(j["description"]) for j in jobs.values())
    print(f"\n공고 {len(spec)}건 · 정답 {sum(p['label'] == 'GOLD' for p in spec)} · 함정 {sum(p['label'] == 'TRAP' for p in spec)}")
    print("함정 유형:", dict(Counter(p["type"] for p in spec if p["label"] == "TRAP")))
    print(f"본문 길이: 최소 {lengths[0]} · 중앙값 {lengths[len(lengths) // 2]} · 최대 {lengths[-1]} (일반 공고 118 · 342 · 607)")
    for line in info:
        print("정보:", line)
    print(f"\n실패 {failures}건" if failures else "\n모두 통과")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
