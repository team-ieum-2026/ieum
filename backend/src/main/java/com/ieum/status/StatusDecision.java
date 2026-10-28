// 주인: BE-B
package com.ieum.status;

/** student_status에 저장할 상태와 판정 근거. 구직 중일 때만 세부 상태를 둔다. */
public record StatusDecision(String status, String subStatus, String reason) {}
