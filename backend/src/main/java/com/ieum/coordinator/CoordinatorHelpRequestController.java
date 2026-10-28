// 주인: BE-B
package com.ieum.coordinator;

import com.ieum.common.ApiResponse;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import com.ieum.common.ErrorResponse;
import com.ieum.helprequest.HelpRequestNotFoundException;
import com.ieum.helprequest.HelpRequestResult;
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
    @Operation(summary = "도움 요청 처리 상태 변경", description = "OPEN 또는 CONTACTED로 변경합니다.")
    public ApiResponse<HelpRequestResult> updateStatus(
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
