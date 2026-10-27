// 주인: BE-A
package com.ieum.recommendation;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * M1 현재 추천 · 새로 추천받기 응답의 data. 형식은 docs/API.md 2-1 · frontend/public/mock/M1_recommendations_current.json.
 * 맞는 공고가 없으면 recommendationId 는 null, items 는 [].
 */
public record RecommendationResponse(
        Long recommendationId,
        OffsetDateTime createdAt,
        String basis,
        List<Item> items
) {

    public record Item(
            Long itemId,
            int rank,
            String response,
            Job job,
            List<String> reasonSummary
    ) {
    }

    public record Job(
            Long jobPostingId,
            String companyName,
            String title,
            String location,
            String employmentType,
            String career,
            LocalDate deadlineOn,
            boolean untilFilled,
            Integer dDay
    ) {
    }
}
