// 주인: BE-A
package com.ieum.application;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * GET /api/applications/{applicationId} 응답의 data (M6 · M6-1). docs/API.md 2-1.
 * status 가 GENERATING · FAILED 면 document 는 null.
 */
public record ApplicationDetailResponse(
        Long applicationId,
        String status,
        Job job,
        JsonNode document
) {

    public record Job(Long jobPostingId, String companyName, String title) {
    }

    /** PATCH 응답 { document } */
    public record DocumentOnly(JsonNode document) {
    }

    /** POST submit 응답 */
    public record Submitted(Long applicationId, String status) {
    }
}
