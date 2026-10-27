// 주인: BE-B
package com.ieum.profile;

import com.ieum.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** M8 자기소개서 원본 저장과 역량 프로필 갱신. */
@RestController
@RequestMapping("/api/students/{studentId}/cover-letter")
@Tag(name = "수료생 프로필")
public class ProfileCoverLetterController {

    private final ProfileItemService profileItemService;

    public ProfileCoverLetterController(ProfileItemService profileItemService) {
        this.profileItemService = profileItemService;
    }

    @PutMapping
    @Operation(summary = "자기소개서 원본 수정", description = "원문을 저장하고 역량 프로필을 갱신한 전체 프로필을 반환합니다.")
    public ApiResponse<StudentProfileResponse> updateCoverLetter(
            @Parameter(description = "수료생 번호", example = "1") @PathVariable("studentId") Long studentId,
            @Valid @RequestBody CoverLetterUpdateRequest request) {
        return ApiResponse.of(profileItemService.updateCoverLetter(studentId, request.content()));
    }
}
