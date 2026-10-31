// 주인: BE-B
package com.ieum.profile;

import com.ieum.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 개발·평가용 관리 API. P0 인증 없음. */
@RestController
@RequestMapping("/api/admin/profiles")
@Tag(name = "admin-profiles", description = "역량 프로필 초기 추출 (INT-05 · INT-07)")
public class ProfileAdminController {

    private final ProfileBatchExtractionService service;

    public ProfileAdminController(ProfileBatchExtractionService service) {
        this.service = service;
    }

    @PostMapping("/extract")
    @Operation(summary = "누락된 역량 프로필 추출",
            description = "역량 프로필이 없는 수료생을 순서대로 추출·저장한다. 기존 프로필은 건너뛰고 개별 실패 후에도 계속한다. 수 분 걸릴 수 있다.")
    public ApiResponse<ProfileBatchExtractionResult> extract() {
        return ApiResponse.of(service.extractMissing());
    }
}
