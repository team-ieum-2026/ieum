# 주인: AI-A
"""매칭 루브릭 판정 규칙 (평가 계획의 2점 조건을 코드로).

2 적합 = 직무 일치 + 경력 조건 충족(신입 · 관계없음) + 학력 조건 충족 + 희망 지역 안 + 필수 기술 전부 보유(동의어 인정).
1 부분 = 직무는 맞고 한 가지만 부족 (필수 기술이 빠진 경우는 절반 이상은 갖춰야 부분).
0 부적합 = 그 외.
정답 · 함정 공고 검사(check_postings.py)와 교차 라벨에서 같은 규칙을 쓴다.
사람 채점의 기준이기도 하므로, 여기를 바꾸면 채점 안내도 같이 고친다.
"""

# 수료생 희망 직무 → 직무군
DESIRED_JOB_FAMILY = {
    "백엔드 개발": "BE",
    "프론트엔드 개발": "FE",
    "풀스택 개발": "FULLSTACK",
    "AI 서비스 개발": "AI",
    "데이터 엔지니어": "DATA",
    "클라우드 · 인프라 엔지니어": "INFRA",
    "게임 클라이언트 개발": "GAME",
    "임베디드 개발": "EMBEDDED",
}

# 공고 학력 표기(일반 공고 · 실제 공고와 같은 값) → 수준
POSTING_EDUCATION = {"학력무관": 0, "고졸": 1, "대졸(2~3년)": 2, "대졸(4년)": 3, "석사": 4, "박사": 5}
STUDENT_DEGREE = {"HIGH_SCHOOL": 1, "ASSOCIATE": 2, "BACHELOR": 3, "MASTER": 4, "DOCTOR": 5}

# 같은 기술로 보는 표기. 공고 쪽 표기 → 수료생이 가지고 있으면 인정하는 기술들
SKILL_ALIASES = {
    "spring": {"spring boot"},
    "스프링": {"spring boot"},
    "llm api": {"gemini api", "openai api"},
    "sql": {"mysql", "postgresql"},
    "rdbms": {"mysql", "postgresql"},
    "javascript": {"typescript", "react", "vue.js", "next.js", "node.js"},
    "html/css": {"react", "vue.js", "next.js"},
    "c/c++": {"c"},
}

CONDITIONS = ("JOB", "CAREER", "EDU", "REGION", "SKILL")


def norm(skill: str) -> str:
    return skill.strip().lower()


def has_skill(required: str, student_skills: list[str]) -> bool:
    owned = {norm(s) for s in student_skills}
    key = norm(required)
    return key in owned or bool(SKILL_ALIASES.get(key, set()) & owned)


def max_degree(education: list[dict]) -> int:
    return max((STUDENT_DEGREE.get(e["degree"], 0) for e in education), default=0)


def preferred_regions(student: dict) -> set[str]:
    return {r.strip() for r in (student["preferred_region"] or "").split(",") if r.strip()}


def violations(student: dict, posting: dict) -> list[str]:
    """어긋난 조건 목록. 비어 있으면 루브릭 2점(적합).

    posting 은 명세 형식: family · career · education · location · required (필수 기술 목록)
    """
    result = []
    if DESIRED_JOB_FAMILY.get(student["desired_job"]) != posting["family"]:
        result.append("JOB")
    if posting["career"] == "경력":
        result.append("CAREER")
    if POSTING_EDUCATION[posting["education"]] > max_degree(student["education"]):
        result.append("EDU")
    if posting["location"].split()[0] not in preferred_regions(student):
        result.append("REGION")
    if skill_coverage(student, posting) < 1:
        result.append("SKILL")
    return result


def skill_coverage(student: dict, posting: dict) -> float:
    """공고 필수 기술 중 수료생이 가진 비율 (필수 기술이 없으면 1)."""
    required = posting["required"]
    if not required:
        return 1.0
    return sum(has_skill(r, student["skills"]) for r in required) / len(required)


def score(student: dict, posting: dict) -> int:
    """루브릭 점수: 2 적합 · 1 부분(직무는 맞고 1개만 어긋남) · 0 부적합."""
    v = violations(student, posting)
    if not v:
        return 2
    if "JOB" not in v and len(v) == 1 and skill_coverage(student, posting) >= 0.5:
        return 1
    return 0
