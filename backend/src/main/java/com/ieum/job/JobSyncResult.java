// 주인: BE-A
package com.ieum.job;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * POST /api/admin/jobs/sync 응답의 data.
 * status: DONE(완료) · SKIPPED(API 실패 · 이미 실행 중 · 키 없음 → 기존 데이터 유지)
 */
public record JobSyncResult(
        String status,
        String message,
        int fetchedCount,
        int itCount,
        int insertedCount,
        int updatedCount,
        int unchangedCount,
        int deactivatedCount,
        int indexRequestedCount,
        JsonNode indexResult,
        String syncedAt
) {

    static JobSyncResult skipped(String message, String syncedAt) {
        return new JobSyncResult("SKIPPED", message, 0, 0, 0, 0, 0, 0, 0, null, syncedAt);
    }
}
