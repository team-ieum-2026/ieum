// 주인: BE-B
package com.ieum.coordinator;

import com.fasterxml.jackson.databind.JsonNode;

/** C4 응답. 검토 당시 저장된 문서와 그 문서의 검증 요약을 함께 반환한다. */
public record CoordinatorApplicationResponse(
        Long applicationId,
        String status,
        Student student,
        Job job,
        JsonNode document,
        Verification verification) {

    public record Student(Long studentId, String name) {}

    public record Job(Long jobPostingId, String companyName, String title) {}

    public record Verification(int aiSentenceCount, int aiSentenceWithEvidence, int excludedSentenceCount,
                               int userEditedCount, String companyInfoStatus) {}

    public record StatusResult(Long applicationId, String status) {}
}
