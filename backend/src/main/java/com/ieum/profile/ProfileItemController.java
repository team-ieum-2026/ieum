// 주인: BE-B
package com.ieum.profile;

import com.ieum.common.ApiResponse;
import com.ieum.common.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** M8 프로필 항목 변경. 기존 프로필 전체 응답 형식을 사용한다. */
@RestController
@RequestMapping("/api/students/{studentId}/profile/items")
@Tag(name = "수료생 프로필")
public class ProfileItemController {

    private final ProfileItemService profileItemService;

    public ProfileItemController(ProfileItemService profileItemService) {
        this.profileItemService = profileItemService;
    }

    @PostMapping
    @Operation(summary = "프로필 항목 추가", description = "CERTIFICATES·EDUCATION·CAREER·PROJECTS를 지원합니다. 프로젝트의 roleNote·readme 원문 수정은 M8 경로에서만 가능합니다. 항목 저장 후 전체 프로필을 반환하며 AI 재추출은 커밋 뒤 비동기로 처리합니다. profileUpdatedAt은 마지막 추출 완료 시각으로, 완료 후 프로필을 다시 조회하면 갱신됩니다.")
    public ApiResponse<StudentProfileResponse> addItem(
            @Parameter(description = "수료생 번호", example = "1")
            @PathVariable("studentId") Long studentId,
            @Valid @RequestBody ProfileItemRequest request) {
        return ApiResponse.of(profileItemService.addItem(studentId, request));
    }

    @PatchMapping("/{itemId}")
    @Operation(summary = "프로필 항목 수정", description = "BASIC·MILITARY·SKILLS·CERTIFICATES·EDUCATION·CAREER·PROJECTS의 보낸 필드만 수정합니다. BASIC·MILITARY·SKILLS의 itemId는 studentId와 같습니다. 선택 항목의 null은 값을 비웁니다. MILITARY는 value에 SERVED·NOT_SERVED·EXEMPT·NOT_APPLICABLE 또는 null을 보냅니다. SKILLS는 tags 배열로 전체 기술 목록을 교체하며 빈 배열로 비웁니다. PORTFOLIO는 프로젝트 itemId에 fileName·include를 수정합니다. P0에서는 파일명만 저장합니다.")
    public ApiResponse<StudentProfileResponse> updateItem(
            @Parameter(description = "수료생 번호", example = "1") @PathVariable("studentId") Long studentId,
            @Parameter(description = "프로필 항목 번호(BASIC·MILITARY·SKILLS는 studentId와 동일)") @PathVariable("itemId") Long itemId,
            @Valid @RequestBody ProfileItemPatchRequest request) {
        return ApiResponse.of(profileItemService.updateItem(studentId, itemId, request));
    }

    @DeleteMapping("/{itemId}")
    @Operation(summary = "프로필 항목 삭제", description = "해당 수료생의 자격증·학력·경력·프로젝트를 삭제하고 갱신한 전체 프로필을 반환합니다. PORTFOLIO는 첨부 파일명만 제거하고 포함 여부를 false로 바꾸며 프로젝트 원문은 보존합니다.")
    public ApiResponse<StudentProfileResponse> deleteItem(
            @Parameter(description = "수료생 번호", example = "1") @PathVariable("studentId") Long studentId,
            @Parameter(description = "프로필 항목 번호") @PathVariable("itemId") Long itemId,
            @Parameter(description = "삭제할 섹션(CERTIFICATES·EDUCATION·CAREER·PROJECTS·PORTFOLIO)", example = "PROJECTS") @RequestParam("section") String section) {
        return ApiResponse.of(profileItemService.deleteItem(studentId, itemId, section));
    }

    @ExceptionHandler(ProfileItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleItemNotFound(ProfileItemNotFoundException exception) {
        // 공통 파일을 변경하지 않고 기존 NOT_FOUND 오류 형식을 사용한다.
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(new ErrorResponse.ErrorBody("NOT_FOUND", exception.getMessage())));
    }
}
