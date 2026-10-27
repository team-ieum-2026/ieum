# 주인: AI-B
"""공고 색인 전처리: BE 가 보낸 공고 한 건 → 매칭용 텍스트 · 경력 필수 여부 · 지역.

실험(ieum-practice preprocess.py · eda.py, eda_real.md)에서 옮겨 왔다. 규칙을 바꾸면 PREPROCESS_VERSION 을 올린다.
버전이 바뀌면 다음 색인 요청 때 같은 contentHash 공고도 다시 계산된다.
비IT 직종 필터는 BE 가 직종 코드로 하므로 여기서는 하지 않는다.
"""

import html
import re
from dataclasses import dataclass

from matching.models import IndexJob

PREPROCESS_VERSION = "v1"

# 매칭용 텍스트 글자 수 상한. 실험은 리랭커(bge-reranker-v2-m3) 토크나이저로 400토큰에서 잘랐지만 토크나이저가 requirements 에 없어 글자 수로 자른다.
# 근거: 실험 데이터(jobs_clean.json 138건)에서 400토큰에서 잘린 24건의 잘린 위치가 최소 739자 · 중앙값 831자.
# 700자로 자르면 400토큰을 넘는 공고가 없고, 잘리는 공고는 30건(토큰 기준 24건)으로 조금 더 보수적이다. 평균 0.52토큰/글자(eda_real.md 6번).
MAX_MATCH_CHARS = 700

# 매칭용 칸 순서. [근무 시간] · [전형] 은 정형 문구라 뺀다. [전공] 은 실험처럼 [우대 조건] 다음에 둔다
MATCH_ORDER = ["직무", "필요 역량", "자격증", "우대 조건", "전공", "내용"]
SECTION_NAMES = ["직무", "내용", "필요 역량", "자격증", "전공", "우대 조건", "근무 시간", "전형"]
_SECTION_RE = re.compile(r"^\[(" + "|".join(re.escape(s) for s in SECTION_NAMES) + r")\] ?", re.M)

# 사무 능력만 적힌 [필요 역량] (쉼표로 나눈 모든 조각이 이 단어를 포함하면 칸을 뺀다)
_OFFICE = re.compile(r"문서작성|표계산|프레젠테이션|스프레드시트|워드")

_EMAIL = re.compile(r"[\w.+-]+@[\w-]+\.[\w.]+")
_PHONE = re.compile(r"(?<!\d)0\d{1,2}[-) .]\s?\d{3,4}[-. ]\d{4}(?!\d)")

# 기술 표기 통일 (eda_real.md 8번 표, 같은 기술의 다른 표기만)
_A = lambda w: rf"(?<![A-Za-z]){w}(?![A-Za-z])"  # noqa: E731 — 영문자 경계
TECH_UNIFY = [
    (_A("JAVA") + "|" + _A("java(?!script)") + r"|자바(?!스크립트)", "Java"),
    (_A("python") + "|파이썬", "Python"),
    (r"\.net(?![A-Za-z])", ".NET"),
    (_A("mysql"), "MySQL"),
    (_A(r"ms[\s-]?sql"), "MS SQL"),
    (r"리눅스(?!\s*마스터)", "Linux"),
    ("포토샵", "Photoshop"),
    (r"일러스트레이터|일러스트(?!레이터)|" + _A("illustrator"), "Illustrator"),
    ("피그마", "Figma"),
]
REGION_UNIFY = {"경기도": "경기", "서울특별시": "서울", "인천광역시": "인천"}

# 경력 필수: 본문 "경력 N년 이상" · "N년 이상 (실무) 경력". 바로 뒤에 '우대' 가 오면 필수가 아니다
_CAREER_RE = re.compile(r"경력\s*\d+\s*년\s*이상|\d+\s*년\s*이상\s*(의\s*)?(실무\s*)?경력")
_PREFER_AFTER = re.compile(r"^.{0,12}우대")
# 본문에 신입 지원 문구가 있으면 본문의 "N년 이상" 만으로는 경력 필수로 보지 않는다
_NEWCOMER = re.compile(r"신입\s*(지원|가능|및\s*경력|/\s*경력|·\s*경력|,\s*경력)|신입\s*포함")


@dataclass(frozen=True)
class Preprocessed:
    match_text: str
    career_required: bool
    region: str | None


def mask_contacts(text: str) -> str:
    return _PHONE.sub("[연락처]", _EMAIL.sub("[연락처]", text))


def unify_tech(text: str) -> str:
    for rx, to in TECH_UNIFY:
        text = re.sub(rx, to, text, flags=re.I)
    return text


def clean(text: str | None) -> str:
    """HTML 문자 되돌리기 → 연락처 가리기 → 기술 표기 통일"""
    return unify_tech(mask_contacts(html.unescape(text or ""))).strip()


def unify_region(location: str | None) -> str | None:
    parts = html.unescape(location or "").split()
    if not parts:
        return None
    parts[0] = REGION_UNIFY.get(parts[0], parts[0])
    return " ".join(parts)


def sections(description: str) -> dict[str, str]:
    """[칸] 형식 본문 → {칸 이름: 내용}. 같은 칸이 두 번 오면 뒤의 것을 쓴다."""
    parts = _SECTION_RE.split(description)
    return {parts[i]: parts[i + 1].strip() for i in range(1, len(parts) - 1, 2)}


def is_office_only(competency: str) -> bool:
    pieces = [p for p in competency.split(",") if p.strip()]
    return bool(pieces) and all(_OFFICE.search(p) for p in pieces)


def career_required(career: str | None, description: str) -> bool:
    if (career or "").strip() == "경력":
        return True
    hits = [m for m in _CAREER_RE.finditer(description) if not _PREFER_AFTER.match(description[m.end():])]
    return bool(hits) and not _NEWCOMER.search(description)


def match_text(title: str, region: str | None, description: str) -> str:
    secs = sections(description)
    if secs.get("필요 역량") and is_office_only(secs["필요 역량"]):
        del secs["필요 역량"]
    body = "\n".join(f"[{k}] {secs[k]}" for k in MATCH_ORDER if secs.get(k))
    head = [f"[공고] {title}" if title else "", f"[지역] {region}" if region else ""]
    text = "\n".join(line for line in [*head, body] if line)
    return text[:MAX_MATCH_CHARS]


def preprocess(job: IndexJob) -> Preprocessed:
    description = clean(job.description)
    region = unify_region(job.location)
    return Preprocessed(
        match_text=match_text(clean(job.title), region, description),
        career_required=career_required(job.career, description),
        region=region,
    )
