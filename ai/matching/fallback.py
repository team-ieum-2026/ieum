# 주인: AI-B
"""임시 추천 (AI-B-03 임시 방식): 색인 저장소의 공고를 규칙 필터 + 단어 겹침 점수로 고른다.

본 추천은 matching/recommend.py(임베딩 후보 → 리랭커)다. 이 파일은 그 대체 경로이고,
단어 매칭 · 근거 문장(build_reasons) · 지역 묶음(region_tier) 함수는 recommend.py 도 같이 쓴다.
(후보: 임베딩 유사도 → 리랭커 Top-3. 규칙 필터 · 중복 합치기 · 응답 형식은 그대로 쓸 수 있다)

1. 후보: jobs_index.json 의 공고 중 excludeJobPostingIds 에 없는 것
2. 규칙 필터: careerRequired 공고 제외. 학력 필터는 아직 없음
3. 점수: 프로필 desiredJob 단어 · skills 이름(별칭 포함)이 공고 matchText 에 나오는 개수(서로 다른 단어 수)
4. 지역: config.REGION_POLICY 로 후보를 거른 뒤(region_allowed), 희망 시 · 도 → 수도권 → 그 밖 순.
   같은 묶음 안에서는 점수 높은 순, 동점이면 jobPostingId 순. 희망 지역은 "서울, 경기"처럼 여러 개일 수 있다
5. 점수 0 인 공고는 추천하지 않는다. 남는 공고가 없으면 items: [] (억지로 채우지 않음)
6. 회사명 · 제목이 같은 공고(근무지만 다름)는 하나만 남긴다
7. reasons: 겹친 단어로 만든 문장 (build_reasons). 기술은 프로필 근거, 희망 직무는 JOB_POSTING 근거. LLM 은 쓰지 않는다

지금은 matching/recommend.py(임베딩 · 리랭커)가 실패하거나 벡터 있는 공고가 없을 때의 대체 경로로 쓴다.
"""

import logging
import re
from dataclasses import dataclass

from matching import config
from matching.models import RecommendData, RecommendItem, RecommendReason, RecommendRequest
from matching.preprocess import unify_region
from matching.store import Entry, IndexStore
from profile.skill_aliases import SKILL_ALIASES, canonical_skill
from schemas import CompetencyProfile, EvidenceRef

logger = logging.getLogger("ieum.matching")

CAPITAL_AREA = ("서울", "경기", "인천")
SIDO = {"서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원",
        "충북", "충남", "전북", "전남", "경북", "경남", "제주"}
# 접미사만 떼서는 짧은 이름이 안 나오는 시 · 도
SIDO_FULL = {"충청북도": "충북", "충청남도": "충남", "전라북도": "전북", "전북특별자치도": "전북",
             "전라남도": "전남", "경상북도": "경북", "경상남도": "경남"}
_REGION_SPLIT = re.compile(r"[\s,/·]+")
# 희망 직무에서 빼는 단어: 거의 모든 IT 공고에 나와 점수를 의미 없게 만든다
JOB_STOPWORDS = {"개발", "개발자", "직무", "업무", "담당", "엔지니어", "신입", "경력", "희망"}
_JOB_SPLIT = re.compile(r"[\s/·,()\[\]]+")
_LATIN = re.compile(r"[A-Za-z]")


@dataclass(frozen=True)
class Scored:
    job_posting_id: int
    entry: Entry
    tier: int
    job_terms: list[str]
    skills: list[str]

    @property
    def score(self) -> int:
        return len(self.job_terms) + len(self.skills)


def term_pattern(term: str) -> re.Pattern:
    """띄어쓰기는 있어도 없어도 맞게. 영문이 들어간 단어는 앞뒤가 영문 · 숫자가 아닐 때만 (Java ≠ JavaScript)"""
    body = r"\s*".join(re.escape(part) for part in term.split())
    if _LATIN.search(term):
        body = rf"(?<![A-Za-z0-9]){body}(?![A-Za-z0-9])"
    return re.compile(body, re.I)


def job_terms(desired_job: str | None) -> list[str]:
    words = [w for w in _JOB_SPLIT.split(desired_job or "") if len(w) >= 2 and w not in JOB_STOPWORDS]
    return list(dict.fromkeys(words))


def skill_patterns(profile: CompetencyProfile) -> dict[str, list[re.Pattern]]:
    """대표 이름 → [대표 이름 · 별칭 패턴]. 같은 대표 이름의 skills 는 하나로"""
    patterns: dict[str, list[re.Pattern]] = {}
    for skill in profile.skills:
        name = canonical_skill(skill.name)
        if name and name not in patterns:
            patterns[name] = [term_pattern(t) for t in [name, *SKILL_ALIASES.get(name, [])]]
    return patterns


def to_sido(word: str | None) -> str | None:
    """"서울특별시" · "서울시" · "경기도" → 짧은 시 · 도 이름. 시 · 도가 아니면 None (예: "강남구")"""
    word = SIDO_FULL.get(word or "", word or "")
    for suffix in ("특별자치시", "특별자치도", "특별시", "광역시", "시", "도"):
        if word not in SIDO and word.endswith(suffix):
            word = word[: -len(suffix)]
    return word if word in SIDO else None


