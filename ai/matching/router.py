# 주인: AI-B
"""POST /ai/matching/index — 공고 임베딩 색인 (AI-B-02, S3). 호출: BE-A (일 배치 후)
POST /ai/matching/recommend — 프로필 → Top-3 공고 + 추천 근거 (AI-B-03, S3 · S4). 호출: BE-A
"""

from fastapi import APIRouter, Request

from common.response import not_implemented

router = APIRouter(prefix="/ai/matching", tags=["matching"])


@router.post("/index")
async def index_jobs(request: Request):
    # TODO(AI-B-02): contentHash 가 바뀐 공고만 common.embedding.embed_texts 로 다시 계산
    return not_implemented()


@router.post("/recommend")
async def recommend(request: Request):
    # TODO(AI-B-03): 요청 { studentId, profile, excludeJobPostingIds, topK }
    # 임베딩으로 후보 → common.reranker.rerank 로 Top-3 → 회사명 · 제목 같은 공고는 하나로 합침
    # 응답: {"data": {"items": [{ jobPostingId, rank, reasons: [{ text, evidence: [EvidenceRef] }] }]}}
    return not_implemented()
