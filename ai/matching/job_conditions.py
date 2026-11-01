# 주인: AI-B
"""공고 조건 읽기 (추천 정렬 · 필터용): 필수 기술과 직무군을 공고 본문(색인의 matchText)에서 규칙으로 찾는다.

- 필수 기술: 자격요건 · 지원 자격 구간과 [필요 역량] 의 기타(...) 안에서만 찾는다. 우대 조건은 뺀다.
  기술이 하나도 안 적힌 공고는 판단하지 않는다 (None, 중립).
- 보유 판정은 한 방향만 (ai/eval/LABELING.md 동의어 표와 같게): 공고가 넓은 표기("Spring")면 수료생의 구체 기술("Spring Boot")도 인정.
- 직무군: 공고 제목 → [직무] 칸 순으로 키워드 규칙. 판단 못 하면 None (필터에서 통과).
LLM · 임베딩을 쓰지 않는다. 표나 규칙을 바꾸면 평가(eval_run · score_matching)로 다시 확인한다.
"""

import re

# 대표 이름 → 공고에 나오는 표기들
TECH: dict[str, list[str]] = {
    "Java": ["Java", "자바"], "Spring Boot": ["Spring Boot", "SpringBoot", "스프링 부트", "스프링부트"],
    "Spring": ["Spring", "스프링", "스프링 프레임워크", "Spring Framework"], "JPA": ["JPA"], "MyBatis": ["MyBatis"],
    "MySQL": ["MySQL"], "PostgreSQL": ["PostgreSQL"], "Oracle": ["Oracle"], "SQL": ["SQL"],
    "RDBMS": ["RDBMS", "관계형 데이터베이스", "관계형 DB"], "Redis": ["Redis"], "Kafka": ["Kafka"],
    "Docker": ["Docker", "도커"], "Kubernetes": ["Kubernetes", "쿠버네티스", "k8s"], "AWS": ["AWS"],
    "Linux": ["Linux", "리눅스"], "Python": ["Python", "파이썬"], "JavaScript": ["JavaScript", "자바스크립트", "JS"],
    "TypeScript": ["TypeScript", "타입스크립트"], "React": ["React", "리액트"], "Vue.js": ["Vue.js", "Vue", "뷰"],
    "Next.js": ["Next.js", "NextJS"], "Node.js": ["Node.js", "NodeJS", "노드"], "FastAPI": ["FastAPI"],
    "Django": ["Django"], "Flask": ["Flask"], "Gemini API": ["Gemini API", "Gemini"], "OpenAI API": ["OpenAI API", "OpenAI"],
    "LLM API": ["LLM API"], "LangChain": ["LangChain"], "LangGraph": ["LangGraph"], "Whisper": ["Whisper"],
    "PyTorch": ["PyTorch"], "TensorFlow": ["TensorFlow"], "Pandas": ["Pandas"], "Airflow": ["Airflow"], "Spark": ["Spark"],
    "HTML/CSS": ["HTML/CSS", "HTML", "CSS"], "jQuery": ["jQuery"], "Figma": ["Figma", "피그마"],
    "Kotlin": ["Kotlin"], "Swift": ["Swift"], "Flutter": ["Flutter"], "C++": ["C++"], "C#": ["C#"], "C/C++": ["C/C++"],
    "Unity": ["Unity", "유니티"], "Go": ["Golang"], "Jenkins": ["Jenkins"], "GitHub Actions": ["GitHub Actions"],
    "Terraform": ["Terraform"], "Elasticsearch": ["Elasticsearch"], "MongoDB": ["MongoDB"], "Servlet": ["Servlet"],
    "JSP": ["JSP"], "Storybook": ["Storybook"], "React Query": ["React Query"], "Redux": ["Redux"],
    "Tailwind CSS": ["Tailwind"], "Git": ["Git"], "REST API": ["REST API"],
}

# 공고의 넓은 표기 → 수료생이 가지고 있으면 인정하는 구체 기술 (ai/eval/LABELING.md 동의어 표 · rubric.SKILL_ALIASES 와 같게)
BROAD: dict[str, set[str]] = {
    "spring": {"spring boot"},
    "llm api": {"gemini api", "openai api"},
    "sql": {"mysql", "postgresql"},
    "rdbms": {"mysql", "postgresql"},
    "javascript": {"typescript", "react", "vue.js", "next.js", "node.js"},
    "html/css": {"react", "vue.js", "next.js"},
    "c/c++": {"c"},
}

# 수료생 희망 직무 → 직무군 (ai/eval/rubric.py DESIRED_JOB_FAMILY 와 같게)
DESIRED_JOB_FAMILY: dict[str, str] = {
    "백엔드 개발": "BE",
    "프론트엔드 개발": "FE",
    "풀스택 개발": "FULLSTACK",
    "AI 서비스 개발": "AI",
    "데이터 엔지니어": "DATA",
    "클라우드 · 인프라 엔지니어": "INFRA",
    "게임 클라이언트 개발": "GAME",
    "임베디드 개발": "EMBEDDED",
}

