# 주인: AI-B
"""LLM에 보낼 원문 번호표. 근거 검증(evidence_check)도 같은 텍스트로 quote 를 확인한다.

번호는 [refType#refId]. 기본 정보의 이름 · 이메일 · githubUrl 은 넣지 않고, 본문 속 이메일 · 전화번호는 [연락처]로 가린다.
careers 는 RefType 에 CAREER 가 없어(AI-A 확인 필요) 아직 넣지 않는다.
"""

import logging
import re
from dataclasses import dataclass

from profile.models import ExtractRequest

logger = logging.getLogger("ieum.profile")

# readme · 자기소개서 한 개당 LLM 에 보내는 최대 글자 수
MAX_CHARS = 2000

_EMAIL = re.compile(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+")
_PHONE = re.compile(r"(?<!\d)(?:\+82[-.\s]?)?0?\d{1,2}[-.\s]?\d{3,4}[-.\s]?\d{4}(?!\d)")


@dataclass(frozen=True)
class Source:
    ref_type: str
    ref_id: int
    text: str

    @property
    def tag(self) -> str:
        return f"[{self.ref_type}#{self.ref_id}]"


def mask_contacts(text: str) -> str:
    return _PHONE.sub("[연락처]", _EMAIL.sub("[연락처]", text))


def _clean(text: str | None, ref_type: str, ref_id: int, field: str, limit: int | None = None) -> str:
    text = mask_contacts((text or "").strip())
    if limit is not None and len(text) > limit:
        logger.info("profile source truncated ref=%s#%d field=%s chars=%d limit=%d", ref_type, ref_id, field, len(text), limit)
        text = text[:limit]
    return text


def _join(*lines: tuple[str, str]) -> str:
    return "\n".join(f"{label}: {value}" for label, value in lines if value)


def build_sources(req: ExtractRequest) -> list[Source]:
    sources: list[Source] = []
    for p in req.projects:
        text = _join(
            ("제목", _clean(p.title, "PROJECT", p.item_id, "title")),
            ("역할", _clean(p.role_note, "PROJECT", p.item_id, "roleNote")),
            ("README", _clean(p.readme, "PROJECT", p.item_id, "readme", MAX_CHARS)),
        )
        sources.append(Source("PROJECT", p.item_id, text))
    if req.cover_letter is not None:
        text = _clean(req.cover_letter.content, "COVER_LETTER", req.student_id, "content", MAX_CHARS)
        sources.append(Source("COVER_LETTER", req.student_id, text))
    for c in req.certificates:
        text = _join(("자격증", _clean(c.name, "CERTIFICATE", c.item_id, "name")),
                     ("발급 기관", _clean(c.issuer, "CERTIFICATE", c.item_id, "issuer")))
        sources.append(Source("CERTIFICATE", c.item_id, text))
    for e in req.education:
        text = _join(("학교", _clean(e.school_name, "EDUCATION", e.item_id, "schoolName")),
                     ("전공", _clean(e.major, "EDUCATION", e.item_id, "major")))
        sources.append(Source("EDUCATION", e.item_id, text))
    course = req.course
    sources.append(Source("COURSE", course.course_id, _join(("과정", _clean(course.name, "COURSE", course.course_id, "name")))))
    return sources


def render_sources(sources: list[Source]) -> str:
    return "\n\n".join(f"{s.tag}\n{s.text}" for s in sources)
