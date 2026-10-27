// 주인: BE-A
package com.ieum.job;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * POST /api/admin/jobs/reindex 응답의 data.
 * status: DONE(완료) · SKIPPED(배치 · 재색인 실행 중 · 활성 공고 없음)
 */
public record JobReindexResult(
        String status,
        String message,
        int indexRequestedCount,
        int removeRequestedCount,
        JsonNode indexResult,
        String reindexedAt
) {

    static JobReindexResult skipped(String message, String reindexedAt) {
        return new JobReindexResult("SKIPPED", message, 0, 0, null, reindexedAt);
    }
}
