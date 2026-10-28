// 주인: BE-B
package com.ieum.coordinator;

/**
 * 코디네이터의 승인·반려·전달 완료를 저장하는 트랜잭션 안에서 발행할 내부 이벤트(BE-B-06).
 * CoordinatorApplicationService가 발행하며, 상태 재계산은 원문 커밋 후에만 수행한다.
 * APPROVED·REJECTED·DELIVERED 변경은 수료생의 응답이 아니므로 student.last_response_at을 갱신하지 않는다.
 * 수신자는 이벤트에 상태를 복사하지 않고 커밋된 지원서 원문을 다시 읽는다.
 */
public record ApplicationReviewChangedEvent(Long studentId, Long applicationId) {}
