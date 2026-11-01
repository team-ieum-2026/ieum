# 주인: AI-A
"""공고 379건(생성 91, 실제 138, 일반 150)에서 루브릭 조건을 뽑는다. 모델 3개로 따로 뽑아 투표한다 (LABELING.md 4장).

실행: cd ai && .venv/bin/python -m eval.postings.extract_conditions --model gemini-4-argon [--limit 5] [--ids 613,614] [--redo]
출력: ai/eval/postings/out/conditions/<모델>.json (커밋한다. 라벨을 다시 만들 때 결과가 바뀌지 않게)

- 모델: gemini-3.8-flash, gemini-4-argon (common.llm), gpt-6.1-sol (모델 비교 도구의 호출기, 평가에서만 씀)
- 경력, 학력, 근무지는 정형 칸을 쓴다. 본문에서도 경력과 학력을 읽어, 칸과 다르면 표시한다.
- 직무군과 필수 기술은 정형 칸이 없어 본문에서 뽑는다. 필수 기술은 원문 표기, 표준 이름, 본문 인용을 함께 받는다.
  표준 이름은 수료생 기술 표기에 맞춘다. 원문 표기("자바")만 쓰면 같은 기술인데 기술 부족으로 판정된다.
- 이미 뽑은 공고는 건너뛴다 (--redo 면 다시 뽑음). 루브릭 형식 변환과 투표는 external.py, cross_label.py.
"""

import argparse
import json
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Literal

from common.llm import LLMError, generate_json
from eval.model_compare import providers
from schemas.base import CamelModel

# v2: 기술의 "또는"을 묶음(options)으로, 두 직무를 함께 뽑는 공고는 직무군 2개까지
PROMPT_VERSION = "external-conditions-v2"

AI_DIR = Path(__file__).resolve().parents[2]
SOURCES = {
    "GENERATED": AI_DIR / "eval" / "postings" / "out" / "eval_postings.json",
    "REAL": AI_DIR / "data" / "eval" / "ieum_jobs_20261028.json",
    "GENERAL": AI_DIR / "data" / "eval" / "jobs_general.json",
}
STUDENTS_GLOB = AI_DIR / "eval" / "personas" / "students"
OUT_DIR = Path(__file__).parent / "out" / "conditions"
MODELS = ("gemini-3.8-flash", "gemini-4-argon", "gpt-6.1-sol")
WORKERS = 8

# 모델 비교 과제 A 와 같은 직무군. 수료생 직무군(BE ~ EMBEDDED) 밖의 공고는 모든 수료생에게 직무 불일치다
Family = Literal["BE", "FE", "FULLSTACK", "AI", "DATA", "INFRA", "GAME", "EMBEDDED",
                 "DESIGN", "MOBILE", "ANALYST", "PUBLISHER", "NETWORK", "OTHER"]
BodyCareer = Literal["신입", "경력", "관계없음", "적혀 있지 않음"]
BodyEducation = Literal["학력무관", "고졸", "대졸(2~3년)", "대졸(4년)", "석사", "박사", "적혀 있지 않음"]


class RequiredSkill(CamelModel):
    original: str  # 공고에 적힌 표기 ("ESP32 or Aduino")
    options: list[str]  # 표준 이름. 2개 이상이면 그중 하나만 있으면 된다 (["ESP32", "Arduino"])
    quote: str  # 그 기술이 필수로 적힌 본문 구절


class ExtractedConditions(CamelModel):
    job_families: list[Family]  # 보통 1개. "A 또는 B 개발자"처럼 두 직무를 함께 뽑을 때만 2개
    family_quote: str  # 직무군을 판단한 본문 구절
    required_skills: list[RequiredSkill]
    body_career: BodyCareer
    body_education: BodyEducation


SYSTEM = "너는 채용 공고에서 지원 조건을 정확히 뽑는 도우미다. 공고에 적힌 내용만 쓰고 추측하지 않는다."

PROMPT = """아래 채용 공고에서 지원 조건을 뽑아라.

- jobFamilies: BE(백엔드) · FE(프론트엔드) · FULLSTACK · AI(AI 서비스 · LLM · ML) · DATA(데이터 엔지니어) · INFRA(클라우드 · 시스템 운영 · DevOps) · GAME · EMBEDDED(펌웨어 · 임베디드) · DESIGN(웹 · UI 디자이너) · MOBILE(앱) · ANALYST(데이터 분석가) · PUBLISHER(웹 퍼블리셔) · NETWORK · OTHER
  보통 1개를 고른다. 공고가 "웹서비스 개발자 또는 F/W 개발자"처럼 서로 다른 두 직무 중 하나를 뽑는다고 명시할 때만 2개를 고른다.
  familyQuote: 직무군을 판단한 본문 구절을 그대로 복사한다.
- requiredSkills: 자격요건(필수)에 적힌 기술만. 우대사항 · 우대 조건의 기술은 넣지 않는다. 기술이 적혀 있지 않으면 빈 배열.
  - original: 공고에 적힌 표기 그대로
  - options: 표준 이름 목록. 보통 1개다. "A 또는 B", "A or B", "A/B 중 하나"처럼 둘 중 하나만 있으면 되는 기술은 한 항목의 options 에 함께 넣는다.
    예: "ESP32 or Arduino" → options ["ESP32", "Arduino"]. "C/Python"처럼 슬래시로 묶은 기술도 둘 중 하나로 본다 → options ["C", "Python"].
    "Java, Python" 처럼 쉼표로 나열해 둘 다 필요하면 항목을 나눈다.
    표준 이름은 아래 목록에 같은 기술이 있으면 목록의 표기를 그대로 쓰고, 없으면 영문 공식 이름을 쓴다.
    목록: {vocabulary}
  - quote: 그 기술이 적힌 본문 구절을 그대로 복사한다.
- bodyCareer: 본문에 적힌 경력 요건. 경력 n년 이상이면 경력, 신입이면 신입, 무관이면 관계없음, 없으면 "적혀 있지 않음"
- bodyEducation: 본문에 적힌 학력 요건. 없으면 "적혀 있지 않음"

[제목] {title}
[본문]
{description}
"""


