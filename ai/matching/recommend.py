# 주인: AI-B
"""공고 추천 (AI-B-03): 색인 공고 → 규칙 필터 → 임베딩 후보 → 리랭커 → Top-K.

① 후보: 색인 공고 중 excludeJobPostingIds 에 없고, careerRequired 아니고, vector 가 있는 것
   (vector 는 지금 임베딩 모델로 만든 것만. 모델이 바뀐 공고는 /index 다시 시도에서 채워진다)
② 지역: config.REGION_POLICY (fallback.region_allowed). 희망 지역은 "서울, 경기"처럼 여러 개일 수 있다
   METRO: 희망 지역에 수도권이 있으면 수도권 전부 후보(희망 시 · 도 우선 정렬), 수도권 밖뿐이면(예: 부산) 그 시 · 도만
   STRICT: 희망 시 · 도만. 희망 지역이 없으면 둘 다 전체
③ 검색어: 프로필(희망 직무 · 기술 · 역할 · 성과 · 자격증) 한 문장 → embed_texts(task_type="RETRIEVAL_QUERY")
④ 코사인 유사도 상위 config.CANDIDATE_K
⑤ 공용 rerank 로 후보 전부 점수 (0~1)
⑥ config.SCORE_THRESHOLD 미만 제외
⑦ 회사명 · 제목 같은 공고 하나만 → 정렬(지역 우선 → 점수) → 상위 topK
⑧ 0건이면 items: [] 와 이유(INDEX_EMPTY · PROFILE_EMPTY · NO_CANDIDATES · BELOW_THRESHOLD)를 로그로
⑨ reasons: LLM 없이 겹친 단어로 (fallback.build_reasons). 기술 → 프로필 근거, 희망 직무 → JOB_POSTING 근거
⑩ 단계별 시간(검색어 임베딩 · 리랭크 · 전체)을 로그로

대체 경로: 임베딩 · 리랭커 호출이 실패하거나(키 · 모델 없음, 로드 실패 등) 벡터 있는 공고가 하나도 없으면
fallback.recommend_from_index(단어 겹침 규칙 추천)로 응답하고 경고 로그를 남긴다. 서비스는 멈추지 않는다.
"""

import logging
import math
import time
from collections.abc import Callable, Sequence
from dataclasses import dataclass
from typing import Any

from matching import config
from matching.fallback import (
    build_reasons,
    job_posting_ref,
    job_terms,
    matched_words,
    recommend_from_index,
    region_allowed,
    region_tier,
    skill_patterns,
    term_pattern,
)
from matching.indexer import default_model_name
from matching.models import RecommendData, RecommendItem, RecommendReason, RecommendRequest
from matching.store import Entry, IndexStore
from schemas import CompetencyProfile

logger = logging.getLogger("ieum.matching")

QueryEmbedder = Callable[[Sequence[str]], list[list[float]]]
Reranker = Callable[..., list[tuple[int, float]]]
_DEFAULT: Any = object()

# 기술 · 직무가 겹치지 않아도 의미가 가까워 추천된 공고에 붙이는 문장 (근거는 공고)
GENERIC_REASON = "프로필의 직무 · 역량과 공고 내용이 잘 맞습니다"


class FallbackNeeded(Exception):
    """임베딩 · 리랭커 경로를 쓸 수 없어 규칙 추천으로 넘어간다."""


@dataclass
class Candidate:
    job_posting_id: int
    entry: Entry
    tier: int
    similarity: float = 0.0
    score: float = 0.0


def default_query_embedder() -> QueryEmbedder:
    from common.embedding import embed_texts

    return lambda texts: embed_texts(texts, task_type="RETRIEVAL_QUERY")


def default_reranker() -> Reranker:
    from common.reranker import rerank

    return rerank


def build_query(profile: CompetencyProfile) -> str:
    """수료생 검색어. 구성을 바꾸려면 이 함수만 고친다. 지역 · 경력 조건은 코드 필터로 하므로 넣지 않는다."""
    parts = []
    if profile.desired_job:
        parts.append(f"희망 직무: {profile.desired_job}")
    if profile.skills:
        parts.append("보유 기술: " + ", ".join(s.name for s in profile.skills))
    if profile.roles:
        parts.append("맡은 역할: " + ", ".join(profile.roles))
    if profile.achievements:
        parts.append("성과: " + "; ".join(a.text for a in profile.achievements))
    if profile.certificates:
        parts.append("자격증: " + ", ".join(profile.certificates))
    return ". ".join(parts)


