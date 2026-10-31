# 주인: AI-A
"""정답 · 함정 공고 91건(out/eval_postings.json) → job_posting 적재 SQL (out/eval_postings.sql).

실행: python ai/eval/postings/build_postings_sql.py
형식은 AI-B 의 ai/data/eval/eval_jobs.sql (일반 공고 150건)과 같다.
- id 900201~ 고정, source 'EVAL', external_id 'eval-<id>', raw NULL
- content_hash = SHA-256(description) (BE SeoulJobParser 와 같은 계산)
- job_code = application.yml it-job-codes 의 코드 (실제 공고와 형식을 맞추려고 채운다. BE 는 IT 직종 코드를 동기화할 때만 보고,
  AI 색인은 is_active 만 보므로 SQL 로 넣은 공고는 job_code 와 상관없이 추천 후보에 들어간다)
- 다시 실행해도 ON DUPLICATE KEY UPDATE 로 같은 id 를 덮어써 중복이 생기지 않는다
"""

import hashlib
import json
from datetime import date
from pathlib import Path

HERE = Path(__file__).parent
TODAY = date(2026, 10, 29)

# jobCategory → JOBS_CD (backend application.yml it-job-codes 와 같은 코드)
JOB_CODE = {
    "시스템 소프트웨어 개발자": "133100",
    "펌웨어 및 임베디드 소프트웨어 프로그래머": "133102",
    "응용 소프트웨어 개발자": "133200",
    "모바일 애플리케이션 프로그래머": "133207",
    "웹 개발자": "133300",
    "데이터 설계 및 프로그래머": "135101",
    "정보시스템 운영자": "136101",
    "네트워크 관리자": "136102",
    "웹 디자이너": "415501",
}

COLUMNS = ["id", "source", "external_id", "company_name", "title", "job_code", "job_category", "location",
           "work_address", "employment_type", "career", "education", "salary_text", "description",
           "deadline_on", "until_filled", "posted_on", "is_active", "content_hash"]


def q(value) -> str:
    """MySQL 문자열 리터럴. 역슬래시 · 작은따옴표 · 줄바꿈을 이스케이프한다."""
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, int):
        return str(value)
    text = str(value).replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")
    return f"'{text}'"


def row(j: dict) -> str:
    description = j["description"]
    deadline = j.get("deadlineOn")
    active = bool(j.get("untilFilled")) or (deadline is not None and date.fromisoformat(deadline) >= TODAY)
    values = [
        j["jobPostingId"], "EVAL", f"eval-{j['jobPostingId']}", j["companyName"], j["title"],
        JOB_CODE[j["jobCategory"]], j["jobCategory"], j.get("location"), j.get("workAddress"),
        j.get("employmentType"), j.get("career"), j.get("education"), j.get("salaryText"), description,
        deadline, bool(j.get("untilFilled")), j.get("postedOn"), active,
        hashlib.sha256(description.encode("utf-8")).hexdigest(),
    ]
    return " (" + ", ".join(q(v) for v in values) + ")"


def main() -> None:
    jobs = json.loads((HERE / "out/eval_postings.json").read_text(encoding="utf-8"))["jobs"]
    ids = [j["jobPostingId"] for j in jobs]
    header = f"""-- =====================================================================
-- 매칭 평가용 정답 · 함정 공고 {len(jobs)}건 (AI-A 생성 · 원본 ai/eval/postings/out/eval_postings.json)
-- 이 파일은 build_postings_sql.py 가 만든다. 직접 고치지 말고 spec.json → render_postings.py → 이 스크립트 순서로 다시 만든다.
-- 실행: mysql -u <계정> -p ieum < ai/eval/postings/out/eval_postings.sql   (schema.sql · seed.sql 실행 뒤)
-- - id {min(ids)}~{max(ids)} 고정, source 'EVAL', external_id 'eval-<id>'. 형식은 ai/data/eval/eval_jobs.sql (AI-B 일반 공고 150건)과 같다
-- - 다시 실행해도 중복되지 않는다: ON DUPLICATE KEY UPDATE 로 같은 id 를 덮어쓴다
-- - content_hash = SHA-256(description), job_code = application.yml it-job-codes, raw = NULL
-- - is_active = 마감일이 {TODAY} 이후면 1. BE 배치는 source 'SEOUL_API' 만 끄므로 이 공고는 켜진 채 남는다
-- 빼기: DELETE FROM job_posting WHERE source = 'EVAL' AND id BETWEEN 900201 AND 900300;
-- 주의: id 를 직접 넣으므로 AUTO_INCREMENT 가 {max(ids) + 1} 로 올라간다. 공고 배치(BE-A)보다 **먼저** 실행한다.
--       배치 공고가 먼저 900201 이후 id 를 받으면 이 파일이 그 공고를 덮어쓴다.
-- 모든 회사 · 주소 · 본문은 가상 데이터, 연락처는 [연락처 생략].
-- =====================================================================
USE ieum;
SET NAMES utf8mb4;

INSERT INTO job_posting ({", ".join(COLUMNS)}) VALUES
"""
    update = ",\n".join(f"  {c} = new.{c}" for c in COLUMNS[3:]) + ",\n  synced_at = NOW();\n"
    sql = header + ",\n".join(row(j) for j in jobs) + "\nAS new\nON DUPLICATE KEY UPDATE\n" + update
    (HERE / "out/eval_postings.sql").write_text(sql, encoding="utf-8")
    print(f"{len(jobs)}건 → {HERE / 'out/eval_postings.sql'}")


if __name__ == "__main__":
    main()
