// 주인: BE-A
package com.ieum.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 서류 JSON(docs/API.md 3-3) 한 건에 PATCH 하나를 적용한다 (BE-A-08). DB · 프로필은 모르고 JSON만 바꾼다.
 * - 고친 문장 · 항목 · 섹션은 source = USER. 고친 문장은 원래 AI 근거와 맞지 않을 수 있어 evidence 를 비운다
 * - 서류 항목 id 는 "접두어-프로필 번호"(edu-1, prj-11). 프로필에 없는 서류 전용 항목은 "접두어-u번호"(cert-u1)
 * - saveToProfile 이면 프로필에 무엇을 해야 하는지 ProfileAction 으로 돌려준다. 실제 호출은 서비스가 한다
 */
final class DocumentEditor {

    enum Kind { ADD, UPDATE, DELETE }

    /** 프로필에 할 일. itemId 는 프로필 숫자 id (BASIC · MILITARY · SKILLS 는 studentId), ADD 면 null */
    record ProfileAction(Kind kind, String section, Long itemId, ObjectNode item, String docItemId) {
    }

    private static final JsonNodeFactory F = JsonNodeFactory.instance;
    private static final int TEXT_MAX = 300;
    private static final int LONG_TEXT_MAX = 2000;
    private static final int SENTENCE_MAX = 1000;
    private static final int TAGS_MAX = 50;

    private static final Map<String, String> PREFIX = Map.of(
            "EDUCATION", "edu-", "CAREER", "car-", "CERTIFICATES", "cert-", "PROJECTS", "prj-", "PORTFOLIO", "pf-");
    private static final Set<String> LIST_SECTIONS = Set.of("EDUCATION", "CAREER", "CERTIFICATES", "PROJECTS");
    private static final Map<String, String> REQUIRED = Map.of(
            "EDUCATION", "schoolName", "CAREER", "companyName", "CERTIFICATES", "name", "PROJECTS", "title");
    private static final Map<String, Set<String>> FIELDS = Map.of(
            "EDUCATION", Set.of("schoolName", "major", "degree", "status", "graduatedOn"),
            "CAREER", Set.of("companyName", "position", "startedOn", "endedOn", "description"),
            "CERTIFICATES", Set.of("name", "issuer", "acquiredOn"),
            "PROJECTS", Set.of("title", "periodText", "teamSize", "role"),
            "BASIC", Set.of("name", "email", "githubUrl", "desiredJob"));
    /** 프로젝트를 프로필에 새로 만들 때 넘길 필드. role 은 서류 전용 */
    private static final List<String> PROJECT_PROFILE_FIELDS = List.of("title", "periodText", "teamSize");
    private static final Set<String> DATE_FIELDS = Set.of("graduatedOn", "startedOn", "endedOn", "acquiredOn");
    private static final Map<String, Set<String>> ENUMS = Map.of(
            "degree", Set.of("HIGH_SCHOOL", "ASSOCIATE", "BACHELOR", "MASTER", "DOCTOR"),
            "status", Set.of("GRADUATED", "EXPECTED", "ENROLLED", "DROPPED"),
            "value", Set.of("SERVED", "NOT_SERVED", "EXEMPT", "NOT_APPLICABLE"));

    private final ObjectNode document;
    private final Long studentId;

    DocumentEditor(ObjectNode document, Long studentId) {
        this.document = document;
        this.studentId = studentId;
    }

    /** 변경을 적용하고, saveToProfile 이면 프로필에 할 일을 돌려준다 (없으면 null) */
    ProfileAction apply(DocumentPatchRequest req) {
        String op = req.op() == null ? "" : req.op();
        return switch (op) {
            case "UPDATE_SENTENCE" -> {
                updateSentence(req.sentenceId(), req.text());
                yield null;
            }
            case "ADD_ITEM" -> addItem(req.section(), req.item(), req.save());
            case "UPDATE_ITEM" -> updateItem(req.section(), req.itemId(), req.item(), req.save());
            case "DELETE_ITEM" -> deleteItem(req.section(), req.itemId(), req.save());
            default -> throw invalid("op는 UPDATE_SENTENCE · ADD_ITEM · UPDATE_ITEM · DELETE_ITEM 중 하나여야 해요.");
        };
    }

