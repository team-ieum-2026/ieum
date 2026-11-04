# 주인: AI-A
"""사람 채점 화면 (HTML 파일 하나) 만들기 (DOCUMENT_LABELING.md 6장).

실행 (ai 폴더에서, GPT 와 Claude 채점이 끝난 뒤):
  .venv/bin/python -m eval.document_grade.build_human_page --name v3.9.4-official

결과: eval/results/document_grade/<이름>/human_review.html
- 인터넷, 서버 없이 브라우저로 연다. 채점 중 내용은 브라우저에 임시 저장, 끝나면 CSV 로 내려받아 보낸다
- LLM 점수, 생성 모델 이름, 프롬프트 버전, 어느 쪽이 재작성인지는 넣지 않는다

단계
1. 무작위 30문장 (key.json 의 sample). 다른 단계는 1단계를 마쳐야 열린다
2. 두 LLM 점수가 갈린 문장 (1단계에 이미 있으면 뺀다)
3. 두 LLM 판정(A 우세, B 우세, 동등)이 갈린 반영도
4. 기업 관련 문장 전부: 기업 정보 조작인지
"""

import argparse
import json
from pathlib import Path

AI = Path(__file__).parents[2]
GRADE = AI / "eval/results/document_grade"
TEMPLATE = Path(__file__).parent / "human_page.html"


def grades(name: str, grader: str) -> dict[str, dict]:
    out = {}
    for f in (GRADE / name / grader).glob("*.json"):
        data = json.loads(f.read_text())
        out[f.stem] = data.get("result", data)
    return out


def verdict(reflection: dict | None) -> str | None:
    """합계 차이 2점 이상이면 높은 쪽 우세, 아니면 동등 (4장)"""
    if not reflection:
        return None
    total = {k: sum(reflection[k][i] for i in ("duties", "skills", "preferred", "connection")) for k in ("A", "B")}
    diff = total["A"] - total["B"]
    return "A" if diff >= 2 else "B" if diff <= -2 else "EQUAL"


def materials_text(m: dict) -> str:
    """등록 자료를 사람이 읽기 쉬운 글로"""
    lines = []
    for p in m.get("projects", []):
        lines += [f"[프로젝트] {p['title']} ({p.get('periodText') or ''}, {p.get('teamSize') or '?'}명)",
                  f"기술: {', '.join(p.get('techStack') or [])}", "역할 기록:", p.get("roleNote") or "(없음)",
                  "README:", p.get("readme") or "(없음)", ""]
    for c in m.get("careers", []):
        lines += [f"[경력] {c.get('companyName')} · {c.get('position') or ''} ({c.get('startedOn')} ~ {c.get('endedOn') or '재직 중'})",
                  c.get("description") or "", ""]
    for c in m.get("certificates", []):
        lines.append(f"[자격증] {c.get('name')} ({c.get('issuer') or ''}, {c.get('acquiredOn') or ''})")
    for e in m.get("education", []):
        lines.append(f"[학력] {e.get('schoolName')} {e.get('major') or ''} {e.get('status') or ''}")
    if m.get("course"):
        lines.append(f"[교육 과정] {m['course'].get('name')} ({m['course'].get('startedOn')} ~ {m['course'].get('endedOn')})")
    lines.append(f"[보유 기술] {', '.join(m.get('skills') or [])}")
    letter = (m.get("coverLetter") or {}).get("content")
    lines += ["", "[등록 자기소개서]", letter or "(없음)"]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--name", default="v3.9.4-official")
    args = parser.parse_args()
    base = GRADE / args.name
    docs = json.loads((base / "sheet.json").read_text())["documents"]
    key = json.loads((base / "key.json").read_text())
    gpt, claude = grades(args.name, "gpt"), grades(args.name, "claude")
    if len(gpt) != len(docs) or len(claude) != len(docs):
        raise SystemExit(f"채점 결과가 모자란다: GPT {len(gpt)}, Claude {len(claude)}, 서류 {len(docs)}")

    sample = set(key["sample"])
    by_id = {}
    for d in docs:
        g = {s["sentenceId"]: s for s in gpt[d["docId"]]["sentences"]}
        c = {s["sentenceId"]: s for s in claude[d["docId"]]["sentences"]}
        for s in d["sentences"]:
            by_id[s["id"]] = (d, s, g[s["id"]], c[s["id"]])

    def card(sid):
        d, s, _, _ = by_id[sid]
        return {"id": sid, "doc": d["docId"], "where": s["where"], "text": s["text"], "evidence": s["evidence"]}

    stage1 = [card(i) for i in key["sample"]]
    stage2 = [card(i) for i, (_, _, g, c) in by_id.items() if g["score"] != c["score"] and i not in sample]
    stage3 = [{"doc": d["docId"], "A": d["reflection"]["A"], "B": d["reflection"]["B"]} for d in docs
              if d["reflection"] and verdict(gpt[d["docId"]]["reflection"]) != verdict(claude[d["docId"]]["reflection"])]
    company_ids = [i for i, (_, s, g, c) in by_id.items()
                   if g["companyRelated"] or c["companyRelated"] or any(e["type"] == "COMPANY_INFO" for e in s["evidence"])]
    stage4 = [card(i) for i in company_ids]

    doc_view = {d["docId"]: {"job": d["job"], "companyInfo": d["companyInfo"], "status": d["companyInfoStatus"],
                             "materials": materials_text(d["materials"])} for d in docs}
    data = {"name": args.name, "stages": [stage1, stage2, stage3, stage4], "docs": doc_view}
    html = TEMPLATE.read_text().replace("/*DATA*/null", json.dumps(data, ensure_ascii=False).replace("</", "<\\/"))
    (base / "human_review.html").write_text(html)
    # 사람 채점 대상 목록은 집계에서 쓴다
    (base / "human_targets.json").write_text(json.dumps(
        {"stage1": [x["id"] for x in stage1], "stage2": [x["id"] for x in stage2],
         "stage3": [x["doc"] for x in stage3], "stage4": [x["id"] for x in stage4]}, indent=1) + "\n")
    print(f"1단계 {len(stage1)}문장, 2단계 {len(stage2)}문장, 3단계 {len(stage3)}건, 4단계 {len(stage4)}문장 → {base / 'human_review.html'}")


if __name__ == "__main__":
    main()
