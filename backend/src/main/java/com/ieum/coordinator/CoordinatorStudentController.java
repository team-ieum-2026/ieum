// 주인: BE-B
package com.ieum.coordinator;

import com.ieum.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coordinator/students")
@Tag(name = "코디네이터 수료생")
public class CoordinatorStudentController {

    private final CoordinatorStudentService service;

    public CoordinatorStudentController(CoordinatorStudentService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "수료생 목록 조회", description = "상태·구직 세부 상태·이름으로 검색하고 페이지를 조회합니다.")
    public ApiResponse<CoordinatorStudentResponse.ListResult> list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "subStatus", required = false) String subStatus,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.of(service.list(status, subStatus, q, page, size));
    }

    @GetMapping("/{studentId}")
    @Operation(summary = "수료생 상세 조회", description = "기본 정보·역량 프로필·등록 자료 집계·추천 이력·상담 기록을 조회합니다.")
    public ApiResponse<CoordinatorStudentResponse.Detail> detail(@PathVariable("studentId") Long studentId) {
        return ApiResponse.of(service.detail(studentId));
    }

    @PostMapping("/{studentId}/contact-logs")
    @Operation(summary = "통화·상담 기록 추가", description = "상담 기록을 저장합니다. 수료생의 마지막 응답 시각과 상태는 바꾸지 않습니다.")
    public ApiResponse<CoordinatorStudentResponse.ContactLog> addContactLog(
            @PathVariable("studentId") Long studentId, @RequestBody ContactLogCreateRequest request) {
        return ApiResponse.of(service.addContactLog(studentId, request));
    }
}
