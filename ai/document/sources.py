# 주인: AI-A
"""서류 생성 근거 후보 만들기 (AI-A-04). 원문을 문장 단위로 잘라 번호를 붙인다.

LLM 은 번호만 고르고, 인용(quote)은 코드가 이 후보에서 가져온다. 그래서 가짜 인용이 생길 수 없다.
번호 접두어로 출처를 나눈다 → 검증에서 "NOT_FOUND 인데 C 를 쓴 문장 제외" 같은 규칙을 쓰기 쉽다.
- E: 수료생 자료. AI-B 프로필 추출의 build_candidates 를 그대로 쓴다 (연락처 가리기 · 팀 인원 표시 포함)
- J: 지원한 공고 (제목 · 본문)
- C: 기업 정보 (인재상 · 핵심 가치 · 최근 이슈 항목마다 하나). 출처 URL 이 있는 항목만 넣는다
"""

import logging
from dataclasses import dataclass

from profile.models import ExtractRequest
from profile.sources import build_candidates, mask_contacts, split_chunks
from schemas.company import CompanyInfo
from schemas.draft import DraftRequest, JobPostingInput

logger = logging.getLogger("ieum.document")


@dataclass(frozen=True)
class Source:
    """근거 후보 한 조각. 필드가 서류 Evidence 와 1:1 이라 조립 때 그대로 옮긴다."""
    eid: str
    ref_type: str
    ref_id: int
    label: str
    quote: str
    url: str | None = None
    collected_at: str | None = None


def _student_sources(req: DraftRequest) -> list[Source]:
    # StudentMaterial 과 ExtractRequest 는 같은 모양이라 그대로 넘긴다. 번호 E1, E2 … 도 build_candidates 가 붙인다
    extract_req = ExtractRequest.model_validate(req.student.model_dump(by_alias=True))
    return [Source(c.eid, c.ref_type, c.ref_id, c.label, c.quote) for c in build_candidates(extract_req)]


def _job_sources(job: JobPostingInput) -> list[Source]:
    """공고 제목 1개 + 본문을 문장 단위로 잘라 J1... 번호를 붙인다"""
    quotes = [mask_contacts(job.title.strip())] + split_chunks(mask_contacts(job.description or ""))
    labels = ["공고 제목"] + ["공고 본문"] * (len(quotes) - 1)
    return [
        Source(f"J{i}", "JOB_POSTING", job.job_posting_id, label, quote)
        for i, (label, quote) in enumerate(zip(labels, quotes), start=1)
        if quote
    ]


def _company_sources(info: CompanyInfo | None) -> list[Source]:
    """
    인재상·핵심 가치·최근 이슈를 항목마다 하나씩 C1…로 만든다. 아래 세 경우에는 만들지 않는다.
    1. NOT_FOUND이거나 기업 정보가 null인 경우
    2. companyInfoId가 null인 경우 (경고 로그만 남김)
    3. 출처 URL이 없는 항목
    """
    if info is None or info.status == "NOT_FOUND":
        return []
    if info.company_info_id is None:
        # BE 가 DB id 를 채워 보내기로 되어 있어 정상이면 생기지 않는다. refId 를 못 채우니 기업 정보 근거를 만들지 않는다
        logger.warning("company info without id company=%s → no company sources", info.company_name)
        return []
    source_of = {s.item: s for s in info.sources}
    items = [("talentKeywords", "인재상", k) for k in info.talent_keywords]
    items += [("coreValues", "핵심 가치", v) for v in info.core_values]
    if info.recent_issue:
        items.append(("recentIssue", "최근 이슈", info.recent_issue))

    sources: list[Source] = []
    for item, name, quote in items:
        origin = source_of.get(item)
        if origin is None:
            # 출처가 없는 항목은 근거로 쓰지 않는다 (지어낸 기업 정보와 구분할 수 없음)
            logger.info("company item without source company=%s item=%s", info.company_name, item)
            continue
        sources.append(Source(
            f"C{len(sources) + 1}", "COMPANY_INFO", info.company_info_id,
            f"{info.company_name} {name}", quote.strip(), origin.url, origin.collected_at,
        ))
    return sources


def build_sources(req: DraftRequest) -> list[Source]:
    return _student_sources(req) + _job_sources(req.job) + _company_sources(req.company_info)


def render_sources(sources: list[Source]) -> str:
    """프롬프트에 넣을 "번호 | 이름 | 원문 텍스트" 를 만든다 """
    return "\n".join(f"{s.eid} | {s.label} | {s.quote}" for s in sources)
