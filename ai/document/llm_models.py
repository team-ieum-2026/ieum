# 주인: AI-A
"""서류 생성 LLM 이 돌려주는 JSON 형식. 이 폴더 안에서만 쓴다 (API 응답 형식은 schemas/document.py).

LLM 은 근거 후보 번호(E3, J1, C2 …)만 고른다. 인용문은 쓰지 않고, 코드가 후보에서 가져온다.
"""

from schemas.base import CamelModel


class LlmSentence(CamelModel):
    text: str
    evidence_ids: list[str] = []  # JSON 키: evidenceIds


class LlmProject(CamelModel):
    item_id: int  # 입력 projects[].itemId
    # 짧은 역할 (예: "백엔드"). 그 프로젝트 roleNote 에 글자 그대로 있을 때만 서류에 쓴다 (verify.py)
    role: str | None = None
    # 첫 bullet = 공고에 맞춘 한 줄 요약 (문제 → 내 역할 → 결과). 나머지는 세부 bullet
    bullets: list[LlmSentence] = []


class LlmDraft(CamelModel):
    skills_summary: list[LlmSentence] = []  # 기술 요약 3~5문장
    tags: list[str] = []  # 기술 태그. 등록 자료에 있는 기술만 남긴다 (verify.py)
    projects: list[LlmProject] = []  # 공고 관련도가 높은 순서. 이 순서가 서류의 프로젝트 순서
    cover_letter: list[LlmSentence] = []  # 공고 · 기업에 맞춰 다시 쓴 자기소개서 문장
