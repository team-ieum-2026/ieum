# 주인: AI-A
"""서류 채점 시트 만들기 (DOCUMENT_LABELING.md 1장, 6장).

실행 (ai 폴더에서):
  .venv/bin/python -m eval.document_grade.make_sheet --runs 20261104-v3.9.4-official-test 20261104-v3.9.4-official-real

결과: eval/results/document_grade/<이름>/ (커밋 안 됨)
- sheet.json: 서류마다 AI 문장(근거 포함), 등록 자료, 공고, 기업 정보, 반영도용 A/B 자기소개서
  채점자(GPT, Claude, 사람)에게 주는 파일이다. 생성 모델 이름, 프롬프트 버전, 규칙 검증 결과는 넣지 않는다
- key.json: A/B 중 어느 쪽이 재작성인지, 무작위 30문장 목록. 채점자에게 주지 않는다

문장 ID 는 <묶음 글자><수료생 번호>-<sentenceId> (예: t12-cl-3, r4-cl-1). test 와 real 에 같은 수료생 번호가 있어서다. 무작위 30문장은 시드를 고정해 뽑는다.
"""

import argparse
import json
import random
from pathlib import Path

from eval.document_set.run_drafts import OUT as RUNS, build_request, load_inputs

AI = Path(__file__).parents[2]
GRADE = AI / "eval/results/document_grade"
SEED = 20261104
SAMPLE = 30


def ai_sentences(document: dict, doc_id: str) -> list[dict]:
    """source 가 AI 인 문장만. 기술 요약, 프로젝트 bullet, 자기소개서"""
    out = []
    for section in document["sections"]:
        name = section["section"]
        if name == "SKILLS":
            groups = [("기술 요약", section["summary"])]
        elif name == "PROJECTS":
            groups = [(f"프로젝트 '{p['title']}'", p["bullets"]) for p in section["items"]]
        elif name == "COVER_LETTER":
            groups = [("자기소개서", section["sentences"])]
        else:
            continue
        for where, sentences in groups:
            for s in sentences:
                if s["source"] != "AI":
                    continue
                out.append({
                    "id": f"{doc_id}-{s['sentenceId']}", "where": where, "text": s["text"],
                    "evidence": [{"type": e["refType"], "label": e["label"], "quote": e["quote"]} for e in s["evidence"]],
                })
    return out


def materials_view(m: dict) -> dict:
    """등록 자료 원문. 이름, 이메일 같은 기본 정보는 채점에 필요 없어 뺀다"""
    return {k: m[k] for k in ("course", "education", "careers", "certificates", "skills", "projects", "coverLetter") if k in m}


def rewritten_letter(document: dict) -> str:
    section = next(s for s in document["sections"] if s["section"] == "COVER_LETTER")
    paragraphs, last = [], object()
    for s in section["sentences"]:
        if s.get("paragraph") != last or not paragraphs:
            paragraphs.append([])
            last = s.get("paragraph")
        paragraphs[-1].append(s["text"])
    return "\n\n".join(" ".join(p) for p in paragraphs)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--runs", nargs="+", required=True, help="eval/results/document_draft 아래 실행 폴더 이름")
    parser.add_argument("--name", default="v3.9.4-official")
    args = parser.parse_args()

    materials, jobs, companies = load_inputs()
    rng = random.Random(SEED)
    docs, key = [], {"seed": SEED, "runs": args.runs, "reflection": {}, "sample": []}
    for run in args.runs:
        for f in sorted((RUNS / run).glob("s[0-9]*.json"), key=lambda p: int(p.stem[1:])):
            result = json.loads(f.read_text())
            if not result["ok"]:
                raise SystemExit(f"{f} 실행 실패 서류가 있다. 다시 생성한 뒤 시트를 만든다")
            row = result["row"]
            req = build_request(row, materials, jobs, companies)
            sid = row["studentId"]
            # test 와 real 에 같은 수료생 번호가 있어 묶음 글자를 붙인다 (t12, r4)
            doc_id = f"{row['group'][0]}{sid}"
            original = (req.student.cover_letter.content if req.student.cover_letter else "") or ""
            rewritten = rewritten_letter(result["document"])
            rewritten_is_a = rng.random() < 0.5
            key["reflection"][doc_id] = "A" if rewritten_is_a else "B"
            job = req.job.model_dump(by_alias=True, include={"company_name", "title", "job_category", "career", "education", "description"})
            company = req.company_info.model_dump(by_alias=True, exclude={"sources", "company_info_id"}) if req.company_info else None
            docs.append({
                "docId": doc_id, "studentId": sid,
                "companyInfoStatus": result["document"]["companyInfoStatus"],
                "job": job, "companyInfo": company, "materials": materials_view(materials[sid]),
                "sentences": ai_sentences(result["document"], doc_id),
                # 원본 자기소개서가 없거나 2문장보다 짧으면 반영도에서 뺀다 (DOCUMENT_LABELING.md 4장)
                "reflection": {"A": rewritten if rewritten_is_a else original, "B": original if rewritten_is_a else rewritten}
                if original.count(".") >= 2 else None,
            })
    all_ids = [s["id"] for d in docs for s in d["sentences"]]
    key["sample"] = sorted(rng.sample(all_ids, SAMPLE))

    out = GRADE / args.name
    out.mkdir(parents=True, exist_ok=True)
    (out / "sheet.json").write_text(json.dumps({"documents": docs}, ensure_ascii=False, indent=1) + "\n")
    (out / "key.json").write_text(json.dumps(key, ensure_ascii=False, indent=1) + "\n")
    print(f"서류 {len(docs)}건, AI 문장 {len(all_ids)}개, 반영도 비교 {sum(d['reflection'] is not None for d in docs)}건 → {out}")


if __name__ == "__main__":
    main()
