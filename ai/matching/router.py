# 주인: AI-B
"""POST /ai/matching/index — 공고 임베딩 색인 (AI-B-02, S3). 호출: BE-A (일 배치 후)
POST /ai/matching/recommend — 프로필 → Top-3 공고 + 추천 근거 (AI-B-03, S3 · S4). 호출: BE-A
"""

from fastapi import APIRouter, Request

from common.response import not_implemented, ok
from matching.models import RecommendData, RecommendItem, RecommendReason, RecommendRequest
from schemas.evidence import EvidenceRef

router = APIRouter(prefix="/ai/matching", tags=["matching"])

# 고정 응답용 공고 3건 (모의 데이터). 임베딩 · 리랭커가 붙으면 지운다.
FIXED_ITEMS: list[RecommendItem] = [
    RecommendItem(
        job_posting_id=501,
        rank=1,
        reasons=[
            RecommendReason(
                text="모아요 프로젝트의 Spring Boot API 경험이 주요 업무와 맞아요",
                evidence=[EvidenceRef(ref_type="PROJECT", ref_id=11)],
            ),
            RecommendReason(
                text="목록 조회 응답을 1.8초에서 0.4초로 줄인 경험이 성능 개선 우대 조건과 이어져요",
                evidence=[EvidenceRef(ref_type="PROJECT", ref_id=11)],
            ),
        ],
    ),
    RecommendItem(
        job_posting_id=502,
        rank=2,
        reasons=[
            RecommendReason(
                text="Gemini API를 연동한 프로젝트 경험이 LLM 서비스 개발 업무와 맞아요",
                evidence=[EvidenceRef(ref_type="PROJECT", ref_id=12)],
            ),
            RecommendReason(
                text="AI Agent 과정에서 배운 내용이 공고의 필수 기술과 겹쳐요",
                evidence=[EvidenceRef(ref_type="COURSE", ref_id=1)],
            ),
        ],
    ),
    RecommendItem(
        job_posting_id=503,
        rank=3,
        reasons=[
            RecommendReason(
                text="SQLD 자격증이 데이터베이스 설계 우대 조건과 맞아요",
                evidence=[EvidenceRef(ref_type="CERTIFICATE", ref_id=2)],
            ),
            RecommendReason(
                text="자기소개서에 적은 협업 경험이 회사가 찾는 인재상과 맞아요",
                evidence=[EvidenceRef(ref_type="COVER_LETTER", ref_id=1)],
            ),
        ],
    ),
]


@router.post("/index")
async def index_jobs(request: Request):
    # TODO(AI-B-02): contentHash 가 바뀐 공고만 common.embedding.embed_texts 로 다시 계산
    return not_implemented()


@router.post("/recommend")
async def recommend(body: RecommendRequest) -> dict:
    # TODO(AI-B-03): 지금은 고정 응답. 임베딩으로 후보 → common.reranker.rerank 로 Top-3 → 회사명 · 제목 같은 공고는 하나로 합침
    # 맞는 공고가 없으면 억지로 추천하지 않고 items: [] (팀 결정). 고정 응답에서는 profile 이 {} 일 때로 흉내 낸다.
    if not body.profile:
        return ok(RecommendData(items=[]).model_dump(by_alias=True))

    excluded = set(body.exclude_job_posting_ids)
    candidates = [item for item in FIXED_ITEMS if item.job_posting_id not in excluded][: max(body.top_k, 0)]
    items = [item.model_copy(update={"rank": rank}) for rank, item in enumerate(candidates, start=1)]
    return ok(RecommendData(items=items).model_dump(by_alias=True))
