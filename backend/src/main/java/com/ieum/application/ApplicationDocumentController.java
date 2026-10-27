// 주인: BE-A
package com.ieum.application;

import com.ieum.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** M6 · M6-1 · M6-2 서류 (BE-A-08). docs/API.md 2-1 */
@Tag(name = "지원 서류", description = "M6 서류 조회 · 수정 · 검토 요청")
@RestController
@RequestMapping("/api/applications/{applicationId}")
public class ApplicationDocumentController {

    private final ApplicationDocumentService applicationDocumentService;

    public ApplicationDocumentController(ApplicationDocumentService applicationDocumentService) {
        this.applicationDocumentService = applicationDocumentService;
    }

    @Operation(summary = "서류 조회", description = "GENERATING · FAILED면 document는 null. 없는 지원서면 404.")
    @GetMapping
    public ApiResponse<ApplicationDetailResponse> detail(
            @Parameter(description = "지원서 번호", example = "7") @PathVariable("applicationId") Long applicationId) {
        return ApiResponse.of(applicationDocumentService.getDetail(applicationId));
    }

    @Operation(summary = "서류 수정", description = "한 번에 변경 하나. 고친 항목은 source=USER. saveToProfile이면 프로필에도 저장. DRAFT가 아니면 409.")
    @PatchMapping("/document")
    public ApiResponse<ApplicationDetailResponse.DocumentOnly> patch(
            @Parameter(description = "지원서 번호", example = "7") @PathVariable("applicationId") Long applicationId,
            @RequestBody DocumentPatchRequest request) {
        return ApiResponse.of(applicationDocumentService.patch(applicationId, request));
    }

    @Operation(summary = "담당자에게 검토 요청", description = "DRAFT → SUBMITTED. DRAFT가 아니면 409.")
    @PostMapping("/submit")
    public ApiResponse<ApplicationDetailResponse.Submitted> submit(
            @Parameter(description = "지원서 번호", example = "7") @PathVariable("applicationId") Long applicationId) {
        return ApiResponse.of(applicationDocumentService.submit(applicationId));
    }
}