    /** 프로필에 새로 만든 항목의 id 로 서류 항목 id 를 바꾼다 (cert-u1 → cert-35) */
    void relink(String section, String docItemId, Long profileItemId) {
        ObjectNode item = findItem(section, docItemId);
        item.put("itemId", PREFIX.get(section) + profileItemId);
    }

    // ---------- 문장 ----------

    private void updateSentence(String sentenceId, String text) {
        if (sentenceId == null || sentenceId.isBlank()) {
            throw invalid("sentenceId가 필요해요.");
        }
        if (text == null || text.isBlank()) {
            throw invalid("문장을 비울 수 없어요.");
        }
        if (text.length() > SENTENCE_MAX) {
            throw invalid("문장은 1,000자까지 쓸 수 있어요.");
        }
        ObjectNode sentence = findSentence(sentenceId);
        sentence.put("text", text.strip());
        sentence.put("source", "USER");
        sentence.set("evidence", F.arrayNode());
    }

    private ObjectNode findSentence(String sentenceId) {
        for (JsonNode section : sections()) {
            for (JsonNode s : sentencesOf(section)) {
                if (sentenceId.equals(s.path("sentenceId").asText(null))) {
                    return (ObjectNode) s;
                }
            }
        }
        throw invalid("서류에 없는 문장이에요: " + sentenceId);
    }

    /** SKILLS.summary · PROJECTS.items[].bullets · COVER_LETTER.sentences */
    private static List<JsonNode> sentencesOf(JsonNode section) {
        List<JsonNode> list = new ArrayList<>();
        switch (section.path("section").asText("")) {
            case "SKILLS" -> section.path("summary").forEach(list::add);
            case "COVER_LETTER" -> section.path("sentences").forEach(list::add);
            case "PROJECTS" -> section.path("items").forEach(p -> p.path("bullets").forEach(list::add));
            default -> { }
        }
        return list;
    }

    // ---------- 항목 추가 ----------

    private ProfileAction addItem(String section, JsonNode item, boolean save) {
        if (!LIST_SECTIONS.contains(section)) {
            throw invalid("항목을 추가할 수 없는 섹션이에요: " + section);
        }
        ObjectNode fields = cleanFields(section, item, true);
        ObjectNode added = F.objectNode();
        String docItemId = PREFIX.get(section) + "u" + nextUserNumber(section);
        added.put("itemId", docItemId);
        if ("PROJECTS".equals(section)) {
            for (String f : List.of("title", "periodText", "teamSize", "role")) {
                added.set(f, fields.has(f) ? fields.get(f) : F.nullNode());
            }
            added.set("bullets", F.arrayNode());
        } else {
            added.put("source", "USER");
            for (String f : FIELDS.get(section)) {
                added.set(f, fields.has(f) ? fields.get(f) : F.nullNode());
            }
        }
        items(section).add(added);
        return save ? new ProfileAction(Kind.ADD, section, null, profileFields(section, fields), docItemId) : null;
    }

