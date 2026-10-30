# 주인: AI-B
"""LLM 이 돌려주는 JSON 형식. 이 폴더 안에서만 쓴다.

LLM 은 근거 후보 번호(E1, E2 …)만 고른다. 인용문은 쓰지 않고, 코드가 후보에서 가져온다.
"""

from schemas.base import CamelModel


class LlmItem(CamelModel):
    text: str
    evidence_ids: list[str] = []  # JSON 키: evidenceIds


class LlmProfile(CamelModel):
    roles: list[LlmItem] = []
    achievements: list[LlmItem] = []
    skills: list[LlmItem] = []  # text = 기술 이름 하나
