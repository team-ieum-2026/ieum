// 주인: BE-A
package com.ieum.application;

import com.ieum.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 지원서 하나 (BE-A-07: M5 진행 상태). */
@Tag(name = "지원서", description = "M5 서류 생성 진행 상태")
@RestController
@RequestMapping("/api/applications/{applicationId}")
public class ApplicationController {

    private final ApplicationProgressService progressService;

    public ApplicationController(ApplicationProgressService progressService) {
        this.progressService = progressService;
    }

    @Operation(summary = "서류 생성 진행 상태",
            description = "FE가 2초마다 조회한다. status가 DRAFT면 M6로, FAILED면 다시 시도 버튼. 없는 지원서면 404 APPLICATION_NOT_FOUND.")
    @GetMapping("/progress")
    public ApiResponse<ApplicationProgressResponse> progress(
            @Parameter(description = "지원서 번호", example = "7") @PathVariable("applicationId") Long applicationId) {
        return ApiResponse.of(progressService.getProgress(applicationId));
    }
}
