# 주인: AI-B
"""공고 색인 (AI-B-02): 전처리 → 임베딩 → jobs_index.json 저장.

- contentHash · preprocessVersion · embeddingModel 이 저장된 값과 모두 같고 벡터가 있으면 건너뛴다(skipped).
  벡터가 null 인 공고(임베딩 준비 전에 색인된 것)는 건너뛰지 않고 다시 계산한다.
- 임베딩은 공고마다 [텍스트 1개]로 따로 부르고, 결과가 벡터 1개가 아니면 그 공고만 실패(failed)로 둔다.
- common.embedding 이 없거나 NotImplementedError 면 전처리 결과만 저장하고 vector 는 null (embeddingStatus: NOT_READY).
- 공고 하나가 실패해도 나머지는 계속한다. 실패한 공고는 기존 저장 값을 그대로 둔다.
"""

import logging
from collections.abc import Callable, Sequence
from datetime import datetime, timedelta, timezone
from typing import Any

from pydantic import ValidationError

from common.config import get_settings
from matching.models import IndexData, IndexJob, IndexRequest
from matching.preprocess import PREPROCESS_VERSION, preprocess
from matching.store import Entry, IndexStore

logger = logging.getLogger("ieum.matching")

KST = timezone(timedelta(hours=9))

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


def _one_vector(result: Any) -> list[float] | None:
    """[텍스트 1개] 를 넣었을 때 벡터 1개가 왔는지. 아니면 None."""
    if not isinstance(result, (list, tuple)) or len(result) != 1:
        return None
    vector = result[0]
    if not isinstance(vector, (list, tuple)) or not vector:
        return None
    if not all(isinstance(v, (int, float)) and not isinstance(v, bool) for v in vector):
        return None
    return [float(v) for v in vector]


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
    indexed = skipped = removed = 0
    failed_ids: list[int] = []
    failed_unknown = 0  # jobPostingId 조차 읽을 수 없는 공고
    now = datetime.now(KST).isoformat(timespec="seconds")

    with store.lock:
        entries = store.load()
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

            key = str(job.job_posting_id)
            if _is_fresh(entries.get(key), job, model_name):
                skipped += 1
                continue

            try:
                pre = preprocess(job)
            except Exception:
                logger.exception("index preprocess failed id=%d", job.job_posting_id)
                failed_ids.append(job.job_posting_id)
                continue

            vector = None
            if embed_ready:
                try:
                    vector = _one_vector(embed([pre.match_text]))
                except NotImplementedError:
                    logger.warning("embedding not implemented yet, saving preprocess only (vector=null)")
                    embed_ready = False
                except Exception as e:
                    logger.warning("index embedding failed id=%d error=%s", job.job_posting_id, type(e).__name__)
                    failed_ids.append(job.job_posting_id)
                    continue
                else:
                    if vector is None:
                        logger.warning("index embedding count mismatch id=%d (expected 1 vector)", job.job_posting_id)
                        failed_ids.append(job.job_posting_id)
                        continue

            entries[key] = {
                "contentHash": job.content_hash,
                "preprocessVersion": PREPROCESS_VERSION,
                "embeddingModel": model_name if vector is not None else None,
                "matchText": pre.match_text,
                "careerRequired": pre.career_required,
                "region": pre.region,
                "minEducation": job.min_education,
                "vector": vector,
                "indexedAt": now,
            }
            indexed += 1

        for job_id in dict.fromkeys(req.removed_job_posting_ids):
            if entries.pop(str(job_id), None) is not None:
                removed += 1

        if indexed or removed:
            store.save(entries)

    data = IndexData(
        indexed_count=indexed,
        skipped_count=skipped,
        removed_count=removed,
        failed_count=len(failed_ids) + failed_unknown,
        failed_job_posting_ids=failed_ids,
        embedding_status="OK" if embed_ready else "NOT_READY",
    )
    logger.info(
        "index done indexed=%d skipped=%d removed=%d failed=%d embedding=%s total=%d",
        indexed, skipped, removed, data.failed_count, data.embedding_status, len(entries),
    )
    return data
