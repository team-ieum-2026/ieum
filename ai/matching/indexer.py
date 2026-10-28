# 주인: AI-B
"""공고 색인 (AI-B-02): 전처리 → 임베딩 → jobs_index.json 저장.

- contentHash · preprocessVersion · embeddingModel 이 저장된 값과 모두 같고 벡터가 있으면 건너뛴다(skipped).
  벡터가 null 인 공고(임베딩 준비 전에 색인된 것)는 건너뛰지 않고 다시 계산한다.
- 임베딩은 묶음당 1번: 이번 요청에서 임베딩할 공고 텍스트를 한 번에 공용 embed_texts 로 넘긴다
  (문서용 task_type 은 embed_texts 기본값 RETRIEVAL_DOCUMENT). 결과 개수가 다르거나 호출이 실패하면
  그 묶음의 공고 모두 실패(failed)로 두고, 전처리 결과는 vector null 로 저장해 아래 "다시 시도"에서 채운다.
- common.embedding 이 없거나 NotImplementedError 면 전처리 결과만 저장하고 vector 는 null (embeddingStatus: NOT_READY).
- 형식 · 전처리 실패는 저장하지 않고 기존 값을 그대로 둔다. 공고 하나가 실패해도 나머지는 계속한다.
- 다시 시도: BE 는 한 번 보낸 공고를 다시 보내지 않으므로(contentHash 가 같으면), /index 가 불릴 때마다
  이번 요청의 공고를 처리한 뒤 저장소에서 vector 가 null 이거나 embeddingModel 이 현재 모델과 다른 공고를
  최대 RETRY_LIMIT 건 골라 저장된 matchText 로 다시 임베딩한다 (이것도 묶음 1번). 마지막 시도가 오래된 공고부터
  고른다 (계속 실패하는 공고가 다른 공고의 차례를 막지 않게). 임베딩 모듈이 준비 전이면 건너뛴다.
- 그래서 /index 한 번에 임베딩 API 호출은 최대 2번이다 (이번 요청 묶음 1번 + 다시 시도 1번).
"""

import html
import logging
from collections.abc import Callable, Sequence
from datetime import datetime, timedelta, timezone
from typing import Any

from pydantic import ValidationError

from common.config import get_settings
from matching.models import IndexData, IndexJob, IndexRequest
from matching.preprocess import PREPROCESS_VERSION, Preprocessed, preprocess
from matching.store import Entry, IndexStore

logger = logging.getLogger("ieum.matching")

KST = timezone(timedelta(hours=9))

# /index 한 번에 다시 시도하는 최대 공고 수 (임베딩 API 한도 보호, BE 묶음 크기와 같게)
RETRY_LIMIT = 20

Embedder = Callable[[Sequence[str]], list[list[float]]]
_DEFAULT: Any = object()


def default_embedder() -> Embedder | None:
    try:
        from common.embedding import embed_texts
    except ImportError:
        return None
    return embed_texts


def default_model_name() -> str | None:
    return get_settings().embedding_model or None


def _valid_vector(vector: Any) -> list[float] | None:
    if not isinstance(vector, (list, tuple)) or not vector:
        return None
    if not all(isinstance(v, (int, float)) and not isinstance(v, bool) for v in vector):
        return None
    return [float(v) for v in vector]


def _vectors(result: Any, count: int) -> list[list[float]] | None:
    """텍스트 count 개를 넣었을 때 벡터 count 개가 왔는지. 개수가 다르거나 하나라도 잘못되면 None (묶음 전체 실패)."""
    if not isinstance(result, (list, tuple)) or len(result) != count:
        return None
    vectors = [_valid_vector(v) for v in result]
    return None if any(v is None for v in vectors) else vectors


def _is_fresh(old: Entry | None, job: IndexJob, model_name: str | None) -> bool:
    return (
        old is not None
        and old.get("contentHash") == job.content_hash
        and old.get("preprocessVersion") == PREPROCESS_VERSION
        and old.get("embeddingModel") == model_name
        and old.get("vector") is not None
    )


