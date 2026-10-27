# 주인: AI-B
"""기술 이름 별칭 사전. 프로젝트 techStack 을 합칠 때 같은 기술의 다른 표기를 대표 이름 하나로 모은다.

비교는 대소문자와 띄어쓰기를 무시한다 ("spring boot" == "SpringBoot"). 사전에 없는 이름은 처음 나온 표기를 그대로 쓴다.
공고 쪽 표기 통일(matching/preprocess.py TECH_UNIFY)과 대표 이름을 맞춘다.
"""

import re

# 대표 이름 → 별칭 (대표 이름 자신은 따로 적지 않아도 된다)
SKILL_ALIASES: dict[str, list[str]] = {
    "Java": ["JAVA", "자바"],
    "Python": ["파이썬"],
    "MySQL": [],
    "MS SQL": ["MSSQL"],
    ".NET": [],
    "Linux": ["리눅스"],
    "Photoshop": ["포토샵"],
    "Illustrator": ["일러스트", "일러스트레이터"],
    "Figma": ["피그마"],
    "JavaScript": ["JS", "자바스크립트"],
    "TypeScript": ["TS"],
    "React": ["리액트"],
    "Spring Boot": ["스프링부트", "springboot"],
}

_SPACES = re.compile(r"\s+")


def skill_key(name: str) -> str:
    """비교용 키: 대소문자 · 띄어쓰기 무시"""
    return _SPACES.sub("", name).casefold()


_CANONICAL: dict[str, str] = {
    skill_key(alias): canonical
    for canonical, aliases in SKILL_ALIASES.items()
    for alias in [canonical, *aliases]
}


def canonical_skill(name: str) -> str:
    """사전에 있으면 대표 이름, 없으면 앞뒤 공백만 뗀 원래 표기"""
    name = name.strip()
    return _CANONICAL.get(skill_key(name), name)