def student_skills() -> list[str]:
    skills: set[str] = set()
    for path in sorted(STUDENTS_GLOB.glob("batch_*.json")):
        for s in json.loads(path.read_text(encoding="utf-8")):
            skills.update(s["skills"])
    return sorted(skills)


def load_postings() -> list[tuple[str, dict]]:
    postings = []
    for source, path in SOURCES.items():
        data = json.loads(path.read_text(encoding="utf-8"))
        jobs = next(v for v in data.values() if isinstance(v, list))
        postings += [(source, j) for j in jobs]
    return postings


def ask(model: str, prompt: str) -> ExtractedConditions:
    if model.startswith("gpt"):
        result = providers.call(model, SYSTEM, prompt, ExtractedConditions)
        if result.parsed is None:
            raise LLMError(result.error or "GPT 응답 형식 오류")
        return result.parsed
    return generate_json(prompt, ExtractedConditions, system_instruction=SYSTEM,
                         prompt_version=PROMPT_VERSION, model=model)


def extract(model: str, source: str, job: dict, vocabulary: str) -> dict:
    prompt = PROMPT.format(vocabulary=vocabulary, title=job["title"], description=job["description"] or "")
    out = ask(model, prompt)
    fields = {"career": job["career"], "education": job["education"], "location": job["location"]}
    mismatch = []
    if out.body_career != "적혀 있지 않음" and out.body_career != job["career"]:
        mismatch.append("career")
    if out.body_education != "적혀 있지 않음" and out.body_education != job["education"]:
        mismatch.append("education")
    return {
        "source": source,
        "title": job["title"],
        "jobCategory": job["jobCategory"],
        "fields": fields,
        "extracted": out.model_dump(by_alias=True),
        "fieldBodyMismatch": mismatch,  # 정형 칸과 본문이 다른 조건
        "model": model,
        "promptVersion": PROMPT_VERSION,
    }


def main() -> None:
    parser = argparse.ArgumentParser(prog="extract_conditions")
    parser.add_argument("--model", choices=MODELS, required=True)
    parser.add_argument("--limit", type=int, help="앞에서부터 N건만 (확인용)")
    parser.add_argument("--ids", help="쉼표로 구분한 jobPostingId 만")
    parser.add_argument("--redo", action="store_true", help="이미 뽑은 공고도 다시 뽑기")
    args = parser.parse_args()

    out_path = OUT_DIR / f"{args.model}.json"
    done = json.loads(out_path.read_text(encoding="utf-8")) if out_path.exists() else {}
    postings = load_postings()
    if args.ids:
        wanted = {int(i) for i in args.ids.split(",")}
        postings = [(s, j) for s, j in postings if j["jobPostingId"] in wanted]
    if args.limit:
        postings = postings[:args.limit]
    todo = [(s, j) for s, j in postings if args.redo or str(j["jobPostingId"]) not in done]
    print(f"[{args.model}] 공고 {len(postings)}건 중 새로 뽑을 {len(todo)}건 (호출 {len(todo)}번, 형식 오류 시 더)")

    vocabulary = ", ".join(student_skills())
    failed = []

    def run(item: tuple[str, dict]) -> None:
        source, job = item
        try:
            done[str(job["jobPostingId"])] = extract(args.model, source, job, vocabulary)
        except LLMError as e:
            failed.append((job["jobPostingId"], str(e)))

    # common/llm.py 의 클라이언트는 처음 동시에 만들면 하나가 닫혀 실패한다. 첫 건을 혼자 처리해 클라이언트를 만든 뒤 동시에 돌린다
    if todo:
        run(todo[0])
    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        list(pool.map(run, todo[1:]))

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    ordered = dict(sorted(done.items(), key=lambda kv: int(kv[0])))
    out_path.write_text(json.dumps(ordered, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"저장: {out_path} (전체 {len(ordered)}건, 실패 {len(failed)}건)")
    for pid, err in failed:
        print(f"  실패 {pid}: {err}")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
