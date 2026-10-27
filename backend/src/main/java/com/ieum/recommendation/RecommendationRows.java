// 주인: BE-A
package com.ieum.recommendation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** RecommendationMapper 가 읽고 쓰는 DB 행. API 응답은 RecommendationResponse 로 따로 만든다. */
public final class RecommendationRows {

    private RecommendationRows() {
    }

    /**
     * recommendation 한 줄 + 아직 활성인 공고 항목 수 · 전체 항목 수.
     * itemCount 0 이면 현재 추천으로 쓰지 않고 새로 만든다. 단 totalCount 도 0 이면 "맞는 공고 0건"으로 저장된 묶음.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Header {
        private Long id;
        private Long studentId;
        private String triggerType;
        private LocalDateTime createdAt;
        private int itemCount;
        private int totalCount;
    }

    /** recommendation_item 한 줄 + job_posting 요약 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        private Long itemId;
        private int rankNo;
        private String response;
        private String reasons;
        private Long jobPostingId;
        private String companyName;
        private String title;
        private String location;
        private String employmentType;
        private String career;
        private LocalDate deadlineOn;
        private boolean untilFilled;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class NewRecommendation {
        private Long id;
        private Long studentId;
        private String triggerType;

        public NewRecommendation(Long studentId, String triggerType) {
            this.studentId = studentId;
            this.triggerType = triggerType;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class NewItem {
        private Long recommendationId;
        private Long jobPostingId;
        private int rankNo;
        /** JSON 문자열: [{ text, evidence: [{ refType, refId }] }] */
        private String reasons;

        public NewItem(Long recommendationId, Long jobPostingId, int rankNo, String reasons) {
            this.recommendationId = recommendationId;
            this.jobPostingId = jobPostingId;
            this.rankNo = rankNo;
            this.reasons = reasons;
        }
    }
}
