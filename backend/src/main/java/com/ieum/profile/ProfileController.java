// 주인: BE-B
package com.ieum.profile;

import com.ieum.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** M8 수료생 프로필 조회. BE-B-03 중 조회만 구현한다. */
@RestController
@RequestMapping("/api/students/{studentId}/profile")
@Tag(name = "수료생 프로필", description = "M8 등록 자료와 저장된 역량 프로필")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    @Operation(summary = "수료생 프로필 조회", description = "수료생 번호에 해당하는 등록 자료와 저장된 역량 프로필을 조회합니다.")
    public ApiResponse<StudentProfileResponse> getProfile(
            @Parameter(description = "수료생 번호. 개발용 시드는 1~50번입니다.", example = "1")
            @PathVariable("studentId") Long studentId) {
        return ApiResponse.of(profileService.getProfile(studentId));
    }
}
