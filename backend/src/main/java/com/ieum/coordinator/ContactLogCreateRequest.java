// 주인: BE-B
package com.ieum.coordinator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

/** 연락 유형과 상담 내용은 문자열만 받는다. 값의 유효성과 길이는 저장 서비스에서 검사한다. */
public record ContactLogCreateRequest(
        @Schema(allowableValues = {"PHONE", "MEETING", "MESSAGE"}, example = "PHONE") String contactType,
        @Schema(description = "상담 내용", example = "모의 상담 기록입니다.") String note) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ContactLogCreateRequest from(JsonNode body) {
        if (body == null || !body.isObject() || body.size() != 2
                || !body.path("contactType").isTextual() || !body.path("note").isTextual()) {
            throw new IllegalArgumentException("연락 유형과 상담 내용은 문자열로 보내 주세요.");
        }
        return new ContactLogCreateRequest(body.get("contactType").textValue(), body.get("note").textValue());
    }
}
