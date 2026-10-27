// 주인: BE-A
package com.ieum.job;

import java.util.List;

/**
 * POST /api/admin/companies/collect 응답의 data.
 * items[].result: SAVED(저장) · FAILED(AI 호출 실패 · 형식 오류, 그 기업의 기존 데이터 유지)
 */
public record CompanyCollectResult(
        int requestedCount,
        int savedCount,
        int failedCount,
        List<Item> items,
        String collectedAt
) {

    public record Item(
            String companyName,
            String result,
            Long companyInfoId,
            String status,
            String message
    ) {

        static Item saved(CompanyInfo row) {
            return new Item(row.getCompanyName(), "SAVED", row.getId(), row.getStatus(), null);
        }

        static Item failed(String companyName, String message) {
            return new Item(companyName, "FAILED", null, null, message);
        }
    }
}
