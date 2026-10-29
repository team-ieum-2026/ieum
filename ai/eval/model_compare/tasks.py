# 주인: AI-A
"""모델 비교 과제 2개. 둘 다 정답이 이미 있어 자동 채점한다.

A. 공고 조건 추출: 생성 공고 본문 → 직무군 · 경력 · 학력 · 필수 기술. 정답은 postings/spec.json
   (실제 공고 라벨링에 쓸 모델을 고르는 과제. 우대 조건의 기술을 필수로 넣으면 틀림)
B. 근거 달린 문장 쓰기: dev 수료생 자료 + 정답 공고 → 기술 요약 · 자기소개서 문장, 문장마다 자료 번호 + 원문 인용.
   인용이 원문에 실제로 있는지, 문장의 숫자가 인용에 있는지 채점 (서류 생성의 핵심만 떼어 낸 축소판)
"""

import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Literal

from schemas.base import CamelModel

EVAL = Path(__file__).resolve().parents[1]

# ---------- A. 공고 조건 추출 ----------

Family = Literal["BE", "FE", "FULLSTACK", "AI", "DATA", "INFRA", "GAME", "EMBEDDED",
                 "DESIGN", "MOBILE", "ANALYST", "PUBLISHER", "NETWORK", "OTHER"]


class PostingConditions(CamelModel):
    job_family: Family
    career: Literal["신입", "경력", "관계없음"]
    education: Literal["학력무관", "고졸", "대졸(2~3년)", "대졸(4년)", "석사", "박사"]
    required_skills: list[str]


SYSTEM_A = "너는 채용 공고에서 지원 조건을 정확히 뽑는 도우미다. 공고에 적힌 내용만 쓰고 추측하지 않는다."

PROMPT_A = """아래 채용 공고에서 지원 조건을 뽑아라.

- jobFamily: BE(백엔드) · FE(프론트엔드) · FULLSTACK · AI(AI 서비스 · LLM · ML) · DATA(데이터 엔지니어) · INFRA(클라우드 · 시스템 운영 · DevOps) · GAME · EMBEDDED(펌웨어 · 임베디드) · DESIGN(웹 · UI 디자이너) · MOBILE(앱) · ANALYST(데이터 분석가) · PUBLISHER(웹 퍼블리셔) · NETWORK · OTHER
- career: 경력 n년 이상을 요구하면 경력, 신입이면 신입, 무관이면 관계없음
- education: 요구 학력. 적혀 있지 않으면 학력무관
- requiredSkills: **자격요건(필수)에 적힌 기술만**. 우대사항 · 우대 조건의 기술은 넣지 않는다. 공고에 적힌 표기 그대로.

[제목] {title}
[본문]
{description}
"""


def _norm(text: str) -> str:
    return re.sub(r"[\s·•ㆍ,.\"'()\[\]→~\-]", "", text).lower()


def items_a(limit: int | None = None) -> list[dict]:
    spec = json.loads((EVAL / "postings/spec.json").read_text(encoding="utf-8"))["postings"]
    jobs = {j["jobPostingId"]: j for j in
            json.loads((EVAL / "postings/out/eval_postings.json").read_text(encoding="utf-8"))["jobs"]}
    picked = spec[::3]  # 91건 중 31건: 문체 · 정답/함정 유형이 고르게 섞이도록 일정 간격
    items = []
    for p in picked[:limit]:
        j = jobs[p["id"]]
        display = p.get("display", {})
        items.append({
            "id": p["id"],
            "prompt": PROMPT_A.format(title=j["title"], description=j["description"]),
            "gold": {"family": p["family"], "career": p["career"], "education": p["edu"],
                     "skills": [[s, display.get(s, s)] for s in p["required"]]},
        })
    return items


def score_a(out: PostingConditions, gold: dict) -> dict:
    pred = [_norm(s) for s in out.required_skills if s.strip()]
    gold_names = [{_norm(n) for n in names} for names in gold["skills"]]
    hit_gold = sum(any(p in g or any(n in p or p in n for n in g) for p in pred) for g in gold_names)
    hit_pred = sum(any(p in g or any(n in p or p in n for n in g) for g in gold_names) for p in pred)
    recall = hit_gold / len(gold_names) if gold_names else 1.0
    precision = hit_pred / len(pred) if pred else (1.0 if not gold_names else 0.0)
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    return {
        "family": out.job_family == gold["family"],
        "career": out.career == gold["career"],
        "education": out.education == gold["education"],
        "skills_f1": round(f1, 3),
        "all_correct": out.job_family == gold["family"] and out.career == gold["career"]
        and out.education == gold["education"] and f1 == 1.0,
    }