def cosine(a: Sequence[float], b: Sequence[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    norm = math.sqrt(sum(x * x for x in a)) * math.sqrt(sum(y * y for y in b))
    return dot / norm if norm else 0.0


def _empty(req: RecommendRequest, reason: str, **detail: Any) -> RecommendData:
    logger.info("recommend empty studentId=%d reason=%s %s", req.student_id, reason,
                " ".join(f"{k}={v}" for k, v in detail.items()))
    return RecommendData(items=[])


def recommend_jobs(
    req: RecommendRequest,
    store: IndexStore,
    *,
    embed_query: QueryEmbedder | None = _DEFAULT,
    rerank_fn: Reranker | None = _DEFAULT,
    model_name: str | None = _DEFAULT,
) -> RecommendData:
    started = time.perf_counter()
    try:
        return _recommend(req, store, embed_query, rerank_fn, model_name, started)
    except FallbackNeeded as e:
        logger.warning("recommend fallback to rules studentId=%d reason=%s", req.student_id, e)
        return recommend_from_index(req, store)


def _recommend(req, store, embed_query, rerank_fn, model_name, started) -> RecommendData:
    entries = store.load()
    if not entries:
        logger.warning("recommend: index store is empty (path=%s)", store.path)
        return _empty(req, "INDEX_EMPTY")

    profile = CompetencyProfile.model_validate(req.profile or {})
    query = build_query(profile)
    if not query:
        return _empty(req, "PROFILE_EMPTY")

    if model_name is _DEFAULT:
        model_name = default_model_name()
    usable = {k: e for k, e in entries.items() if e.get("vector") and e.get("embeddingModel") == model_name}
    if not usable:
        raise FallbackNeeded(f"no vectors in index (entries={len(entries)} model={model_name})")

    # ① ② 규칙 필터
    excluded = set(req.exclude_job_posting_ids)
    candidates: list[Candidate] = []
    for key, entry in usable.items():
        job_id = int(key)
        if job_id in excluded or entry.get("careerRequired"):
            continue
        tier = region_tier(profile.preferred_region, entry.get("region"))
        if not region_allowed(profile.preferred_region, tier, config.REGION_POLICY):
            continue
        candidates.append(Candidate(job_id, entry, tier))
    if not candidates:
        return _empty(req, "NO_CANDIDATES", usable=len(usable), excluded=len(excluded), policy=config.REGION_POLICY)

    # ③ 검색어 임베딩
    t0 = time.perf_counter()
    try:
        embed = default_query_embedder() if embed_query is _DEFAULT else embed_query
        if embed is None:
            raise FallbackNeeded("query embedder missing")
        result = embed([query])
    except FallbackNeeded:
        raise
    except Exception as e:
        raise FallbackNeeded(f"query embedding failed: {type(e).__name__}") from e
    embed_ms = int((time.perf_counter() - t0) * 1000)
    if not isinstance(result, (list, tuple)) or len(result) != 1 or not result[0]:
        raise FallbackNeeded("query embedding returned no vector")
    query_vector = [float(v) for v in result[0]]

    # ④ 코사인 상위 CANDIDATE_K (차원이 다른 벡터는 다른 모델 것이라 뺀다)
    same_dim = [c for c in candidates if len(c.entry["vector"]) == len(query_vector)]
    if not same_dim:
        raise FallbackNeeded("vector dimension mismatch")
    for c in same_dim:
        c.similarity = cosine(query_vector, c.entry["vector"])
    shortlist = sorted(same_dim, key=lambda c: (-c.similarity, c.job_posting_id))[: config.CANDIDATE_K]

    # ⑤ 리랭커로 후보 전부 점수
    t1 = time.perf_counter()
    try:
        rerank = default_reranker() if rerank_fn is _DEFAULT else rerank_fn
        if rerank is None:
            raise FallbackNeeded("reranker missing")
        ranked = rerank(query, [c.entry.get("matchText") or "" for c in shortlist], top_k=len(shortlist))
    except FallbackNeeded:
        raise
    except Exception as e:
        raise FallbackNeeded(f"rerank failed: {type(e).__name__}") from e
    rerank_ms = int((time.perf_counter() - t1) * 1000)
    for index, score in ranked:
        if not 0.0 <= score <= 1.0:
            # 공용 리랭커는 Sigmoid 로 0~1 을 보장한다. 아니면 기준선이 의미 없어 규칙 추천으로
            raise FallbackNeeded(f"rerank score out of range: {score}")
        shortlist[index].score = score

    # ⑥ 기준선
    passed = [c for c in shortlist if c.score >= config.SCORE_THRESHOLD]
    if not passed:
        best = max((c.score for c in shortlist), default=0.0)
        return _empty(req, "BELOW_THRESHOLD", reranked=len(shortlist), bestScore=round(best, 4),
                      threshold=config.SCORE_THRESHOLD)

    # ⑦ 회사명 · 제목 같은 공고는 점수 높은 하나만 → 지역 우선 → 점수 → id
    picked: list[Candidate] = []
    seen: set[tuple[str, str]] = set()
    for c in sorted(passed, key=lambda c: (-c.score, c.job_posting_id)):
        company, title = c.entry.get("companyName"), c.entry.get("title")
        if company and title:
            if (company, title) in seen:
                continue
            seen.add((company, title))
        picked.append(c)
    picked.sort(key=lambda c: (c.tier, -c.score, c.job_posting_id))
    picked = picked[: max(req.top_k, 0)]

    # ⑨ reasons
    terms = {t: term_pattern(t) for t in job_terms(profile.desired_job)}
    skills = skill_patterns(profile)
    items = []
    for rank, c in enumerate(picked, start=1):
        matched_terms, matched_skills = matched_words(c.entry.get("matchText") or "", terms, skills)
        reasons = build_reasons(profile, c.job_posting_id, matched_terms, matched_skills)
        if not reasons:
            reasons = [RecommendReason(text=GENERIC_REASON, evidence=[job_posting_ref(c.job_posting_id)])]
        items.append(RecommendItem(job_posting_id=c.job_posting_id, rank=rank, reasons=reasons))

    # ⑩ 시간
    logger.info(
        "recommend done studentId=%d usable=%d candidates=%d reranked=%d passed=%d picked=%d "
        "embed_ms=%d rerank_ms=%d total_ms=%d top=%s",
        req.student_id, len(usable), len(candidates), len(shortlist), len(passed), len(items),
        embed_ms, rerank_ms, int((time.perf_counter() - started) * 1000),
        [(c.job_posting_id, round(c.score, 4)) for c in picked],
    )
    return RecommendData(items=items)
