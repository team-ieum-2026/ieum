# 주인: AI-B
"""공고 조건 읽기 (추천 정렬 · 필터용): 필수 기술과 직무군을 공고 본문(색인의 matchText)에서 규칙으로 찾는다.

- 필수 기술: 자격요건 · 지원 자격 구간과 [필요 역량] 의 기타(...) 안에서만 찾는다. 우대 조건은 뺀다.
  기술이 하나도 안 적힌 공고는 판단하지 않는다 (None, 중립).
- 보유 판정은 한 방향만 (ai/eval/LABELING.md 동의어 표와 같게): 공고가 넓은 표기("Spring")면 수료생의 구체 기술("Spring Boot")도 인정.
- 직무군: 공고 제목 → [직무] 칸 순으로 키워드 규칙. 직함(프론트엔드 · 백엔드 · 풀스택)을 먼저 보고, 괄호 안 "(… 우대)"는 뺀다.
  판단 못 하면 None (필터에서 통과).
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
    "Angular": ["Angular", "AngularJS", "Angular.js", "앵귤러"],
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
    # 11/3 추가: 공고에 흔히 나오는 프레임워크 · 라이브러리 · DB · AI 도구. 같은 기술의 다른 표기만 묶는다
    "Express": ["Express", "Express.js", "ExpressJS"],  # Node.js 백엔드의 대표 프레임워크
    "NestJS": ["NestJS", "Nest.js"],  # TypeScript 백엔드 프레임워크, 국내 스타트업 공고에 자주 나옴
    "Nuxt": ["Nuxt", "Nuxt.js", "NuxtJS"],  # Vue 기반 SSR 프레임워크 (Next.js 의 Vue 판)
    "Svelte": ["Svelte"], "SvelteKit": ["SvelteKit"],  # 프론트엔드 프레임워크와 그 앱 프레임워크 (서로 다른 기술)
    "Remix": ["Remix"], "Gatsby": ["Gatsby", "Gatsby.js"],  # React 기반 웹 프레임워크
    "Laravel": ["Laravel", "라라벨"],  # PHP 대표 웹 프레임워크
    "Ruby on Rails": ["Ruby on Rails", "Rails"],  # Ruby 대표 웹 프레임워크
    "ASP.NET": ["ASP.NET", "ASP.NET Core"],  # C# 웹 프레임워크, SI · 공공 공고에 흔함
    "Gin": ["Gin"], "Ktor": ["Ktor"],  # Go · Kotlin 의 대표 웹 프레임워크
    "React Native": ["React Native", "리액트 네이티브"],  # 모바일 앱 프레임워크. React 와는 다른 기술로 센다
    "MariaDB": ["MariaDB", "마리아DB"], "MS SQL": ["MS SQL", "MSSQL", "SQL Server"],  # 국내 공고에 흔한 관계형 DB
    "SQLite": ["SQLite"],  # 앱 · 임베디드에 흔한 내장 DB
    "Vector DB": ["Vector DB", "VectorDB", "벡터 DB", "벡터DB", "벡터 데이터베이스", "Vector Database"],  # RAG 공고의 일반 용어
    "Pinecone": ["Pinecone"], "FAISS": ["FAISS"], "ChromaDB": ["ChromaDB", "Chroma DB"],  # 흔한 벡터 DB 제품 (각각 다른 기술)
    "Milvus": ["Milvus"], "Qdrant": ["Qdrant"], "pgvector": ["pgvector"],
    "scikit-learn": ["scikit-learn", "sklearn", "사이킷런", "scikit learn"],  # 대표 머신러닝 라이브러리, 한글 표기가 흔함
    "NumPy": ["NumPy", "넘파이"], "Keras": ["Keras", "케라스"],  # 데이터 · 딥러닝 기본 라이브러리
    "Hugging Face": ["Hugging Face", "HuggingFace", "허깅페이스"],  # 모델 허브 · 라이브러리
    "LlamaIndex": ["LlamaIndex"],  # LangChain 과 함께 흔한 RAG 프레임워크
}
# 이미 있던 항목의 한글 · 다른 표기 (공고에 한글로 쓰는 경우가 흔함). OpenAI API 의 GPT 표기는 OpenAI 쪽에만 둔다 (Gemini API 와는 다른 기술)
TECH["LangChain"] += ["랭체인"]
TECH["PyTorch"] += ["파이토치"]
TECH["TensorFlow"] += ["텐서플로", "텐서플로우"]
TECH["Pandas"] += ["판다스"]
TECH["OpenAI API"] += ["GPT API", "ChatGPT API"]
TECH["Go"] += ["Go"]  # 대소문자를 구분하는 전용 패턴(_EXACT_RE)으로만 찾는다
TECH.update({
    # 11/3 빈칸 점검(평가 공고 379건의 자격요건)에서 나온 흔하고 모호하지 않은 기술
    "gRPC": ["gRPC", "grpc"],  # 백엔드 서비스 간 통신의 대표 프레임워크
    "Photoshop": ["Photoshop", "포토샵"], "Illustrator": ["Illustrator", "일러스트레이터"],  # 디자인 공고의 기본 도구
    "Premiere Pro": ["Premiere Pro", "프리미어 프로"],  # 영상 편집 기본 도구
    "Windows": ["Windows"], "Ubuntu": ["Ubuntu", "우분투"], "Unix": ["Unix", "유닉스"],  # 운영체제 (Linux 와 다른 기술)
    ".NET": [".NET", ".NET Framework", ".NET Core"],  # C# 플랫폼. ASP.NET 은 위에 따로 있다
    "WPF": ["WPF"], "WinForms": ["WinForms", "Windows Forms"],  # .NET 데스크톱 UI 프레임워크
    "PHP": ["PHP"], "Scala": ["Scala", "스칼라"],  # 흔한 프로그래밍 언어
    "Azure": ["Azure", "애저"],  # AWS 와 다른 클라우드
    "Tableau": ["Tableau", "태블로"],  # 대표 BI 도구
    "RxJS": ["RxJS"], "OpenCV": ["OpenCV"],  # Angular · 컴퓨터 비전의 기본 라이브러리
})

# 공고의 넓은 표기 → 수료생이 가지고 있으면 인정하는 구체 기술 (ai/eval/LABELING.md 동의어 표 · rubric.SKILL_ALIASES 와 같게)
BROAD: dict[str, set[str]] = {
    "spring": {"spring boot"},
    "llm api": {"gemini api", "openai api"},
    # 관계형 DB 와 SQL 매퍼는 서로 인정한다 (rubric.SKILL_ALIASES 와 같게)
    "sql": {"mysql", "postgresql", "mybatis"},
    "rdbms": {"mysql", "postgresql", "mybatis"},
    "mysql": {"postgresql", "mybatis"},
    "postgresql": {"mysql", "mybatis"},
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
    ("AI", r"\bAI\b|AI\s|(?<![A-Za-z])AI(?=솔루션|서비스|개발|엔지니어|모델|플랫폼|에이전트)|LLM|챗봇|인공지능|머신\s*러닝|딥\s*러닝|생성형|ML\b"),
    # 서버 장비를 다루는 일 ("서버 · 스토리지 유지보수") 은 서버 개발(BE)이 아니라 인프라. "서버 개발 및 유지보수"는 BE 그대로
    ("INFRA", r"^(?!.*개발)(?=.*서버)(?=.*(?:유지\s*보수|스토리지|전산\s*장비))"),
    ("BE", r"서버|API|자바|java|spring|스프링|SI\b"),
]
# React Native 는 FE 의 react 규칙보다 먼저 모바일로 본다
FAMILY_RULES.insert(next(i for i, (f, _) in enumerate(FAMILY_RULES) if f == "FE"),
                    ("MOBILE", r"react\s*-?\s*native|리액트\s*네이티브"))

# [직무] 칸의 워크넷 직무 분류명 → 직무군. 분류명의 뜻만 보고 정했다 (dev 결과에 맞추지 않음).
# 응용 소프트웨어 개발자 · 웹 개발자 · 범용 응용 소프트웨어 프로그래머처럼 직무를 정할 수 없는 넓은 분류는 넣지 않는다 (판단 못 함 → 통과).
# 직무군 이름 중 INDUSTRIAL · SYSTEM_SW · WEB_OPS · QA · PLANNER · CONSULTING 은 수료생 희망 직무에 없어서, 다른 직무군처럼 걸러진다
JOB_FIELD_FAMILY: dict[str, str] = {
    "웹 디자이너": "DESIGN",
    "IT 기술지원 전문가": "INFRA",
    "정보보안 전문가": "INFRA",
    "정보시스템 운영자": "INFRA",
    "데이터베이스 운영·관리자": "INFRA",
    "시스템 소프트웨어 개발자": "SYSTEM_SW",  # 운영체제 · 컴파일러 · 미들웨어 등 시스템 소프트웨어
    "시스템 소프트웨어 개발자(프로그래머)": "SYSTEM_SW",
    "데이터 설계 및 프로그래머": "DATA",
    "웹 운영자(홈페이지 관리자)": "WEB_OPS",  # 홈페이지 내용 관리 (개발 아님)
    "펌웨어 및 임베디드 소프트웨어 프로그래머": "EMBEDDED",
    "통신기술 개발자 및 통신망 운영 기술자": "NETWORK",
    "통신기기·장비 개발자 및 연구원": "NETWORK",
    "네트워크 시스템 개발자": "NETWORK",
    "기타 네트워크 및 정보 보안 전문가(디지털 포렌식 전문가 등)": "NETWORK",
    "기타 컴퓨터 시스템 및 소프트웨어 전문가(IT 테스터 및 IT QA 전문가 등)": "QA",
    "산업특화 응용 소프트웨어 프로그래머(국방,항공,교통,에너지,금융,자동차 등)": "INDUSTRIAL",  # 산업 · 제어 소프트웨어
    "모바일 애플리케이션 프로그래머(앱·어플 개발)": "MOBILE",
    "웹 기획자": "PLANNER",
    "IT 컨설턴트": "CONSULTING",
}
_FAMILY_RES = [(family, re.compile(rx, re.IGNORECASE)) for family, rx in FAMILY_RULES]
# 직함. 제목에 있으면 위 규칙보다 먼저 본다 ("프론트엔드 개발자 (디자인 시스템)" → FE). 프론트와 백엔드가 함께 있으면 풀스택
_TITLE_RES = [(family, re.compile(rx, re.IGNORECASE)) for family, rx in [
    ("FULLSTACK", r"풀스택|full\s*stack"), ("FE", r"프론트|front\s*-?end"), ("BE", r"백엔드|back\s*-?end"),
]]
# 괄호 안 우대 조건 "(Unity 우대)" 는 직무가 아니라서 직무군 판단에서 뺀다
_PREFERRED_PAREN = re.compile(r"[(\[][^)\]]*우대[^)\]]*[)\]]")

_REQ_HEAD = re.compile(r"자격\s*요건|지원\s*자격|자격\s*사항|필수\s*(요건|사항|역량)?|요구\s*사항")
_PREF_HEAD = re.compile(r"우대")
_OTHER_HEAD = re.compile(r"담당\s*업무|주요\s*업무|업무\s*내용|근무\s*조건|복리\s*후생|제출\s*서류|^\s*\[(?!필요 역량)|문의|회사\s*소개|전형")
_SECTION_LINE = re.compile(r"^\[(공고|직무)\]\s*(.*)$", re.M)
# 줄을 나누는 태그는 줄바꿈으로, 글자 꾸밈 태그는 지운다. "<" 바로 뒤에 태그 이름이 와야 태그로 본다
_LINE_TAG = re.compile(r"</?(?:br|p|li|div|ul|ol|tr|td|th|table|h[1-6])\b[^>]*>", re.IGNORECASE)
_INLINE_TAG = re.compile(r"</?(?:b|strong|span|em|i|u|font)\b[^>]*>", re.IGNORECASE)


# 대소문자를 구분해야 하는 표기. "Go" 는 대문자로 시작하는 독립된 단어만 (Google · go · GO · "Go to" · "Go-To" · go.kr 은 아님).
# "Go+MySQL" 처럼 + 로 이어 쓴 것은 인정한다
_EXACT_RE = {"Go": re.compile(r"(?<![0-9A-Za-z.])Go(?![0-9A-Za-z#\-.])(?!\s+to\b)")}


def _spelling_re(spelling: str) -> re.Pattern[str]:
    if spelling in _EXACT_RE:
        return _EXACT_RE[spelling]
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


def _html_to_lines(text: str) -> str:
    """HTML 한 줄 본문("<p>■ 자격요건</p><p>- Java<br>- …")을 줄 단위로 바꾼다. 태그가 없는 본문은 그대로.

    "< 자격요건 >" 처럼 꺾쇠 안에 띄어쓰기가 있는 머리글은 태그가 아니라서 건드리지 않는다.
    """
    return _INLINE_TAG.sub("", _LINE_TAG.sub("\n", text))


def required_text(text: str) -> str:
    """자격요건 · 지원 자격 구간과 [필요 역량] 기타(...) 만 모은다. 우대는 뺀다"""
    req: list[str] = []
    state = None
    for line in _html_to_lines(text).split("\n"):
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


# 수료생 기술 표기 → 사전 대표 이름 (소문자). 수료생이 "사이킷런" · "Vue" 로 적어도 공고의 scikit-learn · Vue.js 와 맞춘다
_SPELLING_TO_NAME = {re.sub(r"\s+", "", s).lower(): name.lower() for name, ss in TECH.items() for s in [name, *ss]}


def _skill_name(skill: str) -> str:
    key = skill.strip().lower()
    return _SPELLING_TO_NAME.get(re.sub(r"\s+", "", key), key)


def owns(required: str, skills: list[str]) -> bool:
    owned = {_skill_name(s) for s in skills}
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
    for field, text in (("공고", title or lines.get("공고") or ""), ("직무", lines.get("직무") or "")):
        if field == "직무" and text.strip() in JOB_FIELD_FAMILY:  # 제목으로 못 정하면 워크넷 분류명 표를 먼저
            return JOB_FIELD_FAMILY[text.strip()]
        text = _PREFERRED_PAREN.sub(" ", text)
        titles = {family for family, rx in _TITLE_RES if rx.search(text)}
        if "FULLSTACK" in titles or {"FE", "BE"} <= titles:
            return "FULLSTACK"
        if titles:
            return titles.pop()
        for family, rx in _FAMILY_RES:
            if rx.search(text):
                return family
    return None


def family_allowed(desired_job: str | None, title: str | None, match_text: str) -> bool:
    """수료생 직무군과 공고 직무군이 같거나, 어느 한쪽을 판단 못 하면 True"""
    student = DESIRED_JOB_FAMILY.get((desired_job or "").strip())
    posting = posting_family(title, match_text)
    return student is None or posting is None or student == posting
