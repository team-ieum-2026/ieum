// 주인: BE-B
package com.ieum.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.ieum.common.BusinessException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** M8 항목 추가. 기존 section·item JSON 형식으로 자격증·학력·경력을 지원한다. */
public record ProfileItemRequest(
        @NotNull(message = "추가할 섹션을 입력해 주세요.")
        @Pattern(regexp = "CERTIFICATES|EDUCATION|CAREER|PROJECTS", message = "현재는 자격증·학력·경력·프로젝트 추가를 지원해요.")
        @Schema(example = "CERTIFICATES")
        String section,
        @NotNull(message = "추가할 항목 정보를 입력해 주세요.")
        @Schema(description = "section에 해당하는 자격증·학력·경력·프로젝트 정보")
        JsonNode item) {

    @JsonIgnore
    @AssertTrue(message = "추가할 항목의 입력값을 확인해 주세요.")
    public boolean isValidItem() {
        try {
            if ("CERTIFICATES".equals(section)) certificate();
            else if ("EDUCATION".equals(section)) education();
            else if ("CAREER".equals(section)) career();
            else if ("PROJECTS".equals(section)) ProfileProjectValues.merge(item, null, true);
            else return false;
            return true;
        } catch (BusinessException exception) {
            return false;
        }
    }

    CertificateItem certificate() {
        return ProfileItemValues.certificate(item, null);
    }

    EducationItem education() {
        return ProfileItemValues.education(item, null);
    }

    CareerItem career() {
        return ProfileItemValues.career(item, null);
    }

    public record CareerItem(String companyName, String position, LocalDate startedOn, LocalDate endedOn,
                             String description) {}

    public record EducationItem(String schoolName, String major, String degree, String status, LocalDate graduatedOn) {}

    public record CertificateItem(
            @NotBlank(message = "자격증 이름을 입력해 주세요.")
            @Size(max = 100, message = "자격증 이름은 100자 이내로 입력해 주세요.")
            @Schema(example = "모의 자격증")
            String name,
            @Size(max = 100, message = "발급 기관은 100자 이내로 입력해 주세요.")
            @Schema(example = "모의 발급 기관", nullable = true)
            String issuer,
            @Schema(example = "2026-09-01", nullable = true)
            LocalDate acquiredOn) {

        public CertificateItem {
            if (acquiredOn != null && (acquiredOn.getYear() < 1000 || acquiredOn.getYear() > 9999)) {
                throw new IllegalArgumentException("취득일은 1000년부터 9999년 사이로 입력해 주세요.");
            }
        }
    }
}
