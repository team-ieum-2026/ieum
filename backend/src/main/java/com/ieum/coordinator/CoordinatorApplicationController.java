// 주인: BE-B
package com.ieum.coordinator;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieum.common.ApiResponse;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coordinator/applications")
@Tag(name = "코디네이터 서류 검토")
public class CoordinatorApplicationController {

    private final CoordinatorApplicationService service;

    public CoordinatorApplicationController(CoordinatorApplicationService service) {
        this.service = service;
    }

    @GetMapping("/{applicationId}")
    @Operation(summary = "서류 검토 조회", description = "저장된 서류와 문장 근거·직접 수정·기업 정보의 검증 요약을 조회합니다.")
    public ApiResponse<CoordinatorApplicationResponse> detail(@PathVariable("applicationId") Long applicationId) {
        return ApiResponse.of(service.detail(applicationId));
    }

    @PostMapping("/{applicationId}/approve")
    @Operation(summary = "서류 승인", description = "검토 요청된 서류를 승인합니다. 기업 전달은 별도로 기록합니다.")
    public ApiResponse<CoordinatorApplicationResponse.StatusResult> approve(
            @PathVariable("applicationId") Long applicationId, @RequestBody(required = false) JsonNode body) {
        requireEmptyBody(body);
        return ApiResponse.of(service.approve(applicationId));
    }

    @PostMapping("/{applicationId}/reject")
    @Operation(summary = "서류 반려", description = "검토 요청된 서류를 반려하고 보완 메모를 상담 기록으로 남깁니다. 수료생에게는 직접 연락해 주세요.")
    public ApiResponse<CoordinatorApplicationResponse.StatusResult> reject(
            @PathVariable("applicationId") Long applicationId,
            @RequestBody CoordinatorApplicationRejectRequest request) {
        return ApiResponse.of(service.reject(applicationId, request));
    }

    @PostMapping("/{applicationId}/deliver")
    @Operation(summary = "전달 완료 기록", description = "승인된 서류를 전달 완료로 기록합니다. P0에서는 실제 전송하지 않습니다.")
    public ApiResponse<CoordinatorApplicationResponse.StatusResult> deliver(
            @PathVariable("applicationId") Long applicationId, @RequestBody(required = false) JsonNode body) {
        requireEmptyBody(body);
        return ApiResponse.of(service.deliver(applicationId));
    }

    private static void requireEmptyBody(JsonNode body) {
        if (body != null && !body.isNull() && (!body.isObject() || !body.isEmpty())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
