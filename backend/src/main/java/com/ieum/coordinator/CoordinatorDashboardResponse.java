// 주인: BE-B
package com.ieum.coordinator;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

/** C1 대시보드 응답. 키와 목록 항목은 docs/API.md 2-3 및 FE Dashboard 타입을 따른다. */
public record CoordinatorDashboardResponse(
        OffsetDateTime asOf,
        long totalStudents,
        List<HelpRequest> helpRequests,
        StatusSummary statusSummary,
        List<ReviewQueueItem> reviewQueue,
        List<CallListItem> callList,
        List<NoteToCheck> notesToCheck) {

    public record HelpRequest(
            Long helpRequestId,
            Long studentId,
            String studentName,
            String courseLabel,
            OffsetDateTime createdAt,
            String jobTitle,
            String contactMethod,
            String memo) {}

    public record StatusSummary(
            @JsonProperty("EMPLOYED") long employed,
            @JsonProperty("SEEKING") Seeking seeking,
            @JsonProperty("NEED_HELP") long needHelp,
            @JsonProperty("CHECK_REQUIRED") long checkRequired) {}

    public record Seeking(
            long total,
            @JsonProperty("APPLYING") long applying,
            @JsonProperty("WAITING") long waiting,
            @JsonProperty("OTHER") long other) {}

    public record ReviewQueueItem(
            Long applicationId,
            String studentName,
            String jobTitle,
            OffsetDateTime submittedAt) {}

    public record CallListItem(
            Long studentId,
            String studentName,
            OffsetDateTime lastResponseAt,
            long daysSinceResponse) {}

    public record NoteToCheck(Long studentId, String studentName, String passNote) {}
}
