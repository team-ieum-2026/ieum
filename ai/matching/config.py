# 주인: AI-B
"""추천(matching/recommend.py) 설정. 팀 결정 · 평가 결과에 따라 여기 값만 바꾼다."""

from typing import Literal

# 임베딩 코사인 유사도로 고르는 후보 수. 이 후보들을 리랭커가 모두 점수 매긴다 (CPU 에서 후보 수만큼 느려진다)
CANDIDATE_K = 20

# 리랭커 점수(0~1, 공용 리랭커가 Sigmoid 로 고정) 기준선. 이보다 낮은 공고는 추천하지 않는다 (억지로 채우지 않음)
SCORE_THRESHOLD = 0.05

# 지역 정책 (팀 결정 전)
# - METRO: 희망 지역이 수도권이면 수도권 전부 후보. 희망 시 · 도와 같은 공고를 정렬에서 먼저
#          희망 지역이 수도권 밖뿐이면(예: 부산) 그 시 · 도 공고만 (공고가 수도권뿐이라 지금은 빈 결과)
# - STRICT: 희망 시 · 도의 공고만 후보 (희망 지역이 없으면 거르지 않음)
RegionPolicy = Literal["METRO", "STRICT"]
REGION_POLICY: RegionPolicy = "METRO"
