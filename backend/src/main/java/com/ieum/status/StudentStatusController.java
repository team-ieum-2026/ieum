// 주인: BE-B
package com.ieum.status;

import com.ieum.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/students/{studentId}/status")
@Tag(name = "수료생 현재 상태")
public class StudentStatusController {
    private final StatusService service;

    public StudentStatusController(StatusService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "현재 관리 상태 조회")
    public ApiResponse<StudentStatusResult> get(@PathVariable("studentId") Long studentId) {
        return ApiResponse.of(service.getStatus(studentId));
    }

    @PutMapping
    @Operation(summary = "취업·구직 재개·휴식 의사 선택", description = "선택 이력을 보존하며 기존 도움 요청은 종료하지 않습니다.")
    public ApiResponse<StudentStatusResult> select(@PathVariable("studentId") Long studentId,
                                                  @Valid @RequestBody StudentStatusRequest request) {
        return ApiResponse.of(service.selectStatus(studentId, request));
    }
}
