# 주인: AI-A
"""정답 · 함정 공고 명세(spec.json) → 공고(일반 공고와 같은 형식) + 정답 라벨.

실행: python ai/eval/postings/render_postings.py
출력
- out/eval_postings.json: ai/data/jobs_general.json 과 같은 칸 · 형식 ({ _meta, jobs }). 라벨 없음 → BE 적재 · AI 색인용
- ai/eval/gold/gold_matching.csv: 수료생 id · 공고 id · GOLD/TRAP · 함정 유형 · 난이도. 평가 스크립트 전용 (DB · 색인에 넣지 않음)

본문은 명세의 칸(업무 · 필수 기술 · 우대 · 경력 · 학력)만 보고 만든다. 수료생 자료는 읽지 않는다.
문체는 일반 공고에서 본 5가지 중 공고 id로 정해지는 하나를 쓴다 (다시 실행해도 같은 결과).
"""

import csv
import hashlib
import json
import random
from pathlib import Path

HERE = Path(__file__).parent
ROOT = HERE.parents[2]
COMPANIES = ROOT / "ai/data/company/companies.json"
GOLD_CSV = HERE.parent / "gold" / "gold_matching.csv"

PROVINCE = {"서울": "서울특별시", "경기": "경기도", "인천": "인천광역시"}
EDU_TEXT = {
    "고졸": "고등학교 졸업 이상",
    "대졸(2~3년)": "전문대 졸업 이상",
    "대졸(4년)": "4년제 대학 졸업 이상",
    "석사": "석사 학위 이상",
    "박사": "박사 학위 소지자",
}
HOURS = [
    "평일 : (근무시간) (오전) 9시 00분 ~ (오후) 6시 00분, 주 5일 근무, 평균근무시간 : 40",
    "평일 : (근무시간) (오전) 10시 00분 ~ (오후) 7시 00분, 주 5일 근무, 평균근무시간 : 40",
    "평일 : (근무시간) (오전) 8시 30분 ~ (오후) 5시 30분, 주 5일 근무, 평균근무시간 : 40",
]
SELECTION = ["서류,면접", "서류,면접", "서류,면접,기타(과제 전형)", "면접"]
ABILITY = ["문서작성 (워드프로세스 활용)", "프레젠테이션 프로그램 활용", "표계산 (스프레드시트 활용)"]
BENEFITS = ["4대 보험", "퇴직금", "중식 제공", "교육비 지원", "건강검진", "명절 선물", "생일 반차", "야근 식대 지원", "자율 출퇴근"]
DOCUMENTS = ["이력서 및 자기소개서", "포트폴리오(해당자)", "경력기술서(경력자)", "자격증 사본(해당자)", "졸업증명서"]
CONDITIONS_EXTRA = [
    "수습기간 3개월 (급여 100% 지급)",
    "근무지 : 지하철역 도보 5분",
    "재택근무 주 1회 가능",
    "점심시간 12:00~13:00",
]
SALARY = [
    "회사내규에 따름",
    "연봉30,000,000원 이상, 면접 후 결정",
    "연봉32,000,000원 이상 ~ 38,000,000원 이하",
    "월급2,600,000원 이상 ~ 3,000,000원 이하",
    "연봉35,000,000원 이상, 경력에 따라 협의",
]


def career_text(p: dict) -> str:
    if p["career"] == "경력":
        return f"관련 경력 {p['years']}년 이상"
    return "신입 지원 가능" if p["career"] == "신입" else "경력 무관"


def skill_names(p: dict) -> list[str]:
    """본문에 적을 기술 이름. 난이도 C 정답은 다른 표현(display)으로 적는다."""
    display = p.get("display", {})
    return [display.get(skill, skill) for skill in p["required"]]


def qualifications(p: dict) -> list[str]:
    lines = [f"{' · '.join(skill_names(p))} 사용 가능자", career_text(p)]
    if p["edu"] in EDU_TEXT:
        lines.append(EDU_TEXT[p["edu"]])
    return lines


def particle(word: str, with_final: str, without_final: str) -> str:
    last = word[-1]
    has_final = "가" <= last <= "힣" and (ord(last) - 0xAC00) % 28 != 0
    return with_final if has_final else without_final


def short_name(company: str) -> str:
    for token in ("(주)", "(재)", "주식회사", "(주)", " 주식회사"):
        company = company.replace(token, "")
    return company.strip()


