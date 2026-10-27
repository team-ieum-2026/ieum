// 주인: BE-A
package com.ieum.recommendation;

/**
 * 수료생이 추천 공고에 지원 · 패스했을 때 발행하는 이벤트. 응답 저장 트랜잭션 안에서 발행된다.
 * 받는 쪽은 @TransactionalEventListener(phase = AFTER_COMMIT) 로 받으면 저장이 확정된 뒤에만 실행된다.
 * - BE-A-07: response 가 APPLY 면 applicationId 로 AI 서류 생성을 시작한다
 * - BE-B-06: studentId 의 상태를 다시 계산한다
 *
 * @param applicationId APPLY 면 새로 만든 지원서 번호, PASS 면 null
 */
public record RecommendationRespondedEvent(
        Long studentId,
        Long itemId,
        Long jobPostingId,
        String response,
        Long applicationId
) {
}
