# 주인: AI-B
"""LLM 근거 후보 만들기. 원문을 문장 · 줄 단위로 잘게 나눠 번호(E1, E2 …)를 붙인다.

LLM 은 번호만 고르고, 인용(quote)은 코드가 이 후보에서 가져온다. 그래서 가짜 인용이 생길 수 없다.
기본 정보의 이름 · 이메일 · githubUrl 은 넣지 않고, 본문 속 이메일 · 전화번호는 [연락처]로 가린다.
경력은 refType CAREER, refId 는 경력 itemId(student_career.id)로 넣는다.
"""

import logging
import re
from dataclasses import dataclass

from profile.models import ExtractRequest

logger = logging.getLogger("ieum.profile")

# readme · 자기소개서 · roleNote · 경력 설명 한 개당 나누기 전에 쓰는 최대 글자 수
MAX_CHARS = 2000
# 근거 후보 한 조각 최대 글자 수. 더 길면 이 길이로 끊는다
MAX_CHUNK_CHARS = 300
# 글자 · 숫자가 이보다 적은 조각은 버린다 (제목 기호만 있는 줄, "-", "1." 등)
MIN_CHUNK_WORD_CHARS = 4
# 후보가 이보다 많으면 뒤를 자른다 (프롬프트가 너무 길어지지 않게)
MAX_CANDIDATES = 80

_EMAIL = re.compile(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+")
_PHONE = re.compile(r"(?<!\d)(?:\+82[-.\s]?)?0?\d{1,2}[-.\s]?\d{3,4}[-.\s]?\d{4}(?!\d)")
# 줄 앞의 마크다운 제목 · 불릿 · 번호 기호
_LINE_MARK = re.compile(r"^\s*(?:#{1,6}\s*|[-*•·▪>]+\s*|\d{1,2}[.)]\s+)+")
# 문장 끝(. ! ? 。) 뒤 공백에서 나눈다. "1.8s" 처럼 뒤에 공백이 없는 점은 나누지 않는다
_SENTENCE_END = re.compile(r"(?<=[.!?。])\s+")
_WORD_CHAR = re.compile(r"\w")


@dataclass(frozen=True)
class Candidate:
    eid: str
    ref_type: str
    ref_id: int
    label: str
    quote: str


def mask_contacts(text: str) -> str:
    return _PHONE.sub("[연락처]", _EMAIL.sub("[연락처]", text))


def _clean(text: str | None, ref_type: str, ref_id: int, field: str) -> str:
    text = mask_contacts((text or "").strip())
    if len(text) > MAX_CHARS:
        logger.info("profile source truncated ref=%s#%d field=%s chars=%d limit=%d", ref_type, ref_id, field, len(text), MAX_CHARS)
        text = text[:MAX_CHARS]
    return text


def split_chunks(text: str) -> list[str]:
    """줄 → 줄 앞 기호 제거 → 문장 단위 → 300자 상한. 짧은 조각과 같은 조각은 버린다."""
    chunks: list[str] = []
    for line in text.splitlines():
        line = _LINE_MARK.sub("", line).strip()
        for sentence in _SENTENCE_END.split(line):
            sentence = sentence.strip()
            for start in range(0, len(sentence), MAX_CHUNK_CHARS):
                piece = sentence[start:start + MAX_CHUNK_CHARS].strip()
                if len(_WORD_CHAR.findall(piece)) >= MIN_CHUNK_WORD_CHARS and piece not in chunks:
                    chunks.append(piece)
    return chunks


def build_candidates(req: ExtractRequest) -> list[Candidate]:
    raw: list[tuple[str, int, str, str]] = []  # (refType, refId, label, quote)

    def add_text(ref_type: str, ref_id: int, label: str, text: str | None, field: str) -> None:
        seen = {(t, rid, q) for t, rid, _, q in raw}
        for chunk in split_chunks(_clean(text, ref_type, ref_id, field)):
            # 같은 출처의 같은 문장은 한 번만 (예: README 첫 줄 제목이 프로젝트 제목과 같을 때)
            if (ref_type, ref_id, chunk) not in seen:
                raw.append((ref_type, ref_id, label, chunk))

    for p in req.projects:
        title = mask_contacts(p.title.strip())
        name = f"프로젝트 '{title}'"
        if title:
            # 팀 · 개인 프로젝트 표시: 팀으로 받은 상을 혼자 받은 것처럼 쓰지 않게
            size = f" · 팀 {p.team_size}명" if (p.team_size or 0) > 1 else " · 개인" if p.team_size == 1 else ""
            raw.append(("PROJECT", p.item_id, f"{name} 제목{size}", title))
        add_text("PROJECT", p.item_id, f"{name} 역할", p.role_note, "roleNote")
        add_text("PROJECT", p.item_id, f"{name} README", p.readme, "readme")
    for c in req.careers:
        company = mask_contacts(c.company_name.strip())
        name = f"경력 '{company}'"
        header = " · ".join(x for x in [company, mask_contacts((c.position or "").strip())] if x)
        if header:
            raw.append(("CAREER", c.item_id, f"{name} 회사 · 직무", header))
        add_text("CAREER", c.item_id, f"{name} 설명", c.description, "description")
    if req.cover_letter is not None:
        # 임시 규칙(AI-A 확인 전): COVER_LETTER 의 refId 는 studentId
        add_text("COVER_LETTER", req.student_id, "자기소개서", req.cover_letter.content, "content")
    for c in req.certificates:
        quote = " / ".join(x for x in [c.name.strip(), (c.issuer or "").strip()] if x)
        raw.append(("CERTIFICATE", c.item_id, "자격증", mask_contacts(quote)))
    for e in req.education:
        quote = " ".join(x for x in [e.school_name.strip(), (e.major or "").strip()] if x)
        raw.append(("EDUCATION", e.item_id, "학력", mask_contacts(quote)))
    raw.append(("COURSE", req.course.course_id, "교육 과정", mask_contacts(req.course.name.strip())))

    if len(raw) > MAX_CANDIDATES:
        logger.info("profile candidates truncated count=%d limit=%d", len(raw), MAX_CANDIDATES)
        raw = raw[:MAX_CANDIDATES]
    return [Candidate(f"E{i}", t, rid, label, quote) for i, (t, rid, label, quote) in enumerate(raw, start=1)]


def render_candidates(candidates: list[Candidate]) -> str:
    return "\n".join(f"{c.eid} | {c.label} | {c.quote}" for c in candidates)
