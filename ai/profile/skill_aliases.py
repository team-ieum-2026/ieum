# 주인: AI-B
"""기술 이름 별칭 사전. 기술을 합칠 때 같은 기술의 다른 표기를 대표 이름 하나로 모으고, 글 속에 기술 이름이 나오는지 찾는다.

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


def _spellings(name: str) -> list[str]:
    """대표 이름과 별칭 전부. 사전에 없으면 이름 하나"""
    canonical = canonical_skill(name)
    return [canonical, *SKILL_ALIASES.get(canonical, [])] if canonical in SKILL_ALIASES else [canonical]


def _pattern(spelling: str) -> re.Pattern[str]:
    # 띄어쓰기는 있어도 없어도 된다 ("Spring Boot" == "SpringBoot").
    # 영문 이름은 앞뒤가 영문 · 숫자면 다른 단어로 본다 ("Java" ≠ "JavaScript", "C" ≠ "C#"). 뒤에 붙는 한글 조사는 괜찮다.
    body = r"\s*".join(re.escape(part) for part in spelling.split())
    if spelling.isascii():
        body = rf"(?<![0-9a-z]){body}(?![0-9a-z#+])"
    return re.compile(body, re.IGNORECASE)


_ALL_SPELLINGS = [s for canonical, aliases in SKILL_ALIASES.items() for s in [canonical, *aliases]]


def mentions(text: str, name: str) -> bool:
    """글에 기술 이름(또는 별칭)이 나오는지.

    다른 기술의 더 긴 표기 안에 든 경우는 세지 않는다 ("자바스크립트" 안의 "자바" ≠ Java).
    """
    for spelling in _spellings(name):
        if not spelling.strip():
            continue
        rest = text
        for longer in _ALL_SPELLINGS:
            if skill_key(spelling) in skill_key(longer) and skill_key(longer) != skill_key(spelling):
                rest = _pattern(longer).sub(" ", rest)
        if _pattern(spelling).search(rest):
            return True
    return False


def mentioned_skills(text: str | None, names: list[str]) -> list[str]:
    """names 중 글에 나온 기술의 대표 이름. names 순서대로, 같은 기술은 한 번만"""
    found: dict[str, str] = {}
    for name in names:
        canonical = canonical_skill(name)
        if canonical and skill_key(canonical) not in found and mentions(text or "", canonical):
            found[skill_key(canonical)] = canonical
    return list(found.values())
