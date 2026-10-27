# 주인: AI-B
"""LLM 이 돌려주는 JSON 형식. 이 폴더 안에서만 쓴다 (응답에는 quote 를 넣지 않는다)."""

from schemas import EvidenceRef
from schemas.base import CamelModel


class LlmItem(CamelModel):
    text: str
    evidence: list[EvidenceRef] = []
    # 근거 원문에서 그대로 복사한 짧은 문장. 코드가 원문에 실제로 있는지 확인한다.
    quote: str = ""


class LlmProfile(CamelModel):
    roles: list[LlmItem] = []
    achievements: list[LlmItem] = []
