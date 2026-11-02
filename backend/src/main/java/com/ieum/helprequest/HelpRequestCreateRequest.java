// 주인: BE-B
package com.ieum.helprequest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Set;

public record HelpRequestCreateRequest(
        @NotNull(message = "수료생 번호를 입력해 주세요.")
        @Positive(message = "수료생 번호는 양수여야 해요.")
        @Schema(example = "1") Long studentId,
        @Positive(message = "추천 공고 번호는 양수여야 해요.")
        @Schema(example = "93", nullable = true) Long recommendationItemId,
        @Schema(nullable = true, allowableValues = {"RESUME_COVER_LETTER", "JOB_APPLICATION",
                "INTERVIEW_PREPARATION", "CAREER_COUNSELING", "OTHER"}) String helpType) {

    private static final Set<String> HELP_TYPES = Set.of("RESUME_COVER_LETTER", "JOB_APPLICATION",
            "INTERVIEW_PREPARATION", "CAREER_COUNSELING", "OTHER");

    /** 기존 추천 공고 요청 호출부와 생성자 호환성을 유지한다. */
    public HelpRequestCreateRequest(Long studentId, Long recommendationItemId) {
        this(studentId, recommendationItemId, null);
    }

    @JsonIgnore
    @AssertTrue(message = "추천 공고 없이 요청할 때는 도움 유형을 선택해 주세요.")
    public boolean isValid() {
        return studentId != null && studentId > 0
                && (recommendationItemId == null ? helpType != null : recommendationItemId > 0)
                && (helpType == null || HELP_TYPES.contains(helpType));
    }
}
