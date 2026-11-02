# 주인: AI-A
"""배정표(assignment.json)대로 서류를 만들고 자동 지표를 남긴다.

실행 (ai 폴더에서):
  .venv/bin/python -m eval.document_set.run_drafts --label v2 --dry     # 호출 없이 요청 조립 · 프롬프트 크기만
  .venv/bin/python -m eval.document_set.run_drafts --label v2           # dev 7건
  .venv/bin/python -m eval.document_set.run_drafts --label v2 --only 5 8
  .venv/bin/python -m eval.document_set.run_drafts --label v3 --group test --final   # 최종 1회만

결과: eval/results/document_draft/<날짜>-<label>-<group>/ (커밋 안 됨)
- s<수료생>.json: 배정 · 응답 시간 · LLM 원본 · 제외 이유별 문장 수 · 생성/남은 문장 수 · 서류
- summary.json + 화면 요약표

draft_document() 를 그대로 부르지 않고 단계를 나눠 부른다. 서류에는 제외 개수만 남고 이유별 개수는 없어서다.
test · real 은 결과를 보면 처음 보는 상태가 깨지므로 --final 없이는 돌지 않는다 (METRICS.md 6장).
"""

import argparse
import json
import statistics
import time
from collections import Counter
from datetime import date
from pathlib import Path

from common.config import get_settings
from common.llm import LLMError, generate_json
from document.llm_models import LlmDraft
from document.prompts import PROMPT_VERSION, SYSTEM_INSTRUCTION, build_prompt
from document.service import assemble
from document.sources import build_sources
from document.verify import verify
from schemas.draft import DraftRequest

AI = Path(__file__).parents[2]
HERE = Path(__file__).parent
OUT = AI / "eval/results/document_draft"


def load_inputs():
    materials = {m["studentId"]: m for m in json.loads((AI / "eval/personas/out/student_materials.json").read_text())}
    jobs = {j["jobPostingId"]: j for j in json.loads((AI / "eval/postings/out/eval_postings.json").read_text())["jobs"]}
    real = json.loads((AI / "data/eval/ieum_jobs_20261028.json").read_text())
    jobs.update({j["jobPostingId"]: j for j in (real if isinstance(real, list) else real["jobs"])})
    companies = {
        "ai/data/eval/companies.json": json.loads((AI / "data/eval/companies.json").read_text())["companies"],
        "ai/eval/document_set/company_info_real.json": json.loads((HERE / "company_info_real.json").read_text())["companies"],
    }
    # BE 는 company_info 의 DB id 를 채워 보낸다. id 가 없으면 기업 정보 근거(C)가 만들어지지 않으므로 (sources.py)
    # 파일 순서로 id 를 붙인다: 평가용 회사 1~, 실제 회사 1001~
    companies = {
        path: {c["companyName"]: {**c, "companyInfoId": start + i} for i, c in enumerate(rows)}
        for (path, rows), start in zip(companies.items(), (1, 1001))
    }
    return materials, jobs, companies


def profile_of(student_id: int):
    """AI-B 역량 프로필 (참고용). 추출이 실패한 수료생은 null"""
    data = json.loads((AI / f"data/eval/profiles/student_{student_id}.json").read_text())
    return data["profile"] if data.get("llmStatus") == "OK" else None


def build_request(row, materials, jobs, companies) -> DraftRequest:
    company = None
    if row["companyInfoFrom"]:
        company = companies[row["companyInfoFrom"]][row["companyName"]]
    return DraftRequest.model_validate({
        "applicationId": row["studentId"],
        "student": materials[row["studentId"]],
        "profile": profile_of(row["studentId"]),
        "job": jobs[row["jobPostingId"]],
        "companyInfo": company,
    })


def generated_count(draft: LlmDraft) -> int:
    sentences = draft.skills_summary + draft.cover_letter + [b for p in draft.projects for b in p.bullets]
    return sum(1 for s in sentences if s.text.strip())


def kept_count(verified) -> int:
    return len(verified.skills_summary) + len(verified.cover_letter) + sum(len(p.bullets) for p in verified.projects)


def run_one(row, req: DraftRequest, model: str | None) -> dict:
    sources = build_sources(req)
    prompt = build_prompt(req, sources)
    result = {"row": row, "promptVersion": PROMPT_VERSION, "model": model, "promptChars": len(prompt)}
    started = time.perf_counter()
    try:
        draft = generate_json(prompt, LlmDraft, system_instruction=SYSTEM_INSTRUCTION,
                              prompt_version=PROMPT_VERSION, model=model)
    except LLMError as e:
        result.update(ok=False, sec=round(time.perf_counter() - started, 1), error=str(e))
        return result
    sec = round(time.perf_counter() - started, 1)
    verified = verify(draft, req, sources)
    document = assemble(req, sources, verified)
    result.update(
        ok=True, sec=sec,
        generated=generated_count(draft), kept=kept_count(verified),
        reasons=dict(verified.reasons),
        llmRaw=draft.model_dump(by_alias=True),
        document=document.model_dump(by_alias=True),
    )
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--label", required=True, help="프롬프트 · 실험 이름 (예: v2)")
    parser.add_argument("--group", default="dev", choices=["dev", "test", "real"])
    parser.add_argument("--final", action="store_true", help="test · real 을 돌릴 때만. 최종 1회")
    parser.add_argument("--only", type=int, nargs="*", help="이 수료생만")
    parser.add_argument("--dry", action="store_true", help="LLM 을 부르지 않고 요청 조립만")
    args = parser.parse_args()
    if args.group != "dev" and not args.final:
        parser.error("test · real 은 --final 을 붙여야 돈다 (결과를 보면 처음 보는 상태가 깨진다)")

    rows = [r for r in json.loads((HERE / "assignment.json").read_text())["rows"] if r["group"] == args.group]
    if args.only:
        rows = [r for r in rows if r["studentId"] in args.only]
    materials, jobs, companies = load_inputs()
    model = get_settings().document_llm_model or None

    if args.dry:
        for row in rows:
            req = build_request(row, materials, jobs, companies)
            prompt = build_prompt(req, build_sources(req))
            print(row["studentId"], row["jobPostingId"], row["companyInfoStatus"], f"프롬프트 {len(prompt)}자")
        return

    out = OUT / f"{date.today():%Y%m%d}-{args.label}-{args.group}"
    out.mkdir(parents=True, exist_ok=True)
    results = []
    for row in rows:
        result = run_one(row, build_request(row, materials, jobs, companies), model)
        (out / f"s{row['studentId']}.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
        results.append(result)
        print(f"{row['studentId']:>3} {row['companyInfoStatus']:<9} {result['sec']:>5}초",
              f"남음 {result['kept']}/{result['generated']}" if result["ok"] else f"실패 {result['error']}",
              result.get("reasons") or "")

    ok = [r for r in results if r["ok"]]
    reasons = Counter()
    for r in ok:
        reasons.update(r["reasons"])
    secs = [r["sec"] for r in ok]
    summary = {
        "label": args.label, "group": args.group, "promptVersion": PROMPT_VERSION, "model": model,
        "documents": len(results), "failed": len(results) - len(ok),
        "generated": sum(r["generated"] for r in ok), "kept": sum(r["kept"] for r in ok),
        "excludedByReason": dict(reasons),
        "secMedian": statistics.median(secs) if secs else None, "secMax": max(secs) if secs else None,
    }
    (out / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    main()
