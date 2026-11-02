// 주인: BE-B
package com.ieum.helprequest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record HelpRequestCloseRequest(
        @NotNull(message = "수료생 번호를 입력해 주세요.")
        @Positive(message = "수료생 번호는 양수여야 해요.") Long studentId,
        @NotBlank(message = "종료 사유를 선택해 주세요.")
        @Pattern(regexp = "EMPLOYED|NO_LONGER_NEEDED|OTHER", message = "종료 사유를 확인해 주세요.")
        @Schema(allowableValues = {"EMPLOYED", "NO_LONGER_NEEDED", "OTHER"}) String closeReason) {}
