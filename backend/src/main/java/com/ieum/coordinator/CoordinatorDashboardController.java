// 주인: BE-B
package com.ieum.coordinator;

import com.ieum.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coordinator/dashboard")
@Tag(name = "코디네이터 대시보드")
public class CoordinatorDashboardController {

    private final CoordinatorDashboardService service;

    public CoordinatorDashboardController(CoordinatorDashboardService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "코디네이터 대시보드 조회", description = "도움 요청, 상태별 집계와 검토·전화·비고 확인 목록을 조회합니다.")
    public ApiResponse<CoordinatorDashboardResponse> getDashboard() {
        return ApiResponse.of(service.getDashboard());
    }
}
