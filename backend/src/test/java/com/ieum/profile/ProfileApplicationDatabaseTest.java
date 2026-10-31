// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * M6 문서 PATCH → 실제 프로필 저장 → M8 GET을 HTTP와 MySQL로 연결해 검증한다.
 * 새 ID의 모의 자료만 생성하고 소유 관계 확인 후 정리하며, 기존 17개 테이블 지문을 비교한다.
 * AI 연동은 별도 검증 대상이므로 재추출 worker를 대체한다. 기존 원문과 환경 값은 출력하지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "IEUM_PROFILE_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]",
        "spring.sql.init.mode=never", "app.ai.mock=true", "debug=false",
        "app.seoul.sync-cron=-", "app.status.recalculate-cron=-"
})
class ProfileApplicationDatabaseTest {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    private static final List<Table> TABLES = List.of(
            new Table("course", "id"), new Table("student", "id"),
            new Table("student_education", "id"), new Table("student_career", "id"),
            new Table("student_certificate", "id"), new Table("student_project", "id"),
            new Table("student_cover_letter", "student_id"), new Table("competency_profile", "student_id"),
            new Table("job_posting", "id"), new Table("company_info", "id"),
            new Table("recommendation", "id"), new Table("recommendation_item", "id"),
            new Table("help_request", "id"), new Table("application", "id"),
            new Table("application_document", "application_id"), new Table("student_status", "student_id"),
            new Table("contact_log", "id"));

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private PlatformTransactionManager transactions;
    @MockitoBean(enforceOverride = true) private ProfileExtractionWorker worker;

    private Map<String, String> before;
    private Long courseId;
    private Long jobId;
    private Long studentId;
    private Long recommendationId;
    private Long itemId;
    private Long applicationId;
    private Long certificateId;
    private Long projectId;
    private String marker;
    private int expectedRefreshes;

    @BeforeEach
    void createOnlyOwnMockDraftAndRegisteredProfile() throws Exception {
        courseId = jdbc.queryForObject("SELECT MIN(id) FROM course", Long.class);
        jobId = jdbc.queryForObject("SELECT MIN(id) FROM job_posting", Long.class);
        assertNotNull(courseId, "검증 자료가 참조할 기존 과정 한 건이 필요하다");
        assertNotNull(jobId, "검증 자료가 참조할 기존 공고 한 건이 필요하다");
        List<String> engines = jdbc.queryForList("""
                SELECT ENGINE FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN (
                    'student', 'student_certificate', 'student_project', 'recommendation',
                    'recommendation_item', 'application', 'application_document')
                """, String.class);
        assertEquals(7, engines.size());
        assertTrue(engines.stream().allMatch("InnoDB"::equalsIgnoreCase), "원자적 롤백이 가능한 테이블만 사용한다");
        before = fingerprints();
        marker = "모의 프로필 연동 " + UUID.randomUUID();
        studentId = insert("""
                INSERT INTO student (course_id, name, completed_on, skills)
                VALUES (?, ?, '2026-08-31', '["Java"]')
                """, courseId, marker);
        recommendationId = insert("INSERT INTO recommendation (student_id, trigger_type) VALUES (?, 'VISIT')",
                studentId);
        itemId = insert("""
                INSERT INTO recommendation_item (recommendation_id, job_posting_id, rank_no, reasons, response)
                VALUES (?, ?, 1, '[]', 'APPLY')
                """, recommendationId, jobId);
        applicationId = insert("""
                INSERT INTO application (student_id, recommendation_item_id, job_posting_id, status)
                VALUES (?, ?, ?, 'DRAFT')
                """, studentId, itemId, jobId);
        certificateId = insert("""
                INSERT INTO student_certificate (student_id, name, issuer, acquired_on, source)
                VALUES (?, '모의 등록 자격증', '모의 기관', '2026-09-01', 'REGISTERED')
                """, studentId);
        projectId = insert("""
                INSERT INTO student_project
                    (student_id, title, period_text, team_size, role_note, readme,
                     tech_stack, portfolio_file_url, include_in_apply, source)
                VALUES (?, '모의 등록 프로젝트', '2026.07-08', 3, '모의 원본 역할', '모의 README 원문',
                        '["Java"]', 'mock-portfolio.pdf', 1, 'REGISTERED')
                """, studentId);
        JsonNode document = json.readTree("""
                {"companyInfoStatus":"NOT_FOUND","excludedSentenceCount":0,"sections":[
                  {"section":"CERTIFICATES","items":[
                    {"itemId":"cert-%d","name":"모의 등록 자격증","issuer":"모의 기관",
                     "acquiredOn":"2026-09-01","source":"REGISTERED"}]},
                  {"section":"PROJECTS","items":[
                    {"itemId":"prj-%d","title":"모의 등록 프로젝트","periodText":"2026.07-08",
                     "teamSize":3,"role":"모의 서류 역할","bullets":[
                       {"sentenceId":"pj-1","text":"모의 근거 문장","source":"AI",
                        "evidence":[{"refType":"PROJECT","refId":%d,"quote":"모의 README 원문"}]}]}]},
                  {"section":"PORTFOLIO","items":[
                    {"itemId":"pf-%d","fileName":"mock-portfolio.pdf","include":true}]}
                ]}
                """.formatted(certificateId, projectId, projectId, projectId));
        assertEquals(1, jdbc.update("INSERT INTO application_document (application_id, document) VALUES (?, ?)",
                applicationId, json.writeValueAsString(document)));
    }

