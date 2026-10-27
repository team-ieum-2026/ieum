# 주인: AI-B
"""임시 추천 (AI-B-03 임시 방식): 색인 저장소의 공고를 규칙 필터 + 단어 겹침 점수로 고른다.

임베딩 · 리랭커(common.embedding · common.reranker)가 들어오면 이 파일의 순위 매기기를 교체한다.
(후보: 임베딩 유사도 → 리랭커 Top-3. 규칙 필터 · 중복 합치기 · 응답 형식은 그대로 쓸 수 있다)

1. 후보: jobs_index.json 의 공고 중 excludeJobPostingIds 에 없는 것
2. 규칙 필터: careerRequired 공고 제외. 학력 필터는 아직 없음
3. 점수: 프로필 desiredJob 단어 · skills 이름(별칭 포함)이 공고 matchText 에 나오는 개수(서로 다른 단어 수)
4. 순서: 희망 지역과 같은 시 · 도 → 수도권 → 그 밖, 같은 묶음 안에서는 점수 높은 순, 동점이면 jobPostingId 순
5. 점수 0 인 공고는 추천하지 않는다. 남는 공고가 없으면 items: [] (억지로 채우지 않음)
6. 회사명 · 제목이 같은 공고(근무지만 다름)는 하나만 남긴다
7. reasons: 겹친 단어로 만든 한 줄. evidence 는 겹친 skills 의 기존 근거. LLM 은 쓰지 않는다
"""

import logging
import re
from dataclasses import dataclass

from matching.models import RecommendData, RecommendItem, RecommendReason, RecommendRequest
from matching.preprocess import unify_region
from matching.store import Entry, IndexStore
from profile.skill_aliases import SKILL_ALIASES, canonical_skill
from schemas import CompetencyProfile, EvidenceRef

logger = logging.getLogger("ieum.matching")

CAPITAL_AREA = ("서울", "경기", "인천")
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


def region_tier(preferred: str | None, job_region: str | None) -> int:
    """0: 희망 시 · 도와 같음 (희망 지역이 없으면 모두 0) · 1: 수도권 · 2: 그 밖"""
    want = (unify_region(preferred) or "").split()[:1]
    if not want:
        return 0
    sido = (job_region or "").split()[:1]
    if sido == want:
        return 0
    return 1 if sido and sido[0] in CAPITAL_AREA else 2


def _skill_evidence(profile: CompetencyProfile, names: list[str]) -> list[EvidenceRef]:
    refs = dict.fromkeys(
        (ev.ref_type, ev.ref_id)
        for skill in profile.skills
        if canonical_skill(skill.name) in names
        for ev in skill.evidence
    )
    return [EvidenceRef(ref_type=t, ref_id=i) for t, i in refs]


def reason(profile: CompetencyProfile, scored: Scored) -> RecommendReason:
    job = " · ".join(scored.job_terms)
    skills = " · ".join(scored.skills)
    if scored.job_terms and scored.skills:
        text = f"희망 직무 '{job}'와 맞고, 보유 기술 {skills}이(가) 공고에 나옵니다"
    elif scored.skills:
        text = f"보유 기술 {skills}이(가) 공고에 나옵니다"
    else:
        text = f"희망 직무 '{job}'와 공고 직무가 맞습니다"
    # 희망 직무는 근거 refType 이 없어 skills 근거만 붙는다 (희망 직무만 겹치면 evidence 는 [])
    return RecommendReason(text=text, evidence=_skill_evidence(profile, scored.skills))


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
        text = entry.get("matchText") or ""
        matched_terms = [t for t, rx in terms.items() if rx.search(text)]
        matched_skills = [name for name, rxs in skills.items() if any(rx.search(text) for rx in rxs)]
        s = Scored(job_id, entry, region_tier(profile.preferred_region, entry.get("region")), matched_terms, matched_skills)
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
        RecommendItem(job_posting_id=s.job_posting_id, rank=rank, reasons=[reason(profile, s)])
        for rank, s in enumerate(picked, start=1)
    ])