    private int nextUserNumber(String section) {
        int max = 0;
        String prefix = PREFIX.get(section) + "u";
        for (JsonNode it : items(section)) {
            String id = it.path("itemId").asText("");
            if (id.startsWith(prefix)) {
                try {
                    max = Math.max(max, Integer.parseInt(id.substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                    // 규칙에 맞지 않는 id 는 건너뜀
                }
            }
        }
        return max + 1;
    }

    // ---------- 항목 수정 ----------

    private ProfileAction updateItem(String section, String itemId, JsonNode item, boolean save) {
        String s = section == null ? "" : section;
        return switch (s) {
            case "BASIC" -> updateBasic(itemId, item, save);
            case "MILITARY" -> updateMilitary(itemId, item, save);
            case "SKILLS" -> updateSkills(itemId, item, save);
            case "PORTFOLIO" -> updatePortfolio(itemId, item, save);
            case "EDUCATION", "CAREER", "CERTIFICATES", "PROJECTS" -> updateListItem(s, itemId, item, save);
            default -> throw invalid("수정할 수 없는 섹션이에요: " + section);
        };
    }

    private ProfileAction updateBasic(String itemId, JsonNode item, boolean save) {
        requireNoItemId(itemId, "BASIC");
        ObjectNode fields = cleanFields("BASIC", item, false);
        ObjectNode section = section("BASIC");
        ObjectNode target = section.has("fields") && section.get("fields").isObject()
                ? (ObjectNode) section.get("fields") : section.putObject("fields");
        target.setAll(fields);
        section.put("source", "USER");
        return save ? new ProfileAction(Kind.UPDATE, "BASIC", studentId, fields, null) : null;
    }

    private ProfileAction updateMilitary(String itemId, JsonNode item, boolean save) {
        requireNoItemId(itemId, "MILITARY");
        requireOnly(item, Set.of("value"));
        if (!item.has("value")) {
            throw invalid("병역은 value를 보내 주세요.");
        }
        JsonNode value = item.get("value");
        if (!value.isNull() && !(value.isTextual() && ENUMS.get("value").contains(value.asText()))) {
            throw invalid("병역 값이 올바르지 않아요.");
        }
        section("MILITARY").set("value", value);
        ObjectNode profileItem = F.objectNode().set("value", value);
        return save ? new ProfileAction(Kind.UPDATE, "MILITARY", studentId, profileItem, null) : null;
    }

    private ProfileAction updateSkills(String itemId, JsonNode item, boolean save) {
        requireNoItemId(itemId, "SKILLS");
        requireOnly(item, Set.of("tags"));
        JsonNode tags = item.get("tags");
        if (tags == null || !tags.isArray() || tags.size() > TAGS_MAX) {
            throw invalid("기술 태그는 50개까지의 목록으로 보내 주세요.");
        }
        ArrayNode clean = F.arrayNode();
        for (JsonNode t : tags) {
            if (!t.isTextual() || t.asText().isBlank() || t.asText().length() > 50) {
                throw invalid("기술 태그가 올바르지 않아요.");
            }
            clean.add(t.asText().strip());
        }
        ObjectNode section = section("SKILLS");
        section.set("tags", clean);
        section.put("source", "USER");
        ObjectNode profileItem = F.objectNode().set("tags", clean);
        return save ? new ProfileAction(Kind.UPDATE, "SKILLS", studentId, profileItem, null) : null;
    }

    private ProfileAction updatePortfolio(String itemId, JsonNode item, boolean save) {
        requireOnly(item, Set.of("include"));
        JsonNode include = item.get("include");
        if (include == null || !include.isBoolean()) {
            throw invalid("포트폴리오는 include(true · false)만 바꿀 수 있어요.");
        }
        ObjectNode target = findItem("PORTFOLIO", itemId);
        target.put("include", include.asBoolean());
        Long profileId = profileId("PORTFOLIO", itemId);
        if (!save || profileId == null) {
            return null;
        }
        return new ProfileAction(Kind.UPDATE, "PORTFOLIO", profileId, F.objectNode().put("include", include.asBoolean()),
                itemId);
    }

    private ProfileAction updateListItem(String section, String itemId, JsonNode item, boolean save) {
        ObjectNode fields = cleanFields(section, item, false);
        ObjectNode target = findItem(section, itemId);
        target.setAll(fields);
        if (!"PROJECTS".equals(section)) {
            target.put("source", "USER");
        }
        if (!save) {
            return null;
        }
        Long profileId = profileId(section, itemId);
        if (profileId != null) {
            return new ProfileAction(Kind.UPDATE, section, profileId, fields, itemId);
        }
        // 서류에서만 만든 항목을 이번에 프로필에도 저장: 지금 값 전체로 새로 만든다
        ObjectNode whole = F.objectNode();
        for (String f : FIELDS.get(section)) {
            if (target.has(f)) {
                whole.set(f, target.get(f));
            }
        }
        return new ProfileAction(Kind.ADD, section, null, profileFields(section, whole), itemId);
    }

    // ---------- 항목 삭제 ----------

    private ProfileAction deleteItem(String section, String itemId, boolean save) {
        if (!LIST_SECTIONS.contains(section) && !"PORTFOLIO".equals(section)) {
            throw invalid("항목을 삭제할 수 없는 섹션이에요: " + section);
        }
        ArrayNode items = items(section);
        for (Iterator<JsonNode> it = items.elements(); it.hasNext(); ) {
            if (itemIdOf(it.next()).equals(itemId)) {
                it.remove();
                Long profileId = profileId(section, itemId);
                return save && profileId != null ? new ProfileAction(Kind.DELETE, section, profileId, null, itemId) : null;
            }
        }
        throw invalid("서류에 없는 항목이에요: " + itemId);
    }

    // ---------- 공통 ----------

    /** 허용 필드만, 값 형식 확인. requireAll 이면 필수 필드(학교명 등)가 있어야 한다 */
    private ObjectNode cleanFields(String section, JsonNode item, boolean requireAll) {
        if (item == null || !item.isObject() || item.isEmpty()) {
            throw invalid("item에 바꿀 값을 넣어 주세요.");
        }
        Set<String> allowed = FIELDS.get(section);
        requireOnly(item, allowed);
        ObjectNode out = F.objectNode();
        item.properties().forEach(e -> out.set(e.getKey(), checkValue(e.getKey(), e.getValue())));
        String required = "BASIC".equals(section) ? "name" : REQUIRED.get(section);
        boolean present = out.has(required) && !out.get(required).isNull();
        if ((requireAll && !present) || (out.has(required) && !present)) {
            throw invalid(required + "는 비울 수 없어요.");
        }
        return out;
    }

    private static JsonNode checkValue(String field, JsonNode value) {
        if (value.isNull()) {
            return value;
        }
        if ("teamSize".equals(field)) {
            if (!value.canConvertToInt() || !value.isIntegralNumber() || value.asInt() < 1 || value.asInt() > 100) {
                throw invalid("teamSize는 1~100 사이 숫자여야 해요.");
            }
            return value;
        }
        if (!value.isTextual()) {
            throw invalid(field + "는 글자로 보내 주세요.");
        }
        String text = value.asText().strip();
        if (text.isEmpty()) {
            return F.nullNode();
        }
        if (text.length() > ("description".equals(field) ? LONG_TEXT_MAX : TEXT_MAX)) {
            throw invalid(field + "가 너무 길어요.");
        }
        if (DATE_FIELDS.contains(field)) {
            try {
                LocalDate.parse(text);
            } catch (DateTimeParseException e) {
                throw invalid(field + "는 YYYY-MM-DD 형식이어야 해요.");
            }
        }
        if (ENUMS.containsKey(field) && !ENUMS.get(field).contains(text)) {
            throw invalid(field + " 값이 올바르지 않아요.");
        }
        return F.textNode(text);
    }

    private static ObjectNode profileFields(String section, ObjectNode fields) {
        if (!"PROJECTS".equals(section)) {
            return fields.deepCopy();
        }
        ObjectNode out = F.objectNode();
        PROJECT_PROFILE_FIELDS.forEach(f -> {
            if (fields.has(f)) {
                out.set(f, fields.get(f));
            }
        });
        return out;
    }

    private static void requireOnly(JsonNode item, Set<String> allowed) {
        if (item == null || !item.isObject()) {
            throw invalid("item이 필요해요.");
        }
        item.fieldNames().forEachRemaining(f -> {
            if (!allowed.contains(f)) {
                throw invalid("바꿀 수 없는 필드예요: " + f);
            }
        });
    }

    private static void requireNoItemId(String itemId, String section) {
        if (itemId != null) {
            throw invalid(section + "는 itemId 없이(null) 보내 주세요.");
        }
    }

    /** "edu-12" → 12. 서류 전용 항목(edu-u1)이면 null */
    private static Long profileId(String section, String docItemId) {
        String prefix = PREFIX.get(section);
        if (docItemId == null || !docItemId.startsWith(prefix)) {
            return null;
        }
        try {
            return Long.parseLong(docItemId.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ObjectNode findItem(String section, String itemId) {
        if (itemId == null) {
            throw invalid("itemId가 필요해요.");
        }
        for (JsonNode it : items(section)) {
            if (itemIdOf(it).equals(itemId)) {
                return (ObjectNode) it;
            }
        }
        throw invalid("서류에 없는 항목이에요: " + itemId);
    }

    private static String itemIdOf(JsonNode item) {
        return item.path("itemId").asText("");
    }

    private ArrayNode items(String section) {
        ObjectNode s = section(section);
        if (!s.has("items") || !s.get("items").isArray()) {
            s.putArray("items");
        }
        return (ArrayNode) s.get("items");
    }

    private ObjectNode section(String name) {
        for (JsonNode s : sections()) {
            if (name.equals(s.path("section").asText(null))) {
                return (ObjectNode) s;
            }
        }
        throw invalid("서류에 없는 섹션이에요: " + name);
    }

    private JsonNode sections() {
        JsonNode sections = document.path("sections");
        if (!sections.isArray()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "서류 형식이 올바르지 않아요.");
        }
        return sections;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_REQUEST, message);
    }
}
