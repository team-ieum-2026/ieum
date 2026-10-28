// 주인: BE-B
package com.ieum.coordinator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** C1 조회 결과. DB 시각은 서울 시간으로 응답에서 변환한다. */
public final class CoordinatorDashboardData {
    private CoordinatorDashboardData() {}

    @Getter @Setter
    public static class StudentState {
        private Long studentId;
        private String studentName;
        private String status;
        private String subStatus;
        private LocalDateTime lastResponseAt;
        private LocalDate completedOn;
    }

    @Getter @Setter
    public static class HelpRequest {
        private Long helpRequestId;
        private Long studentId;
        private String studentName;
        private String courseLabel;
        private LocalDateTime createdAt;
        private String jobTitle;
        private String contactMethod;
        private String memo;
    }

    @Getter @Setter
    public static class ReviewQueueItem {
        private Long applicationId;
        private String studentName;
        private String jobTitle;
        private LocalDateTime submittedAt;
    }

    @Getter @Setter
    public static class NoteToCheck {
        private Long studentId;
        private String studentName;
        private String passNote;
    }
}
