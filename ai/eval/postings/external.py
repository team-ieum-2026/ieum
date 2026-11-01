# 주인: AI-A
"""모델별 추출 결과(out/conditions/<모델>.json) → 루브릭 명세 형식. LLM 을 부르지 않는다 (LABELING.md 4장).

- 공고마다 모델별 루브릭 명세를 모은다. 투표(쌍 점수의 중앙값)는 cross_label.py 가 한다.
- external_fixes.json: 사람이 검수하며 정한 값. 이 파일에 있는 공고는 모델 투표 대신 사람 값을 쓴다.
  공고 id 별로 루브릭 칸(family, career, education, location, required)을 적는다. 빠진 칸은 첫 모델의 값을 쓴다.
  예: {"710": {"family": ["BE"], "required": ["Java"], "note": "본문 3번 항목이 필수 요건"}}
"""

import json
from pathlib import Path

HERE = Path(__file__).parent
CONDITIONS_DIR = HERE / "out" / "conditions"
FIXES = HERE / "external_fixes.json"
RUBRIC_KEYS = ("family", "career", "education", "location", "required")


def to_posting(record: dict) -> dict:
    """추출 결과 → 루브릭 명세 형식 (rubric.violations 의 posting).

    경력, 학력은 본문에 명시돼 있으면 본문을, 아니면 정형 칸을 쓴다 (사람이 공고를 읽고 판단하는 것과 같게).
    """
    e = record["extracted"]
    fields = record["fields"]
    career = e["bodyCareer"] if e["bodyCareer"] != "적혀 있지 않음" else fields["career"]
    education = e["bodyEducation"] if e["bodyEducation"] != "적혀 있지 않음" else fields["education"]
    return {
        "family": e["jobFamilies"],
        "career": career,
        "education": education,
        "location": fields["location"],
        "required": ["|".join(s["options"]) for s in e["requiredSkills"] if s["options"]],
    }


def load_fixes() -> dict:
    return json.loads(FIXES.read_text(encoding="utf-8")) if FIXES.exists() else {}


def load_by_posting() -> dict[int, dict]:
    """{공고 id: {"source": 출처, "models": {모델: 루브릭 명세}}}. 모델 결과 파일이 없으면 빈 dict"""
    postings: dict[int, dict] = {}
    for path in sorted(CONDITIONS_DIR.glob("*.json")):
        for pid, record in json.loads(path.read_text(encoding="utf-8")).items():
            entry = postings.setdefault(int(pid), {"source": record["source"], "models": {}})
            entry["models"][path.stem] = to_posting(record)
    return postings


def fixed_posting(pid: int, entry: dict, fixes: dict) -> dict | None:
    """사람이 고친 공고면 고친 명세, 아니면 None"""
    fix = fixes.get(str(pid))
    if not fix:
        return None
    base = next(iter(entry["models"].values()))
    return {**base, **{k: v for k, v in fix.items() if k in RUBRIC_KEYS}}
