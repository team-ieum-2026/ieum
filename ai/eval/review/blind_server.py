# 주인: AI-A
"""매칭 정답표 표본 검수 화면 (가린 채점). 시트(sample_sheet_{part}.md)를 한 쌍씩 보여 주고 점수를 받는다 (LABELING.md 5장).

실행: cd ai/eval && python3 review/blind_server.py test 소민   → http://127.0.0.1:8766
- 답은 review/answers_{part}_{이름}.csv 에 바로 쓴다. 칸은 sample_answers_{part}.csv 와 같다 (no, student_id, job_posting_id, my_score, memo).
- 규칙 판정(sample_key_*.csv)과 명세(spec.json)는 읽지 않는다. 화면에는 시트에 있는 내용만 나온다.
- 표준 라이브러리만 쓴다. 이 컴퓨터(127.0.0.1)에서만 열린다.
"""

import csv
import json
import re
import sys
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HERE = Path(__file__).parent
PAGE = HERE / "blind_page.html"
PORT = 8766
FIELDS = ["no", "student_id", "job_posting_id", "my_score", "memo"]


def parse_sheet(path: Path) -> tuple[str, list[dict]]:
    """시트 → (머리말, 쌍 목록). 쌍마다 수료생 부분, 공고 제목 줄, 공고 본문"""
    text = path.read_text(encoding="utf-8")
    head, *blocks = re.split(r"^## ", text, flags=re.M)
    pairs = []
    for block in blocks:
        m = re.match(r"(\d+)\. 수료생 (\d+) × 공고 (\d+)", block)
        student, _, posting = block.partition("**공고**")
        body = re.search(r"```\n(.*?)```", posting, flags=re.S)
        pairs.append({
            "no": m.group(1), "studentId": m.group(2), "jobPostingId": m.group(3),
            "student": student.split("\n", 1)[1].replace("**수료생**", "").strip(),
            "postingTitle": posting.split("\n", 1)[0].strip(),
            "postingBody": body.group(1).strip() if body else "",
        })
    return head, pairs


class Review:
    def __init__(self, part: str, name: str) -> None:
        self.sheet = HERE / f"sample_sheet_{part}.md"
        self.template = HERE / f"sample_answers_{part}.csv"
        self.answers = HERE / f"answers_{part}_{name}.csv"

    def load_answers(self) -> dict[str, dict]:
        path = self.answers if self.answers.exists() else self.template
        with path.open(encoding="utf-8-sig", newline="") as f:
            return {r["no"]: r for r in csv.DictReader(f)}

    def save(self, no: str, score: str, memo: str) -> dict[str, dict]:
        rows = self.load_answers()
        rows[no].update(my_score=score, memo=memo)
        with self.answers.open("w", encoding="utf-8-sig", newline="") as f:
            w = csv.DictWriter(f, fieldnames=FIELDS, lineterminator="\n")
            w.writeheader()
            w.writerows(sorted(rows.values(), key=lambda r: int(r["no"])))
        return rows


def make_handler(review: Review):
    class Handler(BaseHTTPRequestHandler):
        def send(self, body: bytes, content_type: str) -> None:
            self.send_response(200)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def send_json(self, data) -> None:
            self.send(json.dumps(data, ensure_ascii=False).encode(), "application/json; charset=utf-8")

        def do_GET(self) -> None:
            if self.path == "/":
                self.send(PAGE.read_bytes(), "text/html; charset=utf-8")
            elif self.path == "/api/data":
                head, pairs = parse_sheet(review.sheet)
                self.send_json({"head": head, "pairs": pairs, "answers": review.load_answers(),
                                "file": review.answers.name})
            else:
                self.send_error(404)

        def do_POST(self) -> None:
            if self.path != "/api/answer":
                self.send_error(404)
                return
            body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            self.send_json(review.save(body["no"], body["score"], body["memo"]))

        def log_message(self, *args) -> None:
            pass

    return Handler


def main() -> None:
    if len(sys.argv) != 3 or sys.argv[1] not in ("dev", "test"):
        sys.exit("사용법: python3 review/blind_server.py <dev|test> <검수자 이름>")
    review = Review(sys.argv[1], sys.argv[2])
    server = ThreadingHTTPServer(("127.0.0.1", PORT), make_handler(review))
    url = f"http://127.0.0.1:{PORT}"
    print(f"검수 화면: {url} → 답은 {review.answers} (끝내려면 Ctrl+C)")
    webbrowser.open(url)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
