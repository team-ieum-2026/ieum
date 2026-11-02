// 주인: BE-A
package com.ieum.recommendation;

/**
 * 추천 공고 응답(APPLY · PASS · HELP)을 취소해 NONE 으로 되돌렸다.
 * 받는 쪽은 @TransactionalEventListener(phase = AFTER_COMMIT) 로 받는다.
 * - BE-B-06: studentId 의 상태를 다시 계산한다 (지원 의사 · 연속 패스 근거가 사라질 수 있다)
 */
public record RecommendationResponseCancelledEvent(Long studentId, Long itemId, String cancelledResponse) {
}
