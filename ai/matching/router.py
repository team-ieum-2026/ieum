# 주인: AI-B
"""POST /ai/matching/index — 공고 임베딩 색인 (AI-B-02, S3). 호출: BE-A (일 배치 후)
POST /ai/matching/recommend — 프로필 → Top-3 공고 + 추천 근거 (AI-B-03, S3 · S4). 호출: BE-A
"""

import asyncio

from fastapi import APIRouter

from common.response import ok
from matching.indexer import run_index
from matching.models import IndexRequest, RecommendRequest
from matching.recommend import recommend_jobs
from matching.store import IndexStore

router = APIRouter(prefix="/ai/matching", tags=["matching"])

# 공고 색인 저장소 (ai/data/index/jobs_index.json). 테스트에서는 임시 경로로 바꾼다
store = IndexStore()


@router.post("/index")
async def index_jobs(body: IndexRequest) -> dict:
    # 요청: { jobs: [{ jobPostingId, contentHash, companyName, title, jobCategory, location, career, education, description }],
    #        removedJobPostingIds }  (BE 가 20건씩 나눠 보내고 removedJobPostingIds 는 첫 묶음에만)
    # 응답: { indexedCount, skippedCount, removedCount, failedCount, failedJobPostingIds, retriedCount, embeddingStatus }
    # BE(JobSyncService.indexInBatches)는 indexedCount · skippedCount · removedCount 만 읽어 더한다
    # 임베딩 · 파일 쓰기가 동기라 스레드에서 돌려 서버가 멈추지 않게 한다
    data = await asyncio.to_thread(run_index, body, store)
    return ok(data.model_dump(by_alias=True))


@router.post("/recommend")
async def recommend(body: RecommendRequest) -> dict:
    # 규칙 필터 → 임베딩 후보 → 리랭커 → Top-K (matching/recommend.py). 임베딩 · 리랭커가 실패하면 규칙 추천(fallback.py)
    # 맞는 공고가 없으면 억지로 추천하지 않고 items: [] (팀 결정)
    # 리랭커(CPU)가 오래 걸리므로 스레드에서 돌려 서버가 멈추지 않게 한다
    data = await asyncio.to_thread(recommend_jobs, body, store)
    return ok(data.model_dump(by_alias=True))