# 위에서부터 처음 맞는 규칙. 직함(풀스택 · 프론트 · 백엔드)을 AI 같은 도메인 단어보다 먼저 본다 ("백엔드 개발자 (AI 상담 서비스팀)" → BE)
FAMILY_RULES: list[tuple[str, str]] = [
    ("FULLSTACK", r"풀스택|full\s*stack"), ("PUBLISHER", r"퍼블리셔|퍼블리싱"), ("DESIGN", r"디자이너|디자인"),
    ("FE", r"프론트|front|react|vue|next\.js"), ("BE", r"백엔드|back\s*-?end"),
    ("DATA", r"데이터\s*엔지니어|데이터\s*파이프라인|ETL"), ("ANALYST", r"데이터\s*분석|분석가"),
    ("INFRA", r"클라우드|인프라|devops|SRE|운영\s*엔지니어|시스템\s*엔지니어"), ("NETWORK", r"네트워크"),
    ("EMBEDDED", r"임베디드|펌웨어"), ("GAME", r"게임|unity|언리얼"),
    ("MOBILE", r"앱\s*개발|iOS|android|안드로이드|모바일|flutter"),
    ("AI", r"\bAI\b|AI\s|LLM|챗봇|인공지능|머신\s*러닝|딥\s*러닝|생성형|ML\b"),
    ("BE", r"서버|API|자바|java|spring|스프링|SI\b"),
]
_FAMILY_RES = [(family, re.compile(rx, re.IGNORECASE)) for family, rx in FAMILY_RULES]

_REQ_HEAD = re.compile(r"자격\s*요건|지원\s*자격|자격\s*사항|필수\s*(요건|사항|역량)?|요구\s*사항")
_PREF_HEAD = re.compile(r"우대")
_OTHER_HEAD = re.compile(r"담당\s*업무|주요\s*업무|업무\s*내용|근무\s*조건|복리\s*후생|제출\s*서류|^\s*\[(?!필요 역량)|문의|회사\s*소개|전형")
_SECTION_LINE = re.compile(r"^\[(공고|직무)\]\s*(.*)$", re.M)


def _spelling_re(spelling: str) -> re.Pattern[str]:
    # 영문은 앞뒤가 영문 · 숫자면 다른 단어 ("Java" ≠ "JavaScript"), 한글은 앞이 한글이면 다른 단어
    body = r"\s*".join(re.escape(part) for part in spelling.split())
    body = rf"(?<![0-9A-Za-z]){body}(?![0-9A-Za-z#+])" if spelling.isascii() else rf"(?<![가-힣]){body}"
    return re.compile(body, re.IGNORECASE)


# 긴 표기부터 찾고 찾은 자리는 지운다 ("Spring Boot" 안의 "Spring" 을 또 세지 않게)
_PATTERNS = [(_spelling_re(s), name) for s, name in sorted(((s, n) for n, ss in TECH.items() for s in ss), key=lambda x: -len(x[0]))]


def techs_in(text: str) -> list[str]:
    found, rest = [], text
    for rx, name in _PATTERNS:
        if rx.search(rest):
            found.append(name)
            rest = rx.sub(" ", rest)
    return list(dict.fromkeys(found))


def required_text(text: str) -> str:
    """자격요건 · 지원 자격 구간과 [필요 역량] 기타(...) 만 모은다. 우대는 뺀다"""
    req: list[str] = []
    state = None
    for line in text.split("\n"):
        s = line.strip()
        if s.startswith("[필요 역량]"):
            req += re.findall(r"기타\(([^)]*)\)", s)
            state = None
            continue
        if s.startswith("[우대"):
            state = "pref"
            continue
        head = s[:20]
        if _PREF_HEAD.search(head):
            state = "pref"
            continue
        if _REQ_HEAD.search(head):
            state = "req"
            after = re.split(r"[:：]", s, maxsplit=1)
            if len(after) == 2:  # 한 줄 형식 "지원 자격 : A, B 우대 : C" 는 우대 앞까지
                req.append(re.split(r"우대", after[1])[0])
            continue
        if _OTHER_HEAD.search(head):
            state = None
            continue
        if state == "req":
            req.append(s)
    return "\n".join(req)


def required_skills(text: str) -> list[str]:
    return techs_in(required_text(text))


def owns(required: str, skills: list[str]) -> bool:
    owned = {s.strip().lower() for s in skills}
    key = required.lower()
    return key in owned or bool(BROAD.get(key, set()) & owned)


def missing_required(text: str, skills: list[str]) -> bool | None:
    """필수 기술이 적혔는데 하나라도 없으면 True, 전부 있으면 False, 적힌 기술이 없으면 None (중립)"""
    required = required_skills(text)
    if not required:
        return None
    return not all(owns(r, skills) for r in required)


def posting_family(title: str | None, match_text: str) -> str | None:
    """공고 직무군. 제목 → [직무] 칸 순. 판단 못 하면 None"""
    lines = dict((m.group(1), m.group(2)) for m in _SECTION_LINE.finditer(match_text or ""))
    for text in (title or lines.get("공고") or "", lines.get("직무") or ""):
        for family, rx in _FAMILY_RES:
            if rx.search(text):
                return family
    return None


def family_allowed(desired_job: str | None, title: str | None, match_text: str) -> bool:
    """수료생 직무군과 공고 직무군이 같거나, 어느 한쪽을 판단 못 하면 True"""
    student = DESIRED_JOB_FAMILY.get((desired_job or "").strip())
    posting = posting_family(title, match_text)
    return student is None or posting is None or student == posting
