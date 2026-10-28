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
    @Operation(summary = "자기소개서 원본 수정", description = "원문 저장 후 전체 프로필을 반환하며 AI 재추출은 커밋 뒤 비동기로 처리합니다. profileUpdatedAt은 마지막 추출 완료 시각으로, 완료 후 프로필을 다시 조회하면 갱신됩니다.")
    public ApiResponse<StudentProfileResponse> updateCoverLetter(
            @Parameter(description = "수료생 번호", example = "1") @PathVariable("studentId") Long studentId,
            @Valid @RequestBody CoverLetterUpdateRequest request) {
        return ApiResponse.of(profileItemService.updateCoverLetter(studentId, request.content()));
    }
}
