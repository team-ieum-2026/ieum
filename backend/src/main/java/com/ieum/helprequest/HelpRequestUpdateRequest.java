// 주인: BE-B
package com.ieum.helprequest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** 누락한 항목은 보존하고 명시한 null만 비운다. 숫자·객체를 문자열로 강제 변환하지 않는다. */
public class HelpRequestUpdateRequest {

    private static final Set<String> CONTACT_METHODS = Set.of("SMS", "PHONE", "EMAIL");
    private static final int MEMO_MAX_BYTES = 65_535;

    private boolean contactMethodPresent;
    private boolean memoPresent;
    private boolean validTypes = true;
    private String contactMethod;
    private String memo;

    @JsonSetter("contactMethod")
    public void setContactMethod(JsonNode value) {
        contactMethodPresent = true;
        if (value == null || value.isNull()) contactMethod = null;
        else if (value.isTextual()) contactMethod = value.textValue();
        else validTypes = false;
    }

    @JsonSetter("memo")
    public void setMemo(JsonNode value) {
        memoPresent = true;
        if (value == null || value.isNull()) memo = null;
        else if (value.isTextual()) memo = value.textValue();
        else validTypes = false;
    }

    public boolean hasContactMethod() {
        return contactMethodPresent;
    }

    public boolean hasMemo() {
        return memoPresent;
    }

    @Schema(nullable = true, description = "SMS·PHONE·EMAIL 또는 null. 생략하면 유지하고 null이면 비웁니다.")
    public String getContactMethod() {
        return contactMethod;
    }

    @Schema(nullable = true, description = "도움 요청 메모. 생략하면 유지하고 null이면 비웁니다.")
    public String getMemo() {
        return memo;
    }

    @JsonIgnore
    @AssertTrue(message = "연락 방법과 메모 형식을 확인해 주세요.")
    public boolean isValid() {
        return validTypes && (contactMethodPresent || memoPresent)
                && (contactMethod == null || CONTACT_METHODS.contains(contactMethod))
                && (memo == null || memo.getBytes(StandardCharsets.UTF_8).length <= MEMO_MAX_BYTES);
    }
}
