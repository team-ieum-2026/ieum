// 주인: BE-B
package com.ieum.coordinator;

import com.ieum.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** C 서류 검토 · 도움 요청 상태별 목록. 단건 조회 · 처리는 기존 컨트롤러가 맡는다. */
@RestController
@Tag(name = "코디네이터 상태별 목록")
public class CoordinatorListController {

    private final CoordinatorListService service;

    public CoordinatorListController(CoordinatorListService service) {
        this.service = service;
    }

    @GetMapping("/api/coordinator/applications")
    @Operation(summary = "서류 검토 상태별 목록",
            description = "검토 요청 이후 지원서를 제출 순으로 돌려준다. 생략하거나 ALL이면 SUBMITTED · REJECTED · APPROVED · DELIVERED 전체.")
    public ApiResponse<List<CoordinatorListResponse.Application>> applications(
            @Parameter(description = "ALL · SUBMITTED · REJECTED · APPROVED · DELIVERED", example = "SUBMITTED")
            @RequestParam(name = "status", required = false) String status) {
        return ApiResponse.of(service.applications(status));
    }

    @GetMapping("/api/coordinator/help-requests")
    @Operation(summary = "도움 요청 상태별 목록",
            description = "전체 수료생의 도움 요청을 최근 순으로 돌려준다. 생략하거나 ALL이면 OPEN · IN_PROGRESS · CONSULTATION_SCHEDULED · CONTACTED · CLOSED 전체.")
    public ApiResponse<List<CoordinatorListResponse.HelpRequest>> helpRequests(
            @Parameter(description = "ALL · OPEN · IN_PROGRESS · CONSULTATION_SCHEDULED · CONTACTED · CLOSED", example = "CONTACTED")
            @RequestParam(name = "status", required = false) String status) {
        return ApiResponse.of(service.helpRequests(status));
    }
}
