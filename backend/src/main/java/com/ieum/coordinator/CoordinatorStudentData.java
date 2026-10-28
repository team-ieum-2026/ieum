// 주인: BE-B
package com.ieum.coordinator;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** C2·C3 조회 및 상담 기록 저장에 사용하는 DB 행. 응답 JSON은 서비스에서 만든다. */
public final class CoordinatorStudentData {

    private CoordinatorStudentData() {}

    @Getter
    @Setter
    public static class Student {
        private Long studentId;
        private String name;
        private String courseLabel;
        private LocalDate completedOn;
        private String status;
        private String subStatus;
        private LocalDateTime lastResponseAt;
        private boolean hasNote;
        private String email;
        private String desiredJob;
        private String preferredRegion;
        private String reason;
        private JsonNode competencyProfile;
    }

    @Getter
    @Setter
    public static class RegisteredSummary {
        private int education;
        private int certificates;
        private int projects;
        private boolean hasCoverLetter;
    }

    @Getter
    @Setter
    public static class Recommendation {
        private Long recommendationId;
        private LocalDateTime createdAt;
    }

    @Getter
    @Setter
    public static class HistoryItem {
        private Long recommendationId;
        private Long itemId;
        private String companyName;
        private String title;
        private String response;
        private String passReason;
        private String passNote;
        private LocalDateTime respondedAt;
        private Long applicationId;
        private String applicationStatus;
    }

    @Getter
    @Setter
    public static class ContactLog {
        private Long contactLogId;
        private Long studentId;
        private String contactType;
        private String note;
        private LocalDateTime createdAt;
    }
}
