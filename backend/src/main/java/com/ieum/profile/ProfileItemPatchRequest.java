// 주인: BE-B
package com.ieum.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.ieum.common.BusinessException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** M8 프로필 부분 수정. 누락한 필드는 유지하고 명시한 null만 선택 값을 비운다. */
public record ProfileItemPatchRequest(
        @NotNull(message = "수정할 섹션을 입력해 주세요.")
        @Pattern(regexp = "BASIC|MILITARY|SKILLS|CERTIFICATES|EDUCATION|CAREER|PROJECTS|PORTFOLIO", message = "기본정보·병역·기술·자격증·학력·경력·프로젝트 및 포트폴리오 포함 여부 수정을 지원해요.")
        @Schema(example = "CERTIFICATES")
        String section,
        @NotNull(message = "수정할 항목 정보를 입력해 주세요.")
        @Schema(description = "보낸 필드만 수정합니다. 선택 항목의 null은 값을 비웁니다. MILITARY는 value에 병역 값 또는 null을 보냅니다. SKILLS는 tags 배열로 전체 기술 목록을 보내며 빈 배열로 비웁니다. PORTFOLIO는 fileName과 include를 수정하며 P0에서는 파일명만 저장합니다.",
                example = "{\"name\":\"모의 수정 자격증\",\"issuer\":null,\"acquiredOn\":null}")
        JsonNode item) {

    @JsonIgnore
    @AssertTrue(message = "수정할 항목의 입력값을 확인해 주세요.")
    public boolean isValidItem() {
        try {
            if ("PROJECTS".equals(section)) ProfileProjectValues.validatePatch(item, true);
            else if ("PORTFOLIO".equals(section)) ProfileProjectValues.validatePortfolioPatch(item);
            else ProfileItemValues.validatePatch(section, item);
            return true;
        } catch (BusinessException exception) {
            return false;
        }
    }

    ProfileItemRequest.CertificateItem applyTo(StudentProfileResponse.Certificate stored) {
        ProfileItemValues.validatePatch(section, item);
        return ProfileItemValues.certificate(item, stored);
    }

    ProfileItemRequest.EducationItem applyTo(StudentProfileResponse.Education stored) {
        ProfileItemValues.validatePatch(section, item);
        return ProfileItemValues.education(item, stored);
    }

    ProfileItemRequest.CareerItem applyTo(StudentProfileResponse.Career stored) {
        ProfileItemValues.validatePatch(section, item);
        return ProfileItemValues.career(item, stored);
    }
}
