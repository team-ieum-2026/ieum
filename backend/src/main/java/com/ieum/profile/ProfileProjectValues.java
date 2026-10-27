// 주인: BE-B
package com.ieum.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.nio.charset.StandardCharsets;

/** M8의 원문 수정과 서류의 메타데이터 저장을 구분한다. */
final class ProfileProjectValues {

    private ProfileProjectValues() {}

    static ProfileData.Project merge(JsonNode item, ProfileData.Project stored, boolean allowOriginals) {
        requireObject(item);
        var result = new ProfileData.Project();
        result.setItemId(stored == null ? null : stored.getItemId());
        result.setTitle(text(item, "title", stored == null ? null : stored.getTitle(), true, 150));
        result.setPeriodText(text(item, "periodText", stored == null ? null : stored.getPeriodText(), false, 50));
        result.setTeamSize(teamSize(item, stored == null ? null : stored.getTeamSize()));
        result.setTechStack(techStack(item, stored == null ? null : stored.getTechStack()));
        result.setIncludeInApply(stored == null || stored.isIncludeInApply());
        result.setPortfolioFileName(stored == null ? null : stored.getPortfolioFileName());
        String roleNote = stored == null ? null : stored.getRoleNote();
        String readme = stored == null ? null : stored.getReadme();
        result.setRoleNote(allowOriginals ? original(item, "roleNote", roleNote, 65535) : roleNote);
        result.setReadme(allowOriginals ? original(item, "readme", readme, 16777215) : readme);
        result.setSource("USER");
        return result;
    }

    static void validatePatch(JsonNode item, boolean allowOriginals) {
        requireObject(item);
        boolean known = hasMetadata(item) || (allowOriginals
                ? item.has("roleNote") || item.has("readme")
                : item.has("role") || item.has("bullets") || item.has("roleNote") || item.has("readme"));
        if (!known) throw invalid();
        // 누락된 제목은 저장된 값과 병합할 때 확인한다.
        var placeholder = new ProfileData.Project();
        placeholder.setTitle("검증용 프로젝트");
        merge(item, placeholder, allowOriginals);
    }

    static boolean hasMetadata(JsonNode item) {
        return item.has("title") || item.has("periodText") || item.has("teamSize") || item.has("techStack");
    }

    static boolean include(JsonNode item) {
        requireObject(item);
        JsonNode include = item.get("include");
        if (include == null || !include.isBoolean()) throw invalid();
        return include.booleanValue();
    }

    static void validatePortfolioPatch(JsonNode item) {
        requireObject(item);
        if (!item.has("fileName") && !item.has("include")) throw invalid();
        if (item.has("fileName")) portfolioFileName(item, null);
        if (item.has("include")) include(item);
    }

    static String portfolioFileName(JsonNode item, String fallback) {
        requireObject(item);
        String fileName = text(item, "fileName", fallback, false, 300);
        if (fileName != null && fileName.isBlank()) throw invalid();
        return fileName;
    }

    private static Integer teamSize(JsonNode item, Integer fallback) {
        JsonNode value = item.get("teamSize");
        if (value == null) return fallback;
        if (value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 1) throw invalid();
        return value.intValue();
    }

    private static JsonNode techStack(JsonNode item, JsonNode fallback) {
        JsonNode value = item.get("techStack");
        if (value == null) return fallback;
        if (value.isNull()) return null;
        if (!value.isArray()) throw invalid();
        for (JsonNode tech : value) {
            if (!tech.isTextual() || tech.textValue().isBlank()) throw invalid();
        }
        return value.deepCopy();
    }

    private static String original(JsonNode item, String field, String fallback, int maxBytes) {
        String value = text(item, field, fallback, false, maxBytes);
        if (value != null && value.getBytes(StandardCharsets.UTF_8).length > maxBytes) throw invalid();
        return value;
    }

    private static String text(JsonNode item, String field, String fallback, boolean required, int maxLength) {
        JsonNode value = item.get(field);
        String result = fallback;
        if (value != null) {
            if (value.isNull()) result = null;
            else if (value.isTextual()) result = value.textValue();
            else throw invalid();
        }
        if ((required && (result == null || result.isBlank())) || (result != null && result.length() > maxLength)) {
            throw invalid();
        }
        return result;
    }

    private static void requireObject(JsonNode item) {
        if (item == null || !item.isObject()) throw invalid();
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_REQUEST);
    }
}
