// 주인: BE-B
package com.ieum.helprequest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record HelpRequestStatusUpdateRequest(
        @NotBlank(message = "도움 요청 상태를 입력해 주세요.")
        @Pattern(regexp = "OPEN|CONTACTED", message = "도움 요청 상태를 확인해 주세요.")
        @Schema(allowableValues = {"OPEN", "CONTACTED"}, example = "CONTACTED") String status) {}
