// 주인: BE-B
package com.ieum.helprequest;

import com.ieum.common.ApiResponse;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import com.ieum.common.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/help-requests")
@Tag(name = "도움 요청")
public class HelpRequestController {

    private final HelpRequestService service;

    public HelpRequestController(HelpRequestService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "도움 요청 생성", description = "추천 공고의 응답을 HELP로 저장하고 도움 요청을 생성합니다.")
    public ApiResponse<HelpRequestResult> create(@Valid @RequestBody HelpRequestCreateRequest request) {
        return ApiResponse.of(service.create(request));
    }

    @PatchMapping("/{helpRequestId}")
    @Operation(summary = "연락 방법·메모 수정", description = "보낸 필드만 수정합니다. 생략한 필드는 유지하고 null은 값을 비웁니다.")
    public ApiResponse<HelpRequestUpdateResult> update(
            @PathVariable("helpRequestId") Long helpRequestId,
            @Valid @RequestBody HelpRequestUpdateRequest request) {
        if (helpRequestId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return ApiResponse.of(service.update(helpRequestId, request));
    }

    @ExceptionHandler(HelpRequestNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(HelpRequestNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(new ErrorResponse.ErrorBody("NOT_FOUND", exception.getMessage())));
    }
}
