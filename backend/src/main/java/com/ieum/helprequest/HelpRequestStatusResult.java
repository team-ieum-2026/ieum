// 주인: BE-B
package com.ieum.helprequest;

import java.time.OffsetDateTime;

/** 코디네이터 처리 상태 변경 응답. consultationAt 은 상담 예정 일시(없으면 null) */
public record HelpRequestStatusResult(Long helpRequestId, String status, OffsetDateTime consultationAt) {}
