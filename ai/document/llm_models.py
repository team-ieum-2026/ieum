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


class LlmCompanyLink(CamelModel):
    """회사와의 연결 문장(자기소개서 첫 문장)의 계획"""

    company_evidence_id: str = ""  # 쓸 C 하나
    student_evidence_ids: list[str] = []  # 그와 맞는 E
    fit: str = ""  # 수료생 경험이 그 C 와 어떻게 맞는지 한 줄. 주어는 수료생


class LlmCoverPlan(CamelModel):
    """자기소개서를 쓰기 전에 LLM 이 먼저 채우는 계획. 서류에는 나가지 않는다 (verify · assemble 이 읽지 않음)"""

    company_link: LlmCompanyLink | None = None  # 첫 문장 계획이라 맨 앞. 기업 정보가 없으면 null
    core_experience: str = ""  # 핵심 경험 이름 (프로젝트 제목 또는 경력). 2~3문장으로 쓴다
    core_evidence_ids: list[str] = []  # 핵심 경험의 E 근거
    # 맺음 문장에 이을 공고 업무 J 1개, 많아야 2개. 스키마로 강제하지 않는다 (넘으면 형식 오류 → 재요청으로 시간이 늘어서)
    closing_job_ids: list[str] = []
    brief_experiences: list[str] = []  # 각 1문장으로 줄일 경험
    dropped_experiences: list[str] = []  # 공고와 이어지지 않아 뺄 경험


class LlmDraft(CamelModel):
    skills_summary: list[LlmSentence] = []  # 기술 요약 3~5문장
    tags: list[str] = []  # 기술 태그. 등록 자료에 있는 기술만 남긴다 (verify.py)
    projects: list[LlmProject] = []  # 공고 관련도가 높은 순서. 이 순서가 서류의 프로젝트 순서
    # LLM 은 JSON 을 위에서부터 쓰므로 계획을 자기소개서 바로 앞에 둔다
    cover_plan: LlmCoverPlan | None = None
    cover_letter: list[LlmSentence] = []  # 공고 · 기업에 맞춰 다시 쓴 자기소개서 문장