    @AfterEach
    void removeOnlyOwnedMockRowsAndPreserveAllExistingRowsAndTimes() throws Exception {
        try {
            if (studentId != null) {
                new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    requireOwnedRows();
                    if (applicationId != null) {
                        jdbc.update("DELETE FROM application_document WHERE application_id = ?", applicationId);
                        assertEquals(1, jdbc.update("DELETE FROM application WHERE id = ? AND student_id = ?",
                                applicationId, studentId));
                    }
                    if (itemId != null) assertEquals(1, jdbc.update(
                            "DELETE FROM recommendation_item WHERE id = ? AND recommendation_id = ?",
                            itemId, recommendationId));
                    if (recommendationId != null) assertEquals(1, jdbc.update(
                            "DELETE FROM recommendation WHERE id = ? AND student_id = ?", recommendationId, studentId));
                    jdbc.update("DELETE FROM student_certificate WHERE student_id = ?", studentId);
                    jdbc.update("DELETE FROM student_project WHERE student_id = ?", studentId);
                    assertEquals(1, jdbc.update("DELETE FROM student WHERE id = ? AND course_id = ? AND name = ?",
                            studentId, courseId, marker));
                });
            }
        } finally {
            if (before != null) assertTrue(before.equals(fingerprints()),
                    "HTTP 검증 종료 뒤 기존 17개 테이블의 행과 저장 시각을 보존해야 한다");
        }
    }

    @Test
    void saveToProfileTrueAddsUpdatesAndDeletesTheSameCertificateThroughHttp() throws Exception {
        JsonNode added = patch("ADD_ITEM", "CERTIFICATES", null,
                json.createObjectNode().put("name", "모의 새 자격증").put("issuer", "모의 신규 기관")
                        .put("acquiredOn", "2026-10-01"), true);
        JsonNode documentItem = findByName(items(added, "CERTIFICATES"), "모의 새 자격증");
        String documentId = documentItem.path("itemId").asText();
        long profileId = numericId(documentId, "cert-");
        JsonNode stored = findById(profile().path("certificates"), profileId);
        assertEquals("USER", documentItem.path("source").asText());
        assertEquals("USER", stored.path("source").asText());
        assertEquals("모의 신규 기관", stored.path("issuer").asText());
        assertEquals("2026-10-01", stored.path("acquiredOn").asText());

        JsonNode updated = patch("UPDATE_ITEM", "CERTIFICATES", documentId,
                json.createObjectNode().put("name", "모의 수정 자격증").putNull("issuer"), true);
        assertEquals(documentId, findByName(items(updated, "CERTIFICATES"), "모의 수정 자격증")
                .path("itemId").asText());
        stored = findById(profile().path("certificates"), profileId);
        assertEquals("모의 수정 자격증", stored.path("name").asText());
        assertTrue(stored.has("issuer") && stored.path("issuer").isNull());
        assertEquals("2026-10-01", stored.path("acquiredOn").asText(), "누락한 값은 유지해야 한다");

        JsonNode deleted = patch("DELETE_ITEM", "CERTIFICATES", documentId, null, true);
        assertFalse(hasId(items(deleted, "CERTIFICATES"), documentId));
        assertFalse(hasId(profile().path("certificates"), Long.toString(profileId)));
        assertEquals(0L, count("SELECT COUNT(*) FROM student_certificate WHERE student_id = ? AND id = ?",
                studentId, profileId));
        assertEquals(1, profile().path("certificates").size(), "기존 등록 항목은 남아 있어야 한다");
    }

    @Test
    void saveToProfileFalseChangesOnlyTheDocumentForBothRegisteredAndDocumentOnlyItems() throws Exception {
        JsonNode originalProfile = profile();
        JsonNode added = patch("ADD_ITEM", "CERTIFICATES", null,
                json.createObjectNode().put("name", "모의 서류 전용 자격증"), false);
        String documentOnlyId = findByName(items(added, "CERTIFICATES"), "모의 서류 전용 자격증")
                .path("itemId").asText();
        assertTrue(documentOnlyId.startsWith("cert-u"));
        JsonNode updated = patch("UPDATE_ITEM", "CERTIFICATES", "cert-" + certificateId,
                json.createObjectNode().put("name", "모의 서류에서만 수정"), false);
        JsonNode changedItem = findByName(items(updated, "CERTIFICATES"), "모의 서류에서만 수정");
        assertEquals("cert-" + certificateId, changedItem.path("itemId").asText());
        assertEquals("USER", changedItem.path("source").asText());
        assertTrue(originalProfile.equals(profile()), "서류 전용 수정은 등록 프로필에 반영하면 안 된다");
        patch("DELETE_ITEM", "CERTIFICATES", "cert-" + certificateId, null, false);
        JsonNode deleted = patch("DELETE_ITEM", "CERTIFICATES", documentOnlyId, null, false);

        assertEquals(0, items(deleted, "CERTIFICATES").size());
        assertTrue(originalProfile.equals(profile()), "false 변경은 프로필 값과 역량 갱신 시각을 모두 보존해야 한다");
        assertEquals(1L, count("SELECT COUNT(*) FROM student_certificate WHERE student_id = ?", studentId));
    }

    @Test
    void documentOnlyCertificateCanBePromotedAndRelinkedToOneRealProfileId() throws Exception {
        JsonNode added = patch("ADD_ITEM", "CERTIFICATES", null,
                json.createObjectNode().put("name", "모의 승격 자격증").put("issuer", "모의 최초 기관")
                        .put("acquiredOn", "2026-09-01"), false);
        String documentOnlyId = findByName(items(added, "CERTIFICATES"), "모의 승격 자격증")
                .path("itemId").asText();
        JsonNode promoted = patch("UPDATE_ITEM", "CERTIFICATES", documentOnlyId,
                json.createObjectNode().put("issuer", "모의 변경 기관"), true);
        String linkedId = findByName(items(promoted, "CERTIFICATES"), "모의 승격 자격증")
                .path("itemId").asText();
        long profileId = numericId(linkedId, "cert-");
        assertFalse(hasId(items(promoted, "CERTIFICATES"), documentOnlyId));
        JsonNode stored = findById(profile().path("certificates"), profileId);
        assertEquals("모의 승격 자격증", stored.path("name").asText());
        assertEquals("모의 변경 기관", stored.path("issuer").asText());
        assertEquals("2026-09-01", stored.path("acquiredOn").asText());

        JsonNode updated = patch("UPDATE_ITEM", "CERTIFICATES", linkedId,
                json.createObjectNode().put("name", "모의 승격 후 수정"), true);
        assertEquals(linkedId, findByName(items(updated, "CERTIFICATES"), "모의 승격 후 수정")
                .path("itemId").asText());
        assertEquals(2L, count("SELECT COUNT(*) FROM student_certificate WHERE student_id = ?", studentId),
                "승격 후 다시 저장할 때 중복 항목을 만들면 안 된다");
    }

    @Test
    void failedProfileValidationRollsBackTheDocumentAndKeepsTheRegisteredProfile() throws Exception {
        String documentOnlyName = "모".repeat(101); // 서류 한도 300자 이내지만 프로필 VARCHAR(100) 한도 초과.
        JsonNode added = patch("ADD_ITEM", "CERTIFICATES", null,
                json.createObjectNode().put("name", documentOnlyName), false);
        String documentOnlyId = findByName(items(added, "CERTIFICATES"), documentOnlyName).path("itemId").asText();
        Map<String, String> unchanged = ownedFingerprints();

        Reply promoted = send("PATCH", documentPath(), request("UPDATE_ITEM", "CERTIFICATES", documentOnlyId,
                json.createObjectNode().put("issuer", "모의 실패 기관"), true));
        assertInvalid(promoted);
        assertTrue(unchanged.equals(ownedFingerprints()), "프로필 저장 거절 시 서류 변경·시각도 함께 취소해야 한다");

        Reply updated = send("PATCH", documentPath(), request("UPDATE_ITEM", "CERTIFICATES", "cert-" + certificateId,
                json.createObjectNode().put("name", documentOnlyName), true));
        assertInvalid(updated);
        assertTrue(unchanged.equals(ownedFingerprints()), "등록 항목의 저장 실패도 서류와 원본을 보존해야 한다");
    }

    @Test
    void projectAndPortfolioChangesKeepReadmeRoleEvidenceAndRegisteredAttachment() throws Exception {
        Map<String, Object> original = projectRow();
        JsonNode bullets = items(document(), "PROJECTS").get(0).path("bullets").deepCopy();
        JsonNode changed = patch("UPDATE_ITEM", "PROJECTS", "prj-" + projectId,
                json.createObjectNode().put("title", "모의 수정 프로젝트").put("periodText", "2026.07-09")
                        .put("teamSize", 4).put("role", "모의 서류 전용 수정 역할"), true);

        assertEquals("모의 수정 프로젝트", findById(profile().path("projects"), projectId).path("title").asText());
        assertTrue(bullets.equals(items(changed, "PROJECTS").get(0).path("bullets")),
                "메타데이터 수정으로 AI 근거 문장을 바꾸면 안 된다");
        Map<String, Object> after = projectRow();
        for (String field : List.of("readme", "role_note", "tech_stack", "portfolio_file_url", "include_in_apply")) {
            assertTrue(json.valueToTree(original.get(field)).equals(json.valueToTree(after.get(field))),
                    "프로젝트 원문 필드를 보존해야 한다");
        }
        patch("UPDATE_ITEM", "PORTFOLIO", "pf-" + projectId, json.createObjectNode().put("include", false), true);
        JsonNode stored = findById(profile().path("projects"), projectId);
        assertFalse(stored.path("includeInApply").asBoolean());
        assertEquals("mock-portfolio.pdf", stored.path("portfolioFileName").asText());
        assertTrue(java.util.Objects.equals(original.get("readme"), projectRow().get("readme")));
        assertTrue(java.util.Objects.equals(original.get("role_note"), projectRow().get("role_note")));
    }

    private JsonNode patch(String op, String section, String id, ObjectNode item, boolean save) throws Exception {
        Reply result = send("PATCH", documentPath(), request(op, section, id, item, save));
        assertEquals(200, result.status());
        JsonNode document = result.body().path("data").path("document");
        assertTrue(document.isObject());
        assertTrue(document.equals(document()), "응답과 다시 조회한 저장 서류가 같아야 한다");
        if (save) {
            // 다음 요청 전에 noop worker까지 전달됐음을 확인해 pending 병합과 정리 경쟁을 피한다.
            verify(worker, timeout(2000).times(++expectedRefreshes)).refresh(studentId);
        }
        return document;
    }

    private ObjectNode request(String op, String section, String id, ObjectNode item, boolean save) {
        ObjectNode request = json.createObjectNode().put("op", op).put("section", section).put("saveToProfile", save);
        if (id != null) request.put("itemId", id);
        if (item != null) request.set("item", item);
        return request;
    }

    private JsonNode profile() throws Exception {
        Reply response = send("GET", "/api/students/" + studentId + "/profile", null);
        assertEquals(200, response.status());
        assertEquals(studentId.longValue(), response.body().path("data").path("studentId").asLong());
        return response.body().path("data");
    }

    private JsonNode document() throws Exception {
        Reply response = send("GET", "/api/applications/" + applicationId, null);
        assertEquals(200, response.status());
        assertEquals("DRAFT", response.body().path("data").path("status").asText());
        return response.body().path("data").path("document");
    }

    private static JsonNode items(JsonNode document, String section) {
        for (JsonNode value : document.path("sections")) {
            if (section.equals(value.path("section").asText())) return value.path("items");
        }
        throw new AssertionError("검증 서류의 섹션을 조회해야 한다");
    }

    private static JsonNode findByName(JsonNode rows, String name) {
        for (JsonNode row : rows) if (name.equals(row.path("name").asText())) return row;
        throw new AssertionError("요청으로 만든 모의 자격증이 있어야 한다");
    }

    private static JsonNode findById(JsonNode rows, long id) {
        for (JsonNode row : rows) if (row.path("itemId").asLong() == id) return row;
        throw new AssertionError("응답에 연결된 프로필 번호가 있어야 한다");
    }

    private static boolean hasId(JsonNode rows, String id) {
        for (JsonNode row : rows) if (id.equals(row.path("itemId").asText())) return true;
        return false;
    }

    private static long numericId(String id, String prefix) {
        assertTrue(id.startsWith(prefix) && !id.startsWith(prefix + "u"), "실제 프로필 번호로 연결되어야 한다");
        long parsed = Long.parseLong(id.substring(prefix.length()));
        assertTrue(parsed > 0);
        return parsed;
    }

    private static void assertInvalid(Reply response) {
        assertEquals(400, response.status());
        assertEquals("INVALID_REQUEST", response.body().path("error").path("code").asText());
        assertFalse(response.body().has("data"));
    }

    private Map<String, Object> projectRow() {
        return jdbc.queryForMap("SELECT * FROM student_project WHERE id = ? AND student_id = ?", projectId, studentId);
    }

    private String documentPath() {
        return "/api/applications/" + applicationId + "/document";
    }

    private Reply send(String method, String path, Object body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(12)).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Reply(response.statusCode(), json.readTree(response.body()));
    }

    private void requireOwnedRows() {
        assertTrue(studentId > 0);
        assertEquals(1L, count("SELECT COUNT(*) FROM student WHERE id = ? AND course_id = ? AND name = ?",
                studentId, courseId, marker));
        if (recommendationId != null) assertEquals(1L, count(
                "SELECT COUNT(*) FROM recommendation WHERE id = ? AND student_id = ?", recommendationId, studentId));
        if (itemId != null) assertEquals(1L, count("""
                SELECT COUNT(*) FROM recommendation_item i JOIN recommendation r ON r.id = i.recommendation_id
                WHERE i.id = ? AND i.recommendation_id = ? AND i.job_posting_id = ? AND r.student_id = ?
                """, itemId, recommendationId, jobId, studentId));
        if (applicationId != null) assertEquals(1L, count("""
                SELECT COUNT(*) FROM application WHERE id = ? AND student_id = ?
                  AND recommendation_item_id = ? AND job_posting_id = ?
                """, applicationId, studentId, itemId, jobId));
        for (String table : List.of("help_request", "student_status", "contact_log", "competency_profile",
                "student_education", "student_career", "student_cover_letter")) {
            assertEquals(0L, count("SELECT COUNT(*) FROM " + table + " WHERE student_id = ?", studentId),
                    "이 HTTP 경로가 만들지 않는 추가 원문은 추측으로 삭제하지 않는다");
        }
    }

    private Map<String, String> fingerprints() throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        for (Table table : TABLES) result.put(table.name(), signature(
                "SELECT * FROM " + table.name() + " ORDER BY " + table.key()));
        return result;
    }

    private Map<String, String> ownedFingerprints() throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        for (String table : List.of("student_certificate", "student_project")) result.put(table, signature(
                "SELECT * FROM " + table + " WHERE student_id = ? ORDER BY id", studentId));
        result.put("student", signature("SELECT * FROM student WHERE id = ?", studentId));
        result.put("document", signature("SELECT * FROM application_document WHERE application_id = ?", applicationId));
        result.put("application", signature("SELECT * FROM application WHERE id = ?", applicationId));
        return result;
    }

    private String signature(String sql, Object... values) throws Exception {
        byte[] content = json.writeValueAsBytes(jdbc.queryForList(sql, values));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private long count(String sql, Object... values) {
        return jdbc.queryForObject(sql, Long.class, values);
    }

    private long insert(String sql, Object... values) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        assertEquals(1, jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, keys));
        assertNotNull(keys.getKey());
        return keys.getKey().longValue();
    }

    private record Table(String name, String key) {}
    private record Reply(int status, JsonNode body) {}
}
