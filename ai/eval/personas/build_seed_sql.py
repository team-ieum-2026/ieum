# 주인: AI-A
"""평가용 수료생 20명(students/batch_*.json) → BE-B 적재용 SQL + AI 입력(StudentMaterial) JSON.

실행: python ai/eval/personas/build_seed_sql.py
출력
- out/eval_students.sql: db/seed.sql 적재 뒤에 실행. id 1~20의 등록 자료만 바꾼다
  (이름 · 과정 · 이메일 · 수료일 · 마지막 응답 시각 · 상태는 그대로)
- out/student_materials.json: POST /ai/profile/extract · /ai/document/draft 의 student 형식 (camelCase)

행 id 규칙: 학력 1000 + 수료생 id × 10 + 순번, 경력 2000 + …, 자격증 3000 + … (시드의 자동 증가 id와 겹치지 않게).
프로젝트 id는 JSON에 적힌 값을 그대로 쓴다 (11 · 12 · 113 은 목업 · 추천 이력이 참조).
"""

import json
from pathlib import Path

HERE = Path(__file__).parent
OUT = HERE / "out"

# db/seed.sql 의 course 와 같은 값
COURSES = {
    1: {"courseId": 1, "name": "청년취업사관학교 AI Agent 과정", "cohort": "4기", "campus": "동대문캠퍼스",
        "startedOn": "2026-03-02", "endedOn": "2026-08-29"},
    2: {"courseId": 2, "name": "청년취업사관학교 클라우드 풀스택 과정", "cohort": "3기", "campus": "동대문캠퍼스",
        "startedOn": "2026-02-02", "endedOn": "2026-07-31"},
}
COMPLETED_ON = {1: "2026-08-29", 2: "2026-07-31"}


def load_students() -> list[dict]:
    students = []
    for path in sorted((HERE / "students").glob("batch_*.json")):
        students.extend(json.loads(path.read_text(encoding="utf-8")))
    return sorted(students, key=lambda s: s["id"])


def row_id(base: int, student_id: int, index: int) -> int:
    return base + student_id * 10 + index + 1


def sql(value) -> str:
    """MySQL 문자열 리터럴. 기본 모드(백슬래시 이스케이프)를 기준으로 한다."""
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, int):
        return str(value)
    if isinstance(value, list):
        value = json.dumps(value, ensure_ascii=False)
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def insert(table: str, columns: list[str], rows: list[list]) -> str:
    values = ",\n".join(" (" + ", ".join(sql(v) for v in row) + ")" for row in rows)
    return f"INSERT INTO {table} ({', '.join(columns)}) VALUES\n{values};\n"


def build_sql(students: list[dict]) -> str:
    ids = [s["id"] for s in students]
    lo, hi = min(ids), max(ids)
    parts = [
        "-- =====================================================================\n"
        "-- 평가용 수료생 20명 등록 자료 교체 (AI-A 생성, BE-B 적재)\n"
        "-- 원본: ai/eval/personas/students/batch_*.json · 설계: ai/eval/personas/PERSONAS.md\n"
        "-- 이 파일은 build_seed_sql.py 가 만든다. 직접 고치지 말고 JSON을 고친 뒤 다시 만든다.\n"
        "-- 실행: db/schema.sql → db/seed.sql 다음에 mysql -u <계정> -p ieum < eval_students.sql\n"
        "-- 바꾸는 것: student 의 희망 직무 · 지역 · 병역 · 기술, 학력 · 경력 · 자격증 · 프로젝트 · 자기소개서\n"
        "-- 그대로 두는 것: 이름 · 과정 · 이메일 · GitHub · 수료일 · 마지막 응답 시각 · student_status · 추천 이력\n"
        "-- 모든 이름 · 학교 · 회사 · 수치는 모의 데이터.\n"
        "-- =====================================================================\n",
        "USE ieum;\nSET NAMES utf8mb4;\nSTART TRANSACTION;\n",
    ]

    # 이름이 다르면 0행이 바뀐다 → 시드가 예상과 다르다는 신호
    for s in students:
        parts.append(
            f"UPDATE student SET desired_job = {sql(s['desired_job'])}, preferred_region = {sql(s['preferred_region'])}, "
            f"military_status = {sql(s['military_status'])}, skills = {sql(s['skills'])} "
            f"WHERE id = {s['id']} AND name = {sql(s['name'])};"
        )
    parts.append("")

    for table in ["student_cover_letter", "student_project", "student_certificate", "student_career", "student_education"]:
        parts.append(f"DELETE FROM {table} WHERE student_id BETWEEN {lo} AND {hi};")
    parts.append("")

    parts.append(insert(
        "student_education",
        ["id", "student_id", "school_name", "major", "degree", "status", "graduated_on"],
        [[row_id(1000, s["id"], i), s["id"], e["school_name"], e["major"], e["degree"], e["status"], e["graduated_on"]]
         for s in students for i, e in enumerate(s["education"])],
    ))
    careers = [[row_id(2000, s["id"], i), s["id"], c["company_name"], c["position"], c["started_on"], c["ended_on"],
                c["description"]] for s in students for i, c in enumerate(s["careers"])]
    if careers:
        parts.append(insert(
            "student_career",
            ["id", "student_id", "company_name", "position", "started_on", "ended_on", "description"],
            careers,
        ))
    certs = [[row_id(3000, s["id"], i), s["id"], c["name"], c["issuer"], c["acquired_on"]]
             for s in students for i, c in enumerate(s["certificates"])]
    if certs:
        parts.append(insert("student_certificate", ["id", "student_id", "name", "issuer", "acquired_on"], certs))
    parts.append(insert(
        "student_project",
        ["id", "student_id", "title", "period_text", "team_size", "role_note", "readme", "tech_stack",
         "portfolio_file_url", "include_in_apply"],
        [[p["id"], s["id"], p["title"], p["period_text"], p["team_size"], p["role_note"], p["readme"], p["tech_stack"],
          p["portfolio_file_url"], p["include_in_apply"]] for s in students for p in s["projects"]],
    ))
    parts.append(insert(
        "student_cover_letter", ["student_id", "content"], [[s["id"], s["cover_letter"]] for s in students]
    ))
    parts.append("COMMIT;\n")
    return "\n".join(parts)


