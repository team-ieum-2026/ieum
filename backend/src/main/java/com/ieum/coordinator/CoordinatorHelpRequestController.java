// 주인: BE-B
package com.ieum.coordinator;

import com.ieum.common.ApiResponse;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import com.ieum.common.ErrorResponse;
import com.ieum.helprequest.HelpRequestNotFoundException;
import com.ieum.helprequest.HelpRequestStatusResult;
import com.ieum.helprequest.HelpRequestService;
import com.ieum.helprequest.HelpRequestStatusUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coordinator/help-requests")
@Tag(name = "코디네이터 도움 요청")
public class CoordinatorHelpRequestController {

    private final HelpRequestService service;

    public CoordinatorHelpRequestController(HelpRequestService service) {
        this.service = service;
    }

    @PatchMapping("/{helpRequestId}")
    @Operation(summary = "도움 요청 처리 상태 변경",
            description = "OPEN · IN_PROGRESS · CONSULTATION_SCHEDULED · CONTACTED 중 하나로 변경합니다. "
                    + "CONSULTATION_SCHEDULED면 consultationAt(상담 일시) 필수, 다른 상태에서는 보내지 않습니다. 종료(CLOSED)된 요청은 409.")
    public ApiResponse<HelpRequestStatusResult> updateStatus(
            @PathVariable("helpRequestId") Long helpRequestId,
            @Valid @RequestBody HelpRequestStatusUpdateRequest request) {
        if (helpRequestId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return ApiResponse.of(service.updateStatus(helpRequestId, request));
    }

    @ExceptionHandler(HelpRequestNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(HelpRequestNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(new ErrorResponse.ErrorBody("NOT_FOUND", exception.getMessage())));
    }
}