def run_index(
    req: IndexRequest,
    store: IndexStore,
    *,
    embed: Embedder | None = _DEFAULT,
    model_name: str | None = _DEFAULT,
) -> IndexData:
    if embed is _DEFAULT:
        embed = default_embedder()
    if model_name is _DEFAULT:
        model_name = default_model_name()
    embed_ready = embed is not None
    indexed = skipped = removed = retried = 0
    failed_ids: list[int] = []
    failed_unknown = 0  # jobPostingId 조차 읽을 수 없는 공고
    attempted: set[str] = set()  # 이번 요청에서 이미 임베딩을 시도한 공고 (다시 시도 대상에서 뺀다)
    dirty = False
    now = datetime.now(KST).isoformat(timespec="seconds")

    def try_embed_batch(texts: list[str], what: str) -> tuple[list[list[float]] | None, bool]:
        """(벡터 목록 또는 None, 시도했는지). 모듈 준비 전(NotImplementedError)이면 embed_ready 를 끄고 시도 안 한 것으로."""
        nonlocal embed_ready
        try:
            vectors = _vectors(embed(texts), len(texts))
        except NotImplementedError:
            logger.warning("embedding not implemented yet, saving preprocess only (vector=null)")
            embed_ready = False
            return None, False
        except Exception as e:
            logger.warning("index embedding failed %s count=%d error=%s", what, len(texts), type(e).__name__)
            return None, True
        if vectors is None:
            logger.warning("index embedding count mismatch %s (expected %d vectors)", what, len(texts))
        return vectors, True

    with store.lock:
        entries = store.load()

        # 1) 형식 검증 · 건너뛰기 · 전처리
        pending: list[tuple[IndexJob, Preprocessed]] = []
        for raw in req.jobs:
            try:
                job = IndexJob.model_validate(raw)
            except ValidationError as e:
                raw_id = raw.get("jobPostingId") if isinstance(raw, dict) else None
                logger.warning("index job invalid id=%s errors=%d", raw_id, e.error_count())
                if isinstance(raw_id, int):
                    failed_ids.append(raw_id)
                else:
                    failed_unknown += 1
                continue

            if _is_fresh(entries.get(str(job.job_posting_id)), job, model_name):
                skipped += 1
                continue

            try:
                pre = preprocess(job)
            except Exception:
                logger.exception("index preprocess failed id=%d", job.job_posting_id)
                failed_ids.append(job.job_posting_id)
                continue
            pending.append((job, pre))

        # 2) 이번 요청 공고를 묶음 1번으로 임베딩
        vectors: list[list[float]] | None = None
        tried = False
        if embed_ready and pending:
            vectors, tried = try_embed_batch([pre.match_text for _, pre in pending], "request")
            attempted.update(str(job.job_posting_id) for job, _ in pending)

        # 3) 저장
        for i, (job, pre) in enumerate(pending):
            vector = vectors[i] if vectors is not None else None
            entries[str(job.job_posting_id)] = {
                "contentHash": job.content_hash,
                "preprocessVersion": PREPROCESS_VERSION,
                "embeddingModel": model_name if vector is not None else None,
                "matchText": pre.match_text,
                "careerRequired": pre.career_required,
                "region": pre.region,
                "education": job.education,
                # 추천에서 회사명 · 제목이 같은 공고(근무지만 다름)를 하나로 합칠 때 쓴다
                "companyName": html.unescape(job.company_name or "").strip() or None,
                "title": html.unescape(job.title or "").strip() or None,
                "vector": vector,
                "indexedAt": now,
                "embedAttemptedAt": now if tried else None,
            }
            dirty = True
            if tried and vector is None:
                failed_ids.append(job.job_posting_id)  # 전처리는 저장됨 → 다음 /index 때 다시 시도
            else:
                indexed += 1

        for job_id in dict.fromkeys(req.removed_job_posting_ids):
            if entries.pop(str(job_id), None) is not None:
                removed += 1
                dirty = True

        # 4) 저장소의 vector 없는 · 모델 다른 공고 다시 시도 (묶음 1번)
        if embed_ready:
            retried, retry_dirty = _retry_missing(entries, attempted, model_name, now, try_embed_batch)
            dirty = dirty or retry_dirty

        if dirty:
            store.save(entries)

    data = IndexData(
        indexed_count=indexed,
        skipped_count=skipped,
        removed_count=removed,
        failed_count=len(failed_ids) + failed_unknown,
        failed_job_posting_ids=failed_ids,
        retried_count=retried,
        embedding_status="OK" if embed_ready else "NOT_READY",
    )
    logger.info(
        "index done indexed=%d skipped=%d removed=%d failed=%d retried=%d embedding=%s total=%d",
        indexed, skipped, removed, data.failed_count, retried, data.embedding_status, len(entries),
    )
    return data


def _needs_embedding(entry: Entry, model_name: str | None) -> bool:
    return entry.get("vector") is None or entry.get("embeddingModel") != model_name


def _retry_missing(
    entries: dict[str, Entry],
    attempted: set[str],
    model_name: str | None,
    now: str,
    try_embed_batch: Callable[[list[str], str], tuple[list[list[float]] | None, bool]],
) -> tuple[int, bool]:
    """vector 가 없거나 모델이 다른 공고를 최대 RETRY_LIMIT 건 묶음 1번으로 다시 임베딩한다. (채운 수, 저장 필요 여부)

    마지막 시도 시각(embedAttemptedAt)이 없거나 오래된 공고부터, 같으면 jobPostingId 순.
    matchText 는 저장된 것을 그대로 쓴다 (원문이 없어 다시 전처리하지 않는다).
    """
    targets = sorted(
        (key for key, entry in entries.items() if key not in attempted and _needs_embedding(entry, model_name)),
        key=lambda key: (entries[key].get("embedAttemptedAt") or "", int(key)),
    )[:RETRY_LIMIT]
    if not targets:
        return 0, False
    vectors, tried = try_embed_batch([entries[key].get("matchText") or "" for key in targets], "retry")
    if not tried:  # 모듈 준비 전이면 시도 기록도 남기지 않는다
        return 0, False
    filled = 0
    for i, key in enumerate(targets):
        entry = entries[key]
        entry["embedAttemptedAt"] = now
        if vectors is not None:
            entry["vector"] = vectors[i]
            entry["embeddingModel"] = model_name
            filled += 1
    logger.info("index retry targets=%d filled=%d", len(targets), filled)
    return filled, True
