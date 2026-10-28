// 주인: BE-A
package com.ieum.recommendation;

/**
 * 추천 묶음을 새로 저장했을 때 발행하는 이벤트 (VISIT · REFRESH · BATCH, 0건 포함). 저장 트랜잭션 안에서 발행된다.
 * 받는 쪽은 @TransactionalEventListener(phase = AFTER_COMMIT) 로 받으면 저장이 확정된 뒤에만 실행된다.
 * - BE-B-06: studentId 의 상태를 다시 계산한다 (itemCount 0 이면 맞는 공고 없음)
 *
 * @param triggerType VISIT | REFRESH | BATCH
 * @param itemCount   저장한 추천 공고 수 (0~3)
 */
public record RecommendationCreatedEvent(
        Long studentId,
        Long recommendationId,
        String triggerType,
        int itemCount
) {
}