def to_material(s: dict) -> dict:
    """ai/schemas/student.py StudentMaterial 형식. email · githubUrl 은 시드 값 규칙을 따른다."""
    return {
        "studentId": s["id"],
        "basic": {
            "name": s["name"],
            "email": f"student{s['id']:02d}@example.com" if s["id"] != 1 else "minjun.dev@example.com",
            "githubUrl": f"github.com/ieum-seed-{s['id']:02d}" if s["id"] != 1 else "github.com/minjun-dev",
            "desiredJob": s["desired_job"],
            "preferredRegion": s["preferred_region"],
            "militaryStatus": s["military_status"],
            "completedOn": COMPLETED_ON[s["course_id"]],
        },
        "course": COURSES[s["course_id"]],
        "education": [
            {"itemId": row_id(1000, s["id"], i), "source": "REGISTERED", "schoolName": e["school_name"],
             "major": e["major"], "degree": e["degree"], "status": e["status"], "graduatedOn": e["graduated_on"]}
            for i, e in enumerate(s["education"])
        ],
        "careers": [
            {"itemId": row_id(2000, s["id"], i), "source": "REGISTERED", "companyName": c["company_name"],
             "position": c["position"], "startedOn": c["started_on"], "endedOn": c["ended_on"],
             "description": c["description"]}
            for i, c in enumerate(s["careers"])
        ],
        "certificates": [
            {"itemId": row_id(3000, s["id"], i), "source": "REGISTERED", "name": c["name"], "issuer": c["issuer"],
             "acquiredOn": c["acquired_on"]}
            for i, c in enumerate(s["certificates"])
        ],
        "skills": s["skills"],
        "projects": [
            {"itemId": p["id"], "title": p["title"], "periodText": p["period_text"], "teamSize": p["team_size"],
             "techStack": p["tech_stack"], "portfolioFileName": p["portfolio_file_url"],
             "includeInApply": p["include_in_apply"], "roleNote": p["role_note"], "readme": p["readme"],
             "source": "REGISTERED"}
            for p in s["projects"]
        ],
        "coverLetter": {"content": s["cover_letter"], "updatedAt": "2026-10-29T09:00:00+09:00"},
    }


def main() -> None:
    students = load_students()
    OUT.mkdir(exist_ok=True)
    (OUT / "eval_students.sql").write_text(build_sql(students), encoding="utf-8")
    materials = [to_material(s) for s in students]
    (OUT / "student_materials.json").write_text(
        json.dumps(materials, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(f"수료생 {len(students)}명 → {OUT / 'eval_students.sql'}, {OUT / 'student_materials.json'}")


if __name__ == "__main__":
    main()
