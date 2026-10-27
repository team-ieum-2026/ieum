// 주인: BE-A
package com.ieum.application;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** ApplicationMapper 가 읽고 쓰는 DB 행. API 응답은 화면별 Response 로 따로 만든다. */
public final class ApplicationRows {

    private ApplicationRows() {
    }

    /** M7 지원서 한 줄: application + job_posting 요약 + 서류 JSON(없으면 null) */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ListItem {
        private Long applicationId;
        private String companyName;
        private String title;
        private String status;
        private LocalDateTime submittedAt;
        private String document;
    }

    /** M7 "이번 추천의 다른 공고": 가장 최근 추천 묶음에서 지원서가 없는 항목 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class OtherItem {
        private Long itemId;
        private String companyName;
        private String title;
        private String response;
        private String passReason;
    }
}
