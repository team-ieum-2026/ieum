// 주인: BE-B
package com.ieum.coordinator;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** C 서류 검토 · 도움 요청 상태별 목록 항목. 키는 docs/API.md 2-3을 따른다. */
public final class CoordinatorListResponse {
    private CoordinatorListResponse() {}

    public record Application(
            Long applicationId,
            Long studentId,
            String studentName,
            String jobTitle,
            String status,
            OffsetDateTime submittedAt,
            OffsetDateTime reviewedAt,
            OffsetDateTime deliveredAt,
            LocalDate deadlineOn,
            boolean untilFilled) {}

    public record HelpRequest(
            Long helpRequestId,
            Long studentId,
            String studentName,
            String courseLabel,
            OffsetDateTime createdAt,
            String jobTitle,
            String contactMethod,
            String memo,
            String helpType,
            String status,
            OffsetDateTime closedAt,
            String closeReason) {}
}
