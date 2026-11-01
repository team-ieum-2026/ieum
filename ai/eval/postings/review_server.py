# 주인: AI-A
"""실제, 일반 공고 검수 화면. 모델 3개의 판정이 갈린 공고를 브라우저에서 클릭으로 판정한다 (LABELING.md 5장).

실행: cd ai/eval && python3 postings/review_server.py   → http://127.0.0.1:8765
- 화면: review_page.html. 공고 본문에 모델별 근거 구절을 색으로 표시하고, 조건별로 모델 값을 골라 저장한다.
- 저장하면 external_fixes.json 에 바로 쓴다. 다 끝나면 cross_label.py 를 다시 돌려 라벨에 반영한다.
- 정답이 보이는 화면이다. AI-B 는 실행하지 않는다 (LABELING.md 맨 앞).
- 표준 라이브러리만 쓴다. 이 컴퓨터(127.0.0.1)에서만 열린다.
"""

import json
import sys
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[1]))
sys.path.insert(0, str(Path(__file__).parent))
from external import FIXES, load_by_posting, load_fixes  # noqa: E402
from rubric import DESIRED_JOB_FAMILY, POSTING_EDUCATION, has_skill, score, violations  # noqa: E402

HERE = Path(__file__).parent
AI_DIR = HERE.parents[1]
PAGE = HERE / "review_page.html"
PORT = 8765
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
    """모델별 원래 추출 결과 (근거 구절 포함)"""
    return {SHORT.get(p.stem, p.stem): json.loads(p.read_text(encoding="utf-8")) for p in sorted(CONDITIONS_DIR.glob("*.json"))}


def student_line(s: dict) -> str:
    degrees = "/".join(DEGREE.get(e["degree"], e["degree"]) for e in s["education"]) or "없음"
    return f"{s['id']}번 {s['desired_job']}, 희망 {s['preferred_region']}, {degrees}, 기술 {', '.join(s['skills'])}"


def split_postings(students: list[dict], by_posting: dict) -> tuple[list[int], list[int]]:
    """실제, 일반 공고 중 (2점인지가 갈린 공고, 1점과 0점 사이만 갈린 공고)"""
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
    return two, low


def build_data() -> dict:
    students = load_students()
    by_posting = load_by_posting()
    jobs = load_jobs()
    raw = load_raw()
    two, low = split_postings(students, by_posting)
    postings = []
    for part, pids in (("1부", two), ("2부", low)):
        for pid in pids:
            job = jobs[pid]
            models = {}
            for full, posting in by_posting[pid]["models"].items():
                short = SHORT.get(full, full)
                models[short] = {"posting": posting, "extracted": raw[short][str(pid)]["extracted"]}
            postings.append({
                "id": pid, "part": part, "source": "실제" if by_posting[pid]["source"] == "REAL" else "일반",
                "title": job["title"], "jobCategory": job["jobCategory"], "career": job["career"],
                "education": job["education"], "location": job["location"], "description": job["description"] or "",
                "models": models,
            })
    return {
        "postings": postings,
        "students": [{"id": s["id"], "line": student_line(s), "family": DESIRED_JOB_FAMILY.get(s["desired_job"]),
                      "skills": s["skills"]} for s in students],
        "educations": list(POSTING_EDUCATION),
        "fixes": load_fixes(),
    }


def scores_for(posting: dict) -> dict[int, dict]:
    """수료생별 점수와 이유 (어긋난 조건, 없는 필수 기술)"""
    return {s["id"]: {"score": score(s, posting), "violations": violations(s, posting),
                      "missing": [r for r in posting["required"] if not has_skill(r, s["skills"])]}
            for s in load_students()}


class Handler(BaseHTTPRequestHandler):
    def send(self, status: int, body: bytes, content_type: str) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_json(self, data) -> None:
        self.send(200, json.dumps(data, ensure_ascii=False).encode(), "application/json; charset=utf-8")

    def do_GET(self) -> None:
        if self.path == "/":
            self.send(200, PAGE.read_bytes(), "text/html; charset=utf-8")
        elif self.path == "/api/data":
            self.send_json(build_data())
        else:
            self.send(404, b"not found", "text/plain")

    def do_POST(self) -> None:
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])) or b"{}")
        if self.path == "/api/score":
            # 화면에서 고른 값으로 수료생별 점수를 미리 본다
            self.send_json(scores_for(body["posting"]))
        elif self.path == "/api/fix":
            # {"id": 626, "fix": {...}} 저장, fix 가 null 이면 판정 취소
            fixes = load_fixes()
            if body["fix"] is None:
                fixes.pop(str(body["id"]), None)
            else:
                fixes[str(body["id"])] = body["fix"]
            ordered = dict(sorted(fixes.items(), key=lambda kv: int(kv[0])))
            FIXES.write_text(json.dumps(ordered, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            self.send_json(ordered)
        else:
            self.send(404, b"not found", "text/plain")

    def log_message(self, *args) -> None:
        pass


def main() -> None:
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    url = f"http://127.0.0.1:{PORT}"
    print(f"검수 화면: {url} (끝내려면 Ctrl+C)")
    webbrowser.open(url)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
