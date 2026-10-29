# 주인: AI-A
"""평가용 수료생 자료 자동 검사 (PERSONAS.md '자동 검사').

실행: python ai/eval/personas/check_students.py
실패가 있으면 종료 코드 1. 걸린 항목만 사람이 확인한다.
"""

import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).parent

POOR = {6, 7, 14, 19}  # 자료 전반이 부실
MEDIUM = {10, 13, 17}  # 자료는 중간, 자기소개서만 짧음 (맞는 공고 없음 케이스)

# 자기소개서에만 있는 수치 (의도한 과장 · 전용 사실). 이 값은 원문에 없어야 정상
CL_ONLY_NUMBERS = {2: {"10"}, 8: {"12"}, 16: {"100"}}

# 표 4 문체가 드러나는지 보는 표지 (자기소개서 또는 README 에서 찾음)
STYLE_MARKERS = {
    2: r"결론부터",
    6: r"했읍니다",
    9: r"따라서|즉,",
    10: r"!",
    11: r"첫째",
    15: r"함\.",
    18: r"communication|ownership",
    19: r"어요",
    20: r"[\U0001F300-\U0001FAFF]",
}

NUMBER = re.compile(r"\d+(?:\.\d+)?")
TEAM = re.compile(r"(\d+)명(?:이|,) ?(?:\d+주|여섯 주)")
EMAIL = re.compile(r"[\w.+-]+@[\w-]+\.[\w.]+")
PHONE = re.compile(r"01[016789]-?\d{3,4}-?\d{4}")
PLACEHOLDER = re.compile(r"[○△□▽◇]")


def load_students() -> list[dict]:
    students = []
    for path in sorted((HERE / "students").glob("batch_*.json")):
        students.extend(json.loads(path.read_text(encoding="utf-8")))
    return sorted(students, key=lambda s: s["id"])


def source_text(s: dict) -> str:
    """자기소개서를 뺀 등록 자료 전체 (근거가 될 수 있는 원문)."""
    parts = [" ".join(s["skills"])]
    for p in s["projects"]:
        parts += [p["title"], p["readme"] or "", p["role_note"] or "", " ".join(p["tech_stack"])]
    for c in s["careers"]:
        parts += [c["company_name"], c["position"] or "", c["description"] or ""]
    parts += [c["name"] for c in s["certificates"]]
    return "\n".join(parts)


def check(s: dict) -> list[str]:
    sid, problems = s["id"], []
    src, cl = source_text(s), s["cover_letter"]
    src_numbers = set(NUMBER.findall(src))

    # 1. 자기소개서 수치가 원문에 있는가
    extra = set(NUMBER.findall(cl)) - src_numbers
    expected = CL_ONLY_NUMBERS.get(sid, set())
    if extra - expected:
        problems.append(f"자기소개서에만 있는 수치 {sorted(extra - expected)}")
    if expected - extra:
        problems.append(f"의도한 자기소개서 전용 수치 {sorted(expected - extra)} 가 없거나 원문에도 있음")

    # 2. README 의 팀 인원 표현이 team_size 와 같은가
    for p in s["projects"]:
        for n in TEAM.findall(p["readme"] or ""):
            if int(n) != p["team_size"]:
                problems.append(f"프로젝트 {p['id']} README {n}명 ≠ team_size {p['team_size']}")

    # 3. skills 가 모두 자료에 나오는가 (역량 추출 정답 조건)
    for skill in s["skills"]:
        found_in_projects = any(
            skill.lower().replace(" ", "") in (p["readme"] + " ".join(p["tech_stack"])).lower().replace(" ", "")
            for p in s["projects"]
        )
        if not found_in_projects:
            problems.append(f"skills '{skill}' 가 프로젝트 자료에 없음")

    # 4. techStack 은 skills 안에서만
    for p in s["projects"]:
        outside = set(p["tech_stack"]) - set(s["skills"])
        if outside:
            problems.append(f"프로젝트 {p['id']} techStack 이 skills 밖: {sorted(outside)}")

    # 5. 개인정보 · 실제 회사명
    all_text = src + "\n" + cl
    if EMAIL.search(all_text) or PHONE.search(all_text):
        problems.append("이메일 · 전화번호 형식 문자열 있음")
    for c in s["careers"]:
        if not PLACEHOLDER.search(c["company_name"]):
            problems.append(f"경력 회사명 '{c['company_name']}' 이 모의 표기(○△□▽◇)가 아님")
    for e in s["education"]:
        if not PLACEHOLDER.search(e["school_name"]):
            problems.append(f"학교명 '{e['school_name']}' 이 모의 표기가 아님")

    # 6. 길이 (충실 그룹만 실제 수준, 부실 · 중간은 짧게 유지)
    if sid in POOR | MEDIUM:
        if len(cl) > 120:
            problems.append(f"짧아야 할 자기소개서가 {len(cl)}자")
    else:
        main = s["projects"][0]["readme"]
        if not 600 <= len(main) <= 1500:
            problems.append(f"대표 프로젝트 README {len(main)}자 (600~1,500)")
        if not 400 <= len(cl) <= 700:
            problems.append(f"자기소개서 {len(cl)}자 (400~700)")
        if len(s["projects"]) < 2:
            problems.append("프로젝트가 1개 (2개 이상 필요)")

    # 7. 문체 표지
    marker = STYLE_MARKERS.get(sid)
    if marker and not re.search(marker, cl + "\n" + "\n".join(p["readme"] for p in s["projects"])):
        problems.append(f"문체 표지 /{marker}/ 없음")

    return problems


def main() -> int:
    students = load_students()
    ids = [s["id"] for s in students]
    failed = 0
    if ids != list(range(1, 21)):
        print(f"수료생 id 가 1~20 이 아님: {ids}")
        failed += 1
    project_ids = [p["id"] for s in students for p in s["projects"]]
    if len(project_ids) != len(set(project_ids)):
        print("프로젝트 id 중복")
        failed += 1

    for s in students:
        problems = check(s)
        cl_len = len(s["cover_letter"])
        readme_len = len(s["projects"][0]["readme"])
        status = "OK " if not problems else "FAIL"
        print(f"{status} {s['id']:>2} {s['name']}  프로젝트 {len(s['projects'])} · README {readme_len}자 · 자소서 {cl_len}자")
        for p in problems:
            print(f"      - {p}")
        failed += bool(problems)
    print(f"\n실패 {failed}건" if failed else "\n모두 통과")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
