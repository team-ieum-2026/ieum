// 주인: BE-B
package com.ieum.coordinator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** C 서류 검토 · 도움 요청 상태별 목록 조회 결과. DB 시각은 서울 시간으로 응답에서 변환한다. */
public final class CoordinatorListData {
    private CoordinatorListData() {}

    @Getter @Setter
    public static class ApplicationRow {
        private Long applicationId;
        private Long studentId;
        private String studentName;
        private String jobTitle;
        private String status;
        private LocalDateTime submittedAt;
        private LocalDateTime reviewedAt;
        private LocalDateTime deliveredAt;
        private LocalDate deadlineOn;
        private boolean untilFilled;
    }

    @Getter @Setter
    public static class HelpRequestRow {
        private Long helpRequestId;
        private Long studentId;
        private String studentName;
        private String courseLabel;
        private LocalDateTime createdAt;
        private String jobTitle;
        private String contactMethod;
        private String memo;
        private String helpType;
        private String status;
        private LocalDateTime closedAt;
        private String closeReason;
    }
}