def body(p: dict, industry: str, rng: random.Random) -> tuple[str, list[str]]:
    """[내용] 본문과 추가 칸([필요 역량] · [우대 조건] 등)."""
    style = p["id"] % 5
    duties, quals, prefs = p["duties"], qualifications(p), p["pref"]
    extra = []
    if style == 0:  # < 담당업무 > 개조식
        text = "< 담당업무 >\nㆍ " + "\nㆍ ".join(duties)
        text += "\n< 자격요건 >\nㆍ " + "\nㆍ ".join(quals)
        text += "\n< 우대사항 >\nㆍ " + "\nㆍ ".join(prefs)
        extra.append(f"[필요 역량] {rng.choice(ABILITY)}")
    elif style == 1:  # 번호식
        text = "1. 담당업무\n· " + "\n· ".join(duties)
        text += "\n2. 자격요건\n· " + "\n· ".join(quals)
        text += "\n3. 우대사항\n· " + "\n· ".join(prefs)
        text += "\n근무조건\n· 출퇴근 : 주 5일 (월~금)"
    elif style == 2:  # 회사 소개 서술형 + 복리후생
        name = short_name(p["company"])
        text = (
            f"안녕하세요, {name} 채용 담당자입니다.\r\n\r\n"
            f"{name}{particle(name, '은', '는')} {industry} 분야의 회사입니다. "
            f"{duties[0]} 업무를 함께할 분을 찾습니다.\r\n\r\n"
            f"지원 자격 : {', '.join(quals)}\r\n우대 : {', '.join(prefs)}\r\n\r\n"
            "복리후생\r\nㆍ " + "\r\nㆍ ".join(rng.sample(BENEFITS, 4))
        )
    elif style == 3:  # 짧은 공고: 업무 한 줄 + 칸으로 조건
        text = duties[0]
        if p["career"] == "경력":
            text += f" ({career_text(p)})"
        if p["edu"] in EDU_TEXT:
            text += f" - {EDU_TEXT[p['edu']]}"
        extra.append(f"[필요 역량] {rng.choice(ABILITY)},기타({', '.join(skill_names(p))} 사용 경험)")
        extra.append(f"[우대 조건] {', '.join(prefs)}")
    else:  # HTML 형
        text = "<p><b>■ 담당업무</b></p><p>- " + "<br>- ".join(duties) + "</p>"
        text += "<p><b>■ 자격요건</b></p><p>- " + "<br>- ".join(quals) + "</p>"
        text += "<p><b>■ 우대사항</b></p><p>- " + "<br>- ".join(prefs) + "</p>"
    # 실제 공고처럼 매칭과 상관없는 내용을 섞어 길이 · 모양을 일반 공고 분포에 맞춘다
    # HTML 형은 본문 전체가 HTML 이라 덧붙이지 않는다. 번호식은 근무조건 칸이 이미 있다
    if style == 4:
        text += "<p><b>■ 복리후생</b></p><p>- " + "<br>- ".join(rng.sample(BENEFITS, rng.randint(3, 5))) + "</p>"
        return text, extra
    if style != 2 and rng.random() < 0.7:
        text += "\n\n복리후생\nㆍ " + "\nㆍ ".join(rng.sample(BENEFITS, rng.randint(3, 5)))
    if rng.random() < 0.6:
        text += "\n\n제출서류\n· " + "\n· ".join(rng.sample(DOCUMENTS, rng.randint(2, 3)))
    if style != 1 and rng.random() < 0.5:
        text += "\n\n근무조건\nㆍ " + "\nㆍ ".join(rng.sample(CONDITIONS_EXTRA, 2))
    if rng.random() < 0.3:
        text += "\n\n문의 : 채용 담당자 [연락처 생략]"
    return text, extra


def render(p: dict, industry: str) -> dict:
    rng = random.Random(p["id"])
    text, extra = body(p, industry, rng)
    lines = [f"[직무] {p['cat']}", f"[내용] {text}", *extra,
             f"[근무 시간] {rng.choice(HOURS)}", f"[전형] {rng.choice(SELECTION)}"]
    description = "\n".join(lines)
    province, district = p["loc"].split(" ", 1)
    return {
        "title": p["title"],
        "career": p["career"],
        "location": p["loc"],
        "postedOn": f"2026-10-{rng.randint(15, 28)}",
        "education": p["edu"],
        "deadlineOn": f"2026-{rng.choice(['11', '12'])}-{rng.randint(10, 28)}",
        "salaryText": rng.choice(SALARY),
        "companyName": p["company"],
        "contentHash": hashlib.sha256(description.encode("utf-8")).hexdigest(),
        "description": description,
        "jobCategory": p["cat"],
        "untilFilled": rng.random() < 0.6,
        "workAddress": f"{PROVINCE[province]} {district} 예시로 {rng.randint(10, 300)}, {rng.randint(2, 15)}층 (모의동)",
        "jobPostingId": p["id"],
        "employmentType": "정규직" if rng.random() < 0.8 else "계약직",
        "source": "EVAL",
    }


def main() -> None:
    spec = json.loads((HERE / "spec.json").read_text(encoding="utf-8"))["postings"]
    industries = {c["companyName"]: c["industry"]
                  for c in json.loads(COMPANIES.read_text(encoding="utf-8"))["companies"]}
    jobs = [render(p, industries[p["company"]]) for p in spec]

    out = HERE / "out"
    out.mkdir(exist_ok=True)
    meta = {
        "snapshotAt": "2026-10-29",
        "source": "EVAL (평가용 정답 · 함정 공고, 실제 공고 아님)",
        "count": len(jobs),
        "note": "ai/data/jobs_general.json 과 같은 칸. 라벨은 ai/eval/gold/gold_matching.csv 에만 있음. render_postings.py 가 spec.json 으로 생성",
    }
    (out / "eval_postings.json").write_text(
        json.dumps({"_meta": meta, "jobs": jobs}, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )

    GOLD_CSV.parent.mkdir(exist_ok=True)
    with GOLD_CSV.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["student_id", "job_posting_id", "label", "trap_type", "level", "origin", "title"])
        for p in spec:
            w.writerow([p["sid"], p["id"], p["label"], p.get("type", ""), p.get("level", ""), "TARGET", p["title"]])
    print(f"공고 {len(jobs)}건 → {out / 'eval_postings.json'}, 라벨 → {GOLD_CSV}")


if __name__ == "__main__":
    main()
