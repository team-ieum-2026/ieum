// 주인: BE-A
package com.ieum.application;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * M7 지원 현황 응답의 data. 형식은 docs/API.md 2-1 · frontend/public/mock/M7_student_applications.json.
 * 지원서가 없거나 추천이 없으면 각 목록은 [].
 */
public record StudentApplicationsResponse(
        List<Application> applications,
        List<OtherItem> otherItems
) {

    /** submittedAt 은 검토 요청 전(GENERATING · DRAFT · FAILED)이면 null */
    public record Application(
            Long applicationId,
            String companyName,
            String title,
            String status,
            OffsetDateTime submittedAt,
            int userEditedCount
    ) {
    }

    /** passReason 은 PASS 가 아니면 null */
    public record OtherItem(
            Long itemId,
            String companyName,
            String title,
            String response,
            String passReason
    ) {
    }
}
