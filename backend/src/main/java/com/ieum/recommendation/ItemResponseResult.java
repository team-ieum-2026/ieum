// 주인: BE-A
package com.ieum.recommendation;

/** 응답 저장 결과. APPLY 면 만든 지원서 번호(M5 이동용), PASS 면 applicationId 는 null */
public record ItemResponseResult(Long itemId, String response, Long applicationId) {
}
