// 주인: BE-A
package com.ieum.recommendation;

import java.time.LocalDate;
import java.util.List;

/**
 * M2 추천 공고 상세 응답의 data. 형식은 docs/API.md 2-1 · frontend/public/mock/M2_recommendation_item_detail.json.
 * summary 는 공고 본문 [내용]의 앞 2줄, reasons 의 evidence 는 수료생 프로필에서 label · quote 를 채운 근거.
 */
public record RecommendationItemDetailResponse(
        Long itemId,
        int rank,
        String response,
        Job job,
        List<String> summary,
        List<Reason> reasons
) {

    public record Job(
            Long jobPostingId,
            String companyName,
            String title,
            String jobCategory,
            String location,
            String workAddress,
            String employmentType,
            String career,
            String education,
            String salaryText,
            LocalDate deadlineOn,
            boolean untilFilled,
            Integer dDay,
            LocalDate postedOn,
            String description,
            String homepageUrl,
            String source,
            String sourceLabel
    ) {
    }

    public record Reason(String text, List<Evidence> evidence) {
    }

    /** docs/API.md 3-2 근거 형식 */
    public record Evidence(
            String refType,
            Long refId,
            String label,
            String quote,
            String url,
            String collectedAt
    ) {
    }
}
