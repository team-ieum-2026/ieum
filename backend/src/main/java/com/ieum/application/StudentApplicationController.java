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

/** M7 지원 현황 (BE-A-09). 수료생은 항상 경로의 studentId 로 구분한다 (BE-A-01). */
@Tag(name = "수료생 지원 현황", description = "M7 지원서 목록 + 이번 추천의 다른 공고 응답")
@RestController
@RequestMapping("/api/students/{studentId}/applications")
public class StudentApplicationController {

    private final StudentApplicationService studentApplicationService;

    public StudentApplicationController(StudentApplicationService studentApplicationService) {
        this.studentApplicationService = studentApplicationService;
    }

    @Operation(summary = "지원 현황",
            description = "지원서 전체(최근 생성 순)와 가장 최근 추천에서 지원서가 없는 공고의 응답(rank 순). 없으면 각각 [].")
    @GetMapping
    public ApiResponse<StudentApplicationsResponse> list(
            @Parameter(description = "수료생 번호", example = "21") @PathVariable("studentId") Long studentId) {
        return ApiResponse.of(studentApplicationService.list(studentId));
    }
}
