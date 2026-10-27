// 주인: BE-B
package com.ieum.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

/** 프로필 항목의 JSON 입력과 부분 수정 값을 확인한다. */
final class ProfileItemValues {

    private static final Set<String> DEGREES = Set.of("HIGH_SCHOOL", "ASSOCIATE", "BACHELOR", "MASTER", "DOCTOR");
    private static final Set<String> EDUCATION_STATUSES = Set.of("GRADUATED", "EXPECTED", "ENROLLED", "DROPPED");
    private static final Set<String> MILITARY_STATUSES = Set.of("SERVED", "NOT_SERVED", "EXEMPT", "NOT_APPLICABLE");

    private ProfileItemValues() {}

    static JsonNode skills(JsonNode item) {
        requireObject(item);
        JsonNode tags = item.get("tags");
        if (tags == null || !tags.isArray()) throw invalid();
        for (JsonNode tag : tags) {
            if (!tag.isTextual() || tag.textValue().isBlank()) throw invalid();
        }
        return tags.deepCopy();
    }

    static String military(JsonNode item) {
        requireObject(item);
        requireKnownField(item, "value");
        return enumText(item, "value", null, MILITARY_STATUSES, 20);
    }

    static ProfileData.Student basic(JsonNode item, ProfileData.Student stored) {
        validatePatch("BASIC", item);
        var basic = new ProfileData.Student();
        basic.setStudentId(stored.getStudentId());
        basic.setName(text(item, "name", stored.getName(), true, 50));
        basic.setEmail(text(item, "email", stored.getEmail(), false, 100));
        basic.setGithubUrl(text(item, "githubUrl", stored.getGithubUrl(), false, 200));
        basic.setDesiredJob(text(item, "desiredJob", stored.getDesiredJob(), false, 100));
        basic.setPreferredRegion(text(item, "preferredRegion", stored.getPreferredRegion(), false, 100));
        return basic;
    }

    static ProfileItemRequest.CertificateItem certificate(JsonNode item, StudentProfileResponse.Certificate stored) {
        requireObject(item);
        return new ProfileItemRequest.CertificateItem(
                text(item, "name", stored == null ? null : stored.name(), true, 100),
                text(item, "issuer", stored == null ? null : stored.issuer(), false, 100),
                date(item, "acquiredOn", stored == null ? null : stored.acquiredOn()));
    }

    static ProfileItemRequest.EducationItem education(JsonNode item, StudentProfileResponse.Education stored) {
        requireObject(item);
        return new ProfileItemRequest.EducationItem(
                text(item, "schoolName", stored == null ? null : stored.schoolName(), true, 100),
                text(item, "major", stored == null ? null : stored.major(), false, 100),
                enumText(item, "degree", stored == null ? null : stored.degree(), DEGREES, 30),
                enumText(item, "status", stored == null ? null : stored.status(), EDUCATION_STATUSES, 20),
                date(item, "graduatedOn", stored == null ? null : stored.graduatedOn()));
    }

    static ProfileItemRequest.CareerItem career(JsonNode item, StudentProfileResponse.Career stored) {
        requireObject(item);
        String companyName = text(item, "companyName", stored == null ? null : stored.companyName(), true, 100);
        String position = text(item, "position", stored == null ? null : stored.position(), false, 100);
        LocalDate startedOn = date(item, "startedOn", stored == null ? null : stored.startedOn());
        LocalDate endedOn = date(item, "endedOn", stored == null ? null : stored.endedOn());
        requireCareerDates(startedOn, endedOn);
        return new ProfileItemRequest.CareerItem(companyName, position, startedOn, endedOn,
                careerDescription(item, stored == null ? null : stored.description()));
    }

    static void validatePatch(String section, JsonNode item) {
        requireObject(item);
        if ("SKILLS".equals(section)) {
            skills(item);
        } else if ("MILITARY".equals(section)) {
            military(item);
        } else if ("BASIC".equals(section)) {
            requireKnownField(item, "name", "email", "githubUrl", "desiredJob", "preferredRegion");
            if (item.has("name")) text(item, "name", null, true, 50);
            if (item.has("email")) text(item, "email", null, false, 100);
            if (item.has("githubUrl")) text(item, "githubUrl", null, false, 200);
            if (item.has("desiredJob")) text(item, "desiredJob", null, false, 100);
            if (item.has("preferredRegion")) text(item, "preferredRegion", null, false, 100);
        } else if ("CERTIFICATES".equals(section)) {
            requireKnownField(item, "name", "issuer", "acquiredOn");
            if (item.has("name")) text(item, "name", null, true, 100);
            if (item.has("issuer")) text(item, "issuer", null, false, 100);
            if (item.has("acquiredOn")) date(item, "acquiredOn", null);
        } else if ("EDUCATION".equals(section)) {
            requireKnownField(item, "schoolName", "major", "degree", "status", "graduatedOn");
            if (item.has("schoolName")) text(item, "schoolName", null, true, 100);
            if (item.has("major")) text(item, "major", null, false, 100);
            if (item.has("degree")) enumText(item, "degree", null, DEGREES, 30);
            if (item.has("status")) enumText(item, "status", null, EDUCATION_STATUSES, 20);
            if (item.has("graduatedOn")) date(item, "graduatedOn", null);
        } else if ("CAREER".equals(section)) {
            requireKnownField(item, "companyName", "position", "startedOn", "endedOn", "description");
            if (item.has("companyName")) text(item, "companyName", null, true, 100);
            if (item.has("position")) text(item, "position", null, false, 100);
            requireCareerDates(date(item, "startedOn", null), date(item, "endedOn", null));
            if (item.has("description")) careerDescription(item, null);
        } else {
            throw invalid();
        }
    }

    private static String careerDescription(JsonNode item, String fallback) {
        String description = text(item, "description", fallback, false, 65535);
        // MySQL TEXT 한도는 문자 수가 아닌 UTF-8 바이트 수다.
        if (description != null && description.getBytes(StandardCharsets.UTF_8).length > 65535) {
            throw invalid();
        }
        return description;
    }

    private static void requireCareerDates(LocalDate startedOn, LocalDate endedOn) {
        if (startedOn != null && endedOn != null && endedOn.isBefore(startedOn)) {
            throw invalid();
        }
    }

    private static void requireObject(JsonNode item) {
        if (item == null || !item.isObject()) throw invalid();
    }

    private static void requireKnownField(JsonNode item, String... fields) {
        for (String field : fields) {
            if (item.has(field)) return;
        }
        throw invalid();
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

    private static String enumText(JsonNode item, String field, String fallback, Set<String> allowed, int maxLength) {
        String value = text(item, field, fallback, false, maxLength);
        if (value != null && !allowed.contains(value)) throw invalid();
        return value;
    }

    private static LocalDate date(JsonNode item, String field, LocalDate fallback) {
        if (!item.has(field)) return fallback;
        String value = text(item, field, null, false, 10);
        if (value == null) return null;
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid();
        try {
            LocalDate parsed = LocalDate.parse(value);
            if (parsed.getYear() < 1000) throw invalid();
            return parsed;
        } catch (DateTimeParseException exception) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_REQUEST);
    }
}