# ---------- B. 근거 달린 문장 쓰기 ----------

DEV_STUDENTS = [1, 2, 3, 5, 8]
MIN_QUOTE = 6
_NUMBER = re.compile(r"\d+(?:[.,]\d+)?")


class Cite(CamelModel):
    key: str
    quote: str


class Line(CamelModel):
    section: Literal["SKILLS", "COVER_LETTER"]
    text: str
    evidence: list[Cite] = []


class GroundedDraft(CamelModel):
    sentences: list[Line]


SYSTEM_B = ("너는 국비 교육 수료생의 지원 서류 문장을 쓰는 도우미다. 주어진 [자료]에 있는 사실만 쓴다. "
            "자료에 없는 경험 · 수치 · 역할은 만들지 않는다. 확신이 없으면 그 문장을 쓰지 않는다.")

PROMPT_B = """지원 공고에 맞춰 문장을 써라.

1. SKILLS: 기술 요약 3문장, 개조식("~ 설계 · 구현"). 공고 필수 기술과 겹치는 기술 중심.
2. COVER_LETTER: 자기소개서 5~7문장, 합니다체. 공고와 연결 → 핵심 경험(문제 → 해결 → 결과) → 포부(자기소개서에 있을 때만) 순서.

모든 문장에 evidence 1개 이상: key 는 자료 번호, quote 는 그 자료 원문에서 **글자 그대로 복사한 연속된 구절**(10~80자).
문장에 숫자를 쓰면 그 숫자가 quote 안에 그대로 있어야 한다. 팀 프로젝트를 혼자 한 것처럼 쓰지 않는다.

## 지원 공고
{job}

## 자료
{sources}
"""


@dataclass
class Src:
    key: str
    text: str


def _students() -> dict[int, dict]:
    out = {}
    for path in sorted((EVAL / "personas/students").glob("batch_*.json")):
        for s in json.loads(path.read_text(encoding="utf-8")):
            out[s["id"]] = s
    return out


def items_b(limit: int | None = None) -> list[dict]:
    spec = json.loads((EVAL / "postings/spec.json").read_text(encoding="utf-8"))["postings"]
    jobs = {j["jobPostingId"]: j for j in
            json.loads((EVAL / "postings/out/eval_postings.json").read_text(encoding="utf-8"))["jobs"]}
    students = _students()
    items = []
    for sid in DEV_STUDENTS[:limit]:
        s = students[sid]
        posting = next(p for p in spec if p["sid"] == sid and p.get("level") == "A")
        job = jobs[posting["id"]]
        texts = []
        for p in s["projects"]:
            if p["include_in_apply"]:
                texts += [f"{p['title']} README\n{p['readme']}", f"{p['title']} 역할 기록\n{p['role_note']}"]
        texts.append(f"등록 자기소개서\n{s['cover_letter']}")
        texts += [f"경력 {c['company_name']} {c['position']}\n{c['description']}" for c in s["careers"]]
        sources = [Src(f"S{i}", t) for i, t in enumerate(texts, start=1)]
        items.append({
            "id": f"{sid}-{posting['id']}",
            "prompt": PROMPT_B.format(job=f"{job['companyName']} · {job['title']}\n{job['description']}",
                                      sources="\n\n".join(f"[{x.key}] {x.text}" for x in sources)),
            "gold": {"sources": {x.key: x.text for x in sources}},
        })
    return items


def score_b(out: GroundedDraft, gold: dict) -> dict:
    sources = {k: _norm(v) for k, v in gold["sources"].items()}
    lines = [line for line in out.sentences if line.text.strip()]
    grounded = numbers_ok = 0
    for line in lines:
        quotes = [_norm(c.quote) for c in line.evidence
                  if c.key.strip() in sources and len(_norm(c.quote)) >= MIN_QUOTE and _norm(c.quote) in sources[c.key.strip()]]
        if quotes:
            grounded += 1
            if all(_norm(n) in "".join(quotes) for n in _NUMBER.findall(line.text)):
                numbers_ok += 1
    n = len(lines) or 1
    cover = [x.text for x in lines if x.section == "COVER_LETTER"]
    return {
        "sentences": len(lines),
        "grounded_rate": round(grounded / n, 3),  # 근거 인용이 원문에 실제로 있는 문장 비율
        "kept_rate": round(numbers_ok / n, 3),  # 근거 + 숫자까지 통과 = 서류에 남을 문장 비율
        "cover_chars": sum(len(t) for t in cover),
    }


TASKS = {
    "A": (items_a, PostingConditions, SYSTEM_A, score_a),
    "B": (items_b, GroundedDraft, SYSTEM_B, score_b),
}
