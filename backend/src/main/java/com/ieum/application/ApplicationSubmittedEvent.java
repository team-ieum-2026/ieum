// 주인: BE-A
package com.ieum.application;

/**
 * 수료생이 서류를 담당자에게 검토 요청(SUBMITTED)했을 때 발행하는 이벤트. 제출 트랜잭션 안에서 발행된다.
 * BE-B-06 상태 재계산은 @TransactionalEventListener(phase = AFTER_COMMIT) 로 받는다.
 */
public record ApplicationSubmittedEvent(Long studentId, Long applicationId) {
}
