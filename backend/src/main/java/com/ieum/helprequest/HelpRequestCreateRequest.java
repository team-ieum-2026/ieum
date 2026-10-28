// 주인: BE-B
package com.ieum.helprequest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record HelpRequestCreateRequest(
        @NotNull(message = "수료생 번호를 입력해 주세요.")
        @Positive(message = "수료생 번호는 양수여야 해요.")
        @Schema(example = "1") Long studentId,
        @NotNull(message = "추천 공고 번호를 입력해 주세요.")
        @Positive(message = "추천 공고 번호는 양수여야 해요.")
        @Schema(example = "93") Long recommendationItemId) {}
