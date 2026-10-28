// 주인: BE-B
package com.ieum.helprequest;

/**
 * 도움 요청 생성·처리 상태 변경을 저장하는 트랜잭션 안에서 발행할 내부 이벤트(BE-B-06).
 * 상태 재계산은 원문 커밋 후에만 수행한다. BE-B-05 저장 서비스의 실제 발행 연동은 별도 작업이다.
 * 생성 등 수료생 응답에 해당하는 저장은 원문과 같은 트랜잭션에서 실제 응답 시각으로
 * student.last_response_at을 단조 증가시켜야 한다. 코디네이터의 CONTACTED·CLOSED 처리는 갱신하지 않는다.
 * 이벤트 수신 시각을 수료생의 응답 시각으로 사용하지 않는다.
 */
public record HelpRequestChangedEvent(Long studentId, Long helpRequestId) {}
