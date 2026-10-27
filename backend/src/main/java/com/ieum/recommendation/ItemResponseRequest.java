// 주인: BE-A
package com.ieum.recommendation;

/**
 * POST /api/recommendation-items/{itemId}/response 요청 (M2 지원 · M3 패스). docs/API.md 2-1.
 * response: APPLY | PASS. PASS 면 passReason 필수, passNote 는 선택(기타 입력).
 */
public record ItemResponseRequest(String response, String passReason, String passNote) {
}
