// 주인: BE-B
package com.ieum.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** M8 자기소개서 원본 수정. 줄바꿈과 공백을 포함한 입력 원문을 저장한다. */
public record CoverLetterUpdateRequest(
        @NotBlank(message = "자기소개서 내용을 입력해 주세요.")
        @Schema(example = "모의 자기소개서입니다. 프로젝트에서 API와 데이터베이스를 구현했습니다.")
        String content) {}
