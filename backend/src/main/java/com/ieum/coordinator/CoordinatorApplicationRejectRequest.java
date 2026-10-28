// 주인: BE-B
package com.ieum.coordinator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

/** 반려 메모는 문자열만 받으며, 내용과 길이는 저장 서비스에서 검사한다. */
public record CoordinatorApplicationRejectRequest(
        @Schema(description = "보완할 점을 안내할 반려 메모", example = "모의 보완 사항입니다.") String note) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CoordinatorApplicationRejectRequest from(JsonNode body) {
        if (body == null || !body.isObject() || body.size() != 1 || !body.path("note").isTextual()) {
            throw new IllegalArgumentException("반려 메모는 문자열로 보내 주세요.");
        }
        return new CoordinatorApplicationRejectRequest(body.get("note").textValue());
    }
}
