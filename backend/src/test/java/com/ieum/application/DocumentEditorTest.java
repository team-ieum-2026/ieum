package com.ieum.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** ai-mock/document_draft.json(M6 목업과 같은 서류)으로 PATCH 규칙 확인 */
class DocumentEditorTest {

    private final ObjectMapper json = new ObjectMapper();
    private ObjectNode document;
    private DocumentEditor editor;

    @BeforeEach
    void setUp() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/ai-mock/document_draft.json")) {
            document = (ObjectNode) json.readTree(in).path("data").path("document");
        }
        editor = new DocumentEditor(document, 7L);
    }

    private DocumentPatchRequest req(String body) throws Exception {
        return json.readValue(body, DocumentPatchRequest.class);
    }

    private JsonNode section(String name) {
        for (JsonNode s : document.path("sections")) {
            if (name.equals(s.path("section").asText())) {
                return s;
            }
        }
        throw new AssertionError(name);
    }

    private JsonNode item(String section, String itemId) {
        for (JsonNode it : section(section).path("items")) {
            if (itemId.equals(it.path("itemId").asText())) {
                return it;
            }
        }
        return null;
    }

    @Test
    void updateSentenceMarksUserAndClearsEvidence() throws Exception {
        assertNull(editor.apply(req("""
                { "op": "UPDATE_SENTENCE", "sentenceId": "cl-2", "text": " 고친 문장 " }""")));

        JsonNode cl2 = section("COVER_LETTER").path("sentences").get(1);
        assertEquals("고친 문장", cl2.path("text").asText());
        assertEquals("USER", cl2.path("source").asText());
        assertEquals(0, cl2.path("evidence").size());
    }

    @Test
    void projectBulletAndSkillSummaryAreSentencesToo() throws Exception {
        editor.apply(req("""
                { "op": "UPDATE_SENTENCE", "sentenceId": "pj-1", "text": "프로젝트 문장" }"""));
        editor.apply(req("""
                { "op": "UPDATE_SENTENCE", "sentenceId": "sk-1", "text": "기술 요약" }"""));

        assertEquals("프로젝트 문장", item("PROJECTS", "prj-11").path("bullets").get(0).path("text").asText());
        assertEquals("기술 요약", section("SKILLS").path("summary").get(0).path("text").asText());
    }

    @Test
    void addCertificateWithoutSaveGetsUserId() throws Exception {
        assertNull(editor.apply(req("""
                { "op": "ADD_ITEM", "section": "CERTIFICATES",
                  "item": { "name": "ADsP", "issuer": "한국데이터산업진흥원", "acquiredOn": "2026-10-01" },
                  "saveToProfile": false }""")));

        JsonNode added = item("CERTIFICATES", "cert-u1");
        assertEquals("ADsP", added.path("name").asText());
        assertEquals("USER", added.path("source").asText());
    }

    @Test
    void addWithSaveReturnsAddActionAndRelinkChangesId() throws Exception {
        DocumentEditor.ProfileAction action = editor.apply(req("""
                { "op": "ADD_ITEM", "section": "CERTIFICATES", "item": { "name": "ADsP" }, "saveToProfile": true }"""));

        assertEquals(DocumentEditor.Kind.ADD, action.kind());
        assertEquals("cert-u1", action.docItemId());
        editor.relink("CERTIFICATES", "cert-u1", 35L);
        assertTrue(item("CERTIFICATES", "cert-35") != null);
    }

    @Test
    void updateLinkedItemSendsOnlyChangedFieldsToProfile() throws Exception {
        DocumentEditor.ProfileAction action = editor.apply(req("""
                { "op": "UPDATE_ITEM", "section": "EDUCATION", "itemId": "edu-1",
                  "item": { "major": "컴퓨터공학과" }, "saveToProfile": true }"""));

        assertEquals(DocumentEditor.Kind.UPDATE, action.kind());
        assertEquals(1L, action.itemId());
        assertEquals("{\"major\":\"컴퓨터공학과\"}", action.item().toString());
        assertEquals("컴퓨터공학과", item("EDUCATION", "edu-1").path("major").asText());
        assertEquals("USER", item("EDUCATION", "edu-1").path("source").asText());
    }

    @Test
    void projectRoleStaysInDocumentAndBulletsAreRejected() throws Exception {
        DocumentEditor.ProfileAction action = editor.apply(req("""
                { "op": "UPDATE_ITEM", "section": "PROJECTS", "itemId": "prj-11",
                  "item": { "role": "백엔드 · 배포" }, "saveToProfile": true }"""));
        assertEquals("백엔드 · 배포", item("PROJECTS", "prj-11").path("role").asText());
        assertEquals(11L, action.itemId());

        var e = assertThrows(BusinessException.class, () -> editor.apply(req("""
                { "op": "UPDATE_ITEM", "section": "PROJECTS", "itemId": "prj-11", "item": { "bullets": [] } }""")));
        assertEquals(ErrorCode.INVALID_REQUEST, e.getErrorCode());
    }

    @Test
    void basicMilitarySkillsUseStudentIdAndNullItemId() throws Exception {
        var basic = editor.apply(req("""
                { "op": "UPDATE_ITEM", "section": "BASIC", "itemId": null,
                  "item": { "email": "new@example.com" }, "saveToProfile": true }"""));
        assertEquals(7L, basic.itemId());
        assertEquals("new@example.com", section("BASIC").path("fields").path("email").asText());

        editor.apply(req("""
                { "op": "UPDATE_ITEM", "section": "MILITARY", "itemId": null, "item": { "value": "SERVED" } }"""));
        assertEquals("SERVED", section("MILITARY").path("value").asText());
        editor.apply(req("""
                { "op": "UPDATE_ITEM", "section": "MILITARY", "itemId": null, "item": { "value": null } }"""));
        assertTrue(section("MILITARY").path("value").isNull());

        editor.apply(req("""
                { "op": "UPDATE_ITEM", "section": "SKILLS", "itemId": null, "item": { "tags": ["Java", "Redis"] } }"""));
        assertEquals("[\"Java\",\"Redis\"]", section("SKILLS").path("tags").toString());
    }

    @Test
    void portfolioOnlyIncludeCanChange() throws Exception {
        editor.apply(req("""
                { "op": "UPDATE_ITEM", "section": "PORTFOLIO", "itemId": "pf-11", "item": { "include": false } }"""));
        assertFalse(item("PORTFOLIO", "pf-11").path("include").asBoolean());

        assertThrows(BusinessException.class, () -> editor.apply(req("""
                { "op": "UPDATE_ITEM", "section": "PORTFOLIO", "itemId": "pf-11", "item": { "fileName": "x.pdf" } }""")));
    }

    @Test
    void deleteLinkedItemWithSaveReturnsDelete() throws Exception {
        var action = editor.apply(req("""
                { "op": "DELETE_ITEM", "section": "CERTIFICATES", "itemId": "cert-2", "saveToProfile": true }"""));

        assertEquals(DocumentEditor.Kind.DELETE, action.kind());
        assertEquals(2L, action.itemId());
        assertNull(item("CERTIFICATES", "cert-2"));
    }

    @Test
    void invalidRequestsAre400() {
        String[] bodies = {
            "{ \"op\": \"UPDATE_ITEM\", \"section\": \"TRAINING\", \"itemId\": \"trn-1\", \"item\": { \"name\": \"x\" } }",
            "{ \"op\": \"ADD_ITEM\", \"section\": \"BASIC\", \"item\": { \"name\": \"x\" } }",
            "{ \"op\": \"ADD_ITEM\", \"section\": \"CERTIFICATES\", \"item\": { \"issuer\": \"이름 없음\" } }",
            "{ \"op\": \"UPDATE_ITEM\", \"section\": \"CERTIFICATES\", \"itemId\": \"cert-1\", \"item\": { \"acquiredOn\": \"2026/09/01\" } }",
            "{ \"op\": \"UPDATE_ITEM\", \"section\": \"EDUCATION\", \"itemId\": \"edu-1\", \"item\": { \"schoolName\": null } }",
            "{ \"op\": \"UPDATE_ITEM\", \"section\": \"BASIC\", \"itemId\": \"x\", \"item\": { \"email\": \"a@b.c\" } }",
            "{ \"op\": \"UPDATE_SENTENCE\", \"sentenceId\": \"nope\", \"text\": \"x\" }",
            "{ \"op\": \"DELETE_ITEM\", \"section\": \"CERTIFICATES\", \"itemId\": \"cert-99\" }",
            "{ \"op\": \"RENAME\" }"
        };
        for (String body : bodies) {
            var e = assertThrows(BusinessException.class, () -> editor.apply(req(body)), body);
            assertEquals(ErrorCode.INVALID_REQUEST, e.getErrorCode(), body);
        }
    }
}
