# 주인: AI-A
"""LLM 서류 채점 (DOCUMENT_LABELING.md 6장). GPT 와 Claude 에 같은 지시문을 준다.

실행 (ai 폴더에서):
  .venv/bin/python -m eval.document_grade.grade_llm prompts --name v3.9.4-official   # 서류별 지시문 파일 (Claude 에 줌)
  .venv/bin/python -m eval.document_grade.grade_llm gpt --name v3.9.4-official       # GPT 채점
  .venv/bin/python -m eval.document_grade.grade_llm check --name v3.9.4-official --grader claude  # 결과 검사

결과: eval/results/document_grade/<이름>/
- prompts/<docId>.md: 서류별 지시문 전문 (기준표 2, 4, 5장 + 채점 시트). 해시는 prompts/hash.txt
- <grader>/<docId>.json: 채점 결과 (GradeResult 형식). GPT 는 실제 모델 버전, 토큰도 남긴다
"""

import argparse
import hashlib
import json
import re
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from pydantic import BaseModel, Field

from eval.model_compare.providers import call

AI = Path(__file__).parents[2]
GRADE = AI / "eval/results/document_grade"
LABELING = AI / "eval/DOCUMENT_LABELING.md"
GPT_MODEL = "gpt-6.1-sol"


class Claim(BaseModel):
    claim: str
    score: int = Field(ge=0, le=2)


class SentenceGrade(BaseModel):
    sentenceId: str
    claims: list[Claim]
    score: int = Field(ge=0, le=2)  # 주장 점수 중 가장 낮은 값
    companyRelated: bool  # 회사의 제품, 사업, 문화, 인재상, 이슈를 말하는 문장인가 (5장)
    fabricated: bool  # 기업 정보, 공고에 없는 회사 정보를 사실처럼 썼는가 (5장)
    reason: str


class ReflectionScore(BaseModel):
    duties: int = Field(ge=0, le=2)  # 주요 업무와 관련된 경험
    skills: int = Field(ge=0, le=2)  # 필수 기술과 역량
    preferred: int = Field(ge=0, le=2)  # 우대 조건
    connection: int = Field(ge=0, le=2)  # 경험과 직무의 연결
    reason: str


class Reflection(BaseModel):
    A: ReflectionScore
    B: ReflectionScore


class GradeResult(BaseModel):
    sentences: list[SentenceGrade]
    reflection: Reflection | None = None  # 반영도 비교가 없는 서류는 null


def rubric() -> str:
    """기준표 2장, 4장, 5장 원문"""
    text = LABELING.read_text()
    parts = re.split(r"(?m)^## ", text)
    keep = [p for p in parts if p.startswith(("2. ", "4. ", "5. "))]
    return "\n".join("## " + p for p in keep)


SYSTEM = """너는 취업 서류의 사실성을 채점하는 평가자다. 아래 기준표만 따른다. 문장이 화려한지는 보지 않는다.
채점자는 문장, 그 문장에 연결된 근거, 수료생의 등록 자료 원문, 지원 공고, 기업 정보를 본다.
근거는 문장에 연결된 것만 본다. 등록 자료의 다른 곳에 같은 내용이 있어도 연결되지 않았으면 근거가 없는 것으로 본다.
"""


def build_prompt(doc: dict) -> str:
    sentences = [{"sentenceId": s["id"], "where": s["where"], "text": s["text"], "evidence": s["evidence"]} for s in doc["sentences"]]
    parts = [
        "# 기준표\n", rubric(),
        "\n# 할 일\n",
        "1. sentences 의 모든 문장을 2장 기준으로 채점한다. 문장을 주장 단위로 나눠 claims 에 주장별 점수를 쓰고, "
        "문장 점수(score)는 가장 낮은 주장 점수다. 문장 하나도 빼지 않는다.",
        "2. 문장마다 companyRelated(기업 관련 문장인지)와 fabricated(기업 정보 조작인지)를 5장 기준으로 표시한다. "
        "기업 관련이 아니면 fabricated 는 false.",
        "3. reflection 이 있으면 A 와 B 를 4장 표로 각각 채점한다. 어느 쪽이 원본인지 추측하지 않는다. reflection 이 null 이면 결과도 null.",
        "4. reason 은 한국어 한두 문장. 0점, 1점이면 무엇이 근거와 다른지 쓴다.",
        "\n# 채점 시트\n",
        "## 지원 공고\n" + json.dumps(doc["job"], ensure_ascii=False, indent=1),
        f"## 기업 정보 (상태 {doc['companyInfoStatus']})\n" + json.dumps(doc["companyInfo"], ensure_ascii=False, indent=1),
        "## 수료생 등록 자료 원문\n" + json.dumps(doc["materials"], ensure_ascii=False, indent=1),
        "## sentences (채점할 AI 문장)\n" + json.dumps(sentences, ensure_ascii=False, indent=1),
        "## reflection (반영도 비교용 자기소개서 A, B)\n" + json.dumps(doc["reflection"], ensure_ascii=False, indent=1),
        "\n# 출력\n다음 JSON 스키마에 맞는 JSON 객체 하나만 출력한다.\n"
        + json.dumps(GradeResult.model_json_schema(), ensure_ascii=False),
    ]
    return "\n".join(parts)


