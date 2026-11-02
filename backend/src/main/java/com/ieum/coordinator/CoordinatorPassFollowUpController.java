// 주인: BE-B
package com.ieum.coordinator;

import com.ieum.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CoordinatorPassFollowUpController {
    private final CoordinatorPassFollowUpService service;
    public CoordinatorPassFollowUpController(CoordinatorPassFollowUpService service) { this.service = service; }

    @GetMapping("/api/coordinator/pass-follow-ups")
    @Operation(summary = "일반 패스 3회 연속 연락 대상", description = "취업·휴식 의사 선택 또는 상담 기록은 횟수를 초기화합니다. 대표 상태와 별도입니다.")
    public ApiResponse<List<CoordinatorPassFollowUpResponse>> list() { return ApiResponse.of(service.list()); }
}
