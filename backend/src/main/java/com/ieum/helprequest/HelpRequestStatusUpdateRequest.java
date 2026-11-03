// 주인: BE-B
package com.ieum.helprequest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;

/**
 * 코디네이터 처리 상태 변경. CONSULTATION_SCHEDULED 이면 consultationAt(상담 일시) 필수,
 * 다른 상태에서는 consultationAt 을 보내지 않는다 (기존 상담 일시는 이력으로 남는다).
 */
public record HelpRequestStatusUpdateRequest(
        @NotBlank(message = "도움 요청 상태를 입력해 주세요.")
        @Pattern(regexp = "OPEN|IN_PROGRESS|CONSULTATION_SCHEDULED|CONTACTED", message = "도움 요청 상태를 확인해 주세요.")
        @Schema(allowableValues = {"OPEN", "IN_PROGRESS", "CONSULTATION_SCHEDULED", "CONTACTED"}, example = "CONTACTED")
        String status,
        @Schema(description = "상담 예정 일시. CONSULTATION_SCHEDULED 일 때만 필수", example = "2026-11-05T14:00:00+09:00",
                nullable = true)
        OffsetDateTime consultationAt) {

    public HelpRequestStatusUpdateRequest(String status) {
        this(status, null);
    }
}