def preferred_sidos(preferred: str | None) -> list[str]:
    """희망 지역 → 시 · 도 목록. "서울, 경기" → ["서울", "경기"]. 시 · 도가 아닌 말(구 이름 등)은 뺀다"""
    found = (to_sido(w) for w in _REGION_SPLIT.split(unify_region(preferred) or ""))
    return list(dict.fromkeys(s for s in found if s))


def region_tier(preferred: str | None, job_region: str | None) -> int:
    """0: 희망 시 · 도 중 하나와 같음 (희망 지역이 없으면 모두 0) · 1: 수도권 · 2: 그 밖"""
    wants = preferred_sidos(preferred)
    if not wants:
        return 0
    sido = to_sido((job_region or "").split()[0] if job_region and job_region.split() else None)
    if sido in wants:
        return 0
    return 1 if sido in CAPITAL_AREA else 2


def region_allowed(preferred: str | None, tier: int, policy: str) -> bool:
    """후보로 둘지. 희망 지역이 없으면 전부.
    METRO: 희망 시 · 도 + (희망 지역에 수도권이 있으면) 수도권 전체. 희망 지역이 수도권 밖뿐이면 그 시 · 도만
    STRICT: 희망 시 · 도만
    """
    wants = preferred_sidos(preferred)
    if not wants or tier == 0:
        return True
    return policy == "METRO" and tier == 1 and any(w in CAPITAL_AREA for w in wants)


def _skill_evidence(profile: CompetencyProfile, names: list[str]) -> list[EvidenceRef]:
    refs = dict.fromkeys(
        (ev.ref_type, ev.ref_id)
        for skill in profile.skills
        if canonical_skill(skill.name) in names
        for ev in skill.evidence
    )
    return [EvidenceRef(ref_type=t, ref_id=i) for t, i in refs]


def build_reasons(
    profile: CompetencyProfile, job_posting_id: int, matched_terms: list[str], matched_skills: list[str]
) -> list[RecommendReason]:
    """겹친 단어로 만든 추천 근거 (LLM 없음). 임시 추천 · 임베딩 추천이 같이 쓴다.

    - 보유 기술이 겹치면: 그 기술의 프로필 근거(PROJECT 등)
    - 희망 직무 단어가 공고에 나오면: 공고 근거 JOB_POSTING(refId = jobPostingId)
    겹친 게 없으면 [] (임베딩 추천은 별도 문장을 붙인다)
    """
    reasons: list[RecommendReason] = []
    evidence = _skill_evidence(profile, matched_skills)
    if matched_skills and evidence:
        reasons.append(RecommendReason(text=f"보유 기술 {' · '.join(matched_skills)}이(가) 공고에 나옵니다", evidence=evidence))
    if matched_terms:
        reasons.append(RecommendReason(
            text=f"희망 직무 '{' · '.join(matched_terms)}'와 공고 직무가 맞습니다",
            evidence=[job_posting_ref(job_posting_id)],
        ))
    return reasons


def job_posting_ref(job_posting_id: int) -> EvidenceRef:
    return EvidenceRef(ref_type="JOB_POSTING", ref_id=job_posting_id)


def matched_words(text: str, terms: dict[str, re.Pattern], skills: dict[str, list[re.Pattern]]) -> tuple[list[str], list[str]]:
    """(공고에 나온 희망 직무 단어, 공고에 나온 보유 기술 대표 이름)"""
    return (
        [t for t, rx in terms.items() if rx.search(text)],
        [name for name, rxs in skills.items() if any(rx.search(text) for rx in rxs)],
    )


def recommend_from_index(req: RecommendRequest, store: IndexStore) -> RecommendData:
    # 저장소 쓰기는 임시 파일 → 교체라 잠금 없이 읽어도 깨진 파일을 읽지 않는다 (색인 중에도 추천이 막히지 않게)
    entries = store.load()
    if not entries:
        logger.warning("recommend fallback: index store is empty (path=%s)", store.path)
        return RecommendData(items=[])

    profile = CompetencyProfile.model_validate(req.profile or {})
    terms = {t: term_pattern(t) for t in job_terms(profile.desired_job)}
    skills = skill_patterns(profile)
    excluded = set(req.exclude_job_posting_ids)

    scored: list[Scored] = []
    for key, entry in entries.items():
        job_id = int(key)
        if job_id in excluded or entry.get("careerRequired"):
            continue
        tier = region_tier(profile.preferred_region, entry.get("region"))
        if not region_allowed(profile.preferred_region, tier, config.REGION_POLICY):
            continue
        matched_terms, matched_skills = matched_words(entry.get("matchText") or "", terms, skills)
        s = Scored(job_id, entry, tier, matched_terms, matched_skills)
        if s.score > 0:
            scored.append(s)
    scored.sort(key=lambda s: (s.tier, -s.score, s.job_posting_id))

    picked: list[Scored] = []
    seen: set[tuple[str, str]] = set()
    for s in scored:
        company, title = s.entry.get("companyName"), s.entry.get("title")
        if company and title:
            if (company, title) in seen:
                continue
            seen.add((company, title))
        picked.append(s)
        if len(picked) >= max(req.top_k, 0):
            break

    logger.info(
        "recommend fallback studentId=%d candidates=%d matched=%d picked=%d",
        req.student_id, len(entries), len(scored), len(picked),
    )
    return RecommendData(items=[
        RecommendItem(
            job_posting_id=s.job_posting_id, rank=rank,
            reasons=build_reasons(profile, s.job_posting_id, s.job_terms, s.skills),
        )
        for rank, s in enumerate(picked, start=1)
    ])