def check(doc: dict, result: GradeResult) -> list[str]:
    """집계 전 자동 검사 (6장): 문장 ID 빠짐, 겹침, 주장 최저값과 문장 점수, 반영도 유무"""
    want = [s["id"] for s in doc["sentences"]]
    got = [s.sentenceId for s in result.sentences]
    problems = []
    if sorted(got) != sorted(want):
        problems.append(f"문장 ID 다름: 빠짐 {sorted(set(want) - set(got))}, 남음 {sorted(set(got) - set(want))}, 겹침 {len(got) - len(set(got))}")
    for s in result.sentences:
        if s.claims and min(c.score for c in s.claims) != s.score:
            problems.append(f"{s.sentenceId}: 주장 최저값과 문장 점수가 다름")
    if (doc["reflection"] is None) != (result.reflection is None):
        problems.append("반영도 유무가 시트와 다름")
    return problems


def load(name: str) -> list[dict]:
    return json.loads((GRADE / name / "sheet.json").read_text())["documents"]


def write_prompts(name: str):
    out = GRADE / name / "prompts"
    out.mkdir(parents=True, exist_ok=True)
    digest = hashlib.sha256()
    for doc in load(name):
        text = SYSTEM + "\n" + build_prompt(doc)
        (out / f"{doc['docId']}.md").write_text(text)
        digest.update(text.encode())
    (out / "hash.txt").write_text(digest.hexdigest() + "\n")
    print(f"지시문 {len(load(name))}개 → {out}")


def grade_gpt(name: str, only: list[str] | None):
    out = GRADE / name / "gpt"
    out.mkdir(parents=True, exist_ok=True)
    docs = [d for d in load(name) if not only or d["docId"] in only]

    def one(doc):
        for attempt in range(3):
            r = call(GPT_MODEL, SYSTEM, build_prompt(doc), GradeResult)
            problems = check(doc, r.parsed) if r.parsed else [r.error or "파싱 실패"]
            if not problems:
                (out / f"{doc['docId']}.json").write_text(json.dumps({
                    "model": GPT_MODEL, "version": r.version, "attempt": attempt + 1,
                    "tokens": [r.input_tokens, r.output_tokens], "latency": round(r.latency_s, 1),
                    "result": r.parsed.model_dump()}, ensure_ascii=False, indent=1) + "\n")
                return f"{doc['docId']} 완료 ({r.latency_s:.0f}초, {attempt + 1}회)"
        return f"{doc['docId']} 실패: {problems[:2]}"

    with ThreadPoolExecutor(max_workers=6) as pool:
        for line in pool.map(one, docs):
            print(line, flush=True)


def check_grader(name: str, grader: str) -> bool:
    ok = True
    for doc in load(name):
        f = GRADE / name / grader / f"{doc['docId']}.json"
        if not f.exists():
            print(doc["docId"], "결과 없음"); ok = False; continue
        data = json.loads(f.read_text())
        problems = check(doc, GradeResult.model_validate(data.get("result", data)))
        if problems:
            print(doc["docId"], problems); ok = False
    print("검사 통과" if ok else "검사 실패")
    return ok


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["prompts", "gpt", "check"])
    parser.add_argument("--name", default="v3.9.4-official")
    parser.add_argument("--grader", default="gpt")
    parser.add_argument("--only", nargs="*")
    args = parser.parse_args()
    if args.action == "prompts":
        write_prompts(args.name)
    elif args.action == "gpt":
        grade_gpt(args.name, args.only)
    else:
        sys.exit(0 if check_grader(args.name, args.grader) else 1)


if __name__ == "__main__":
    main()
