// 주인: BE-B
package com.ieum.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SQL로 만든 독립 모의 초안을 실제 제출 API부터 검토·M7 조회까지 연결한다.
 * 기존 행은 변경하지 않고, 생성키와 소유 관계를 확인한 모의 행만 삭제한 뒤 전체 행 지문을 비교한다.
 */
@EnabledIfEnvironmentVariable(named = "IEUM_COORDINATOR_REVIEW_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]",
        "spring.sql.init.mode=never", "app.ai.mock=true",
        "app.seoul.sync-cron=-", "app.status.recalculate-cron=-"
})
class CoordinatorSubmissionDatabaseTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    private static final String REJECT_NOTE = "  모의 제출 연동 보완 요청\n원문 유지  ";
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
    @Autowired private PlatformTransactionManager transactionManager;

    private Map<String, String> before;
    private Long courseId;
    private Long jobId;
    private Long studentId;
    private Long recommendationId;
    private Long itemId;
    private Long applicationId;
    private String studentMarker;
    private JsonNode savedDocument;

    @BeforeEach
    void createOnlyAnIndependentMockDraft() throws Exception {
        courseId = jdbc.queryForObject("SELECT MIN(id) FROM course", Long.class);
        jobId = jdbc.queryForObject("SELECT MIN(id) FROM job_posting", Long.class);
        assertNotNull(courseId, "참조할 기존 모의 과정 한 건이 필요하다");
        assertNotNull(jobId, "참조할 기존 공고 한 건이 필요하다");
        List<String> engines = jdbc.queryForList("""
                SELECT ENGINE FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN (
                    'student', 'recommendation', 'recommendation_item', 'application',
                    'application_document', 'student_status', 'contact_log')
                """, String.class);
        assertEquals(7, engines.size(), "기존 제출·검토·상태 테이블이 준비되어 있어야 한다");
        assertTrue(engines.stream().allMatch("InnoDB"::equalsIgnoreCase), "롤백 가능한 테이블만 사용한다");
        before = fingerprints();
        studentMarker = "모의 제출 검증 " + UUID.randomUUID();
        studentId = insert("""
                INSERT INTO student (course_id, name, completed_on, last_response_at)
                VALUES (?, ?, DATE_SUB(CURDATE(), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY))
                """, courseId, studentMarker);
        recommendationId = insert("INSERT INTO recommendation (student_id, trigger_type) VALUES (?, 'VISIT')",
                studentId);
        itemId = insert("""
                INSERT INTO recommendation_item
                    (recommendation_id, job_posting_id, rank_no, reasons, response, responded_at)
                VALUES (?, ?, 1, '[]', 'APPLY', DATE_SUB(NOW(), INTERVAL 8 DAY))
                """, recommendationId, jobId);
        applicationId = insert("""
                INSERT INTO application (student_id, recommendation_item_id, job_posting_id, status)
                VALUES (?, ?, ?, 'DRAFT')
                """, studentId, itemId, jobId);
        savedDocument = json.readTree("""
                {
                  "companyInfoStatus":"NOT_FOUND", "excludedSentenceCount":0,
                  "sections":[{"section":"COVER_LETTER","sentences":[
                    {"sentenceId":"user-1","text":"모의 직접 입력 지원 문장","source":"USER","evidence":[]}
                  ]}]
                }
                """);
        assertEquals(1, jdbc.update("INSERT INTO application_document (application_id, document) VALUES (?, ?)",
                applicationId, json.writeValueAsString(savedDocument)));
    }

    @AfterEach
    void removeOnlyOwnedFixtureAndVerifyAllOriginalRowsAndTimes() throws Exception {
        try {
            if (studentId != null) {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    requireOwnedRows();
                    if (applicationId != null) {
                        jdbc.update("DELETE FROM application_document WHERE application_id = ?", applicationId);
                        assertEquals(1, jdbc.update("DELETE FROM application WHERE id = ? AND student_id = ?",
                                applicationId, studentId));
                    }
                    assertEquals(0L, count("SELECT COUNT(*) FROM help_request WHERE student_id = ?", studentId));
                    for (Long id : jdbc.queryForList("SELECT id FROM contact_log WHERE student_id = ?",
                            Long.class, studentId)) {
                        assertTrue(id > 0);
                        assertEquals(1, jdbc.update("DELETE FROM contact_log WHERE id = ? AND student_id = ?",
                                id, studentId));
                    }
                    jdbc.update("DELETE FROM student_status WHERE student_id = ?", studentId);
                    if (itemId != null) {
                        assertEquals(1, jdbc.update(
                                "DELETE FROM recommendation_item WHERE id = ? AND recommendation_id = ?",
                                itemId, recommendationId));
                    }
                    if (recommendationId != null) {
                        assertEquals(1, jdbc.update("DELETE FROM recommendation WHERE id = ? AND student_id = ?",
                                recommendationId, studentId));
                    }
                    assertEquals(1, jdbc.update("DELETE FROM student WHERE id = ? AND course_id = ? AND name = ?",
                            studentId, courseId, studentMarker));
                });
            }
        } finally {
            if (before != null) {
                assertTrue(before.equals(fingerprints()), "기존 17개 테이블의 행과 저장 시각을 보존해야 한다");
            }
        }
    }

    @Test
    void draftSubmissionApprovalAndDeliveryAreVisibleThroughM7AndTheReviewQueue() throws Exception {
        Map<String, String> unchangedSources = sourceFingerprints();
        assertM7("DRAFT");
        assertFalse(inReviewQueue());
        assertNull(applicationTime("submitted_at"));
        Map<String, String> draft = ownedFingerprints();
        assertError(send("POST", reviewPath() + "/approve", null), 409, "INVALID_STATUS");
        assertError(send("POST", reviewPath() + "/reject", Map.of("note", REJECT_NOTE)), 409, "INVALID_STATUS");
        assertError(send("POST", reviewPath() + "/deliver", null), 409, "INVALID_STATUS");
        assertTrue(draft.equals(ownedFingerprints()), "검토 요청 전에는 어떤 검토 동작도 저장되면 안 된다");

        LocalDateTime oldResponse = lastResponse();
        assertResult(send("POST", applicationPath() + "/submit", null), "SUBMITTED");

        LocalDateTime submittedAt = applicationTime("submitted_at");
        LocalDateTime responseAfterSubmit = lastResponse();
        assertNotNull(submittedAt);
        assertTrue(responseAfterSubmit.isAfter(oldResponse), "실제 제출 API가 마지막 응답 시각을 갱신해야 한다");
        assertStatus("APPLYING");
        assertM7("SUBMITTED");
        assertTrue(inReviewQueue());
        assertStoredDocument("SUBMITTED");
        assertNull(applicationTime("reviewed_at"));
        assertNull(applicationTime("delivered_at"));

        Map<String, String> submitted = ownedFingerprints();
        assertError(send("POST", applicationPath() + "/submit", null), 409, "INVALID_STATUS");
        assertError(send("POST", reviewPath() + "/deliver", null), 409, "INVALID_STATUS");
        assertTrue(submitted.equals(ownedFingerprints()), "중복 제출·승인 전 전달은 시각과 상태를 변경하면 안 된다");

        assertResult(send("POST", reviewPath() + "/approve", null), "APPROVED");

        assertM7("APPROVED");
        assertFalse(inReviewQueue());
        assertStatus("APPLYING");
        assertNotNull(applicationTime("reviewed_at"));
        assertNull(applicationTime("delivered_at"));
        LocalDateTime reviewedAt = applicationTime("reviewed_at");

        assertResult(send("POST", reviewPath() + "/deliver", null), "DELIVERED");

        assertM7("DELIVERED");
        assertFalse(inReviewQueue());
        assertStatus("APPLYING");
        assertStoredDocument("DELIVERED");
        assertNotNull(applicationTime("delivered_at"));
        assertEquals(submittedAt, applicationTime("submitted_at"));
        assertEquals(reviewedAt, applicationTime("reviewed_at"));
        assertEquals(responseAfterSubmit, lastResponse(), "검토 동작을 수료생의 새 응답으로 기록하면 안 된다");
        assertEquals(0L, count("SELECT COUNT(*) FROM contact_log WHERE student_id = ?", studentId));
        assertTrue(unchangedSources.equals(sourceFingerprints()), "추천 원문과 서류 본문·저장 시각은 보존해야 한다");
        Map<String, String> delivered = ownedFingerprints();
        assertError(send("POST", reviewPath() + "/deliver", null), 409, "INVALID_STATUS");
        assertError(send("POST", reviewPath() + "/reject", Map.of("note", REJECT_NOTE)), 409, "INVALID_STATUS");
        assertTrue(delivered.equals(ownedFingerprints()), "전달 후 중복·역방향 처리는 저장되면 안 된다");
    }

    @Test
    void submittedDraftRejectionIsVisibleInM7AndContactHistoryAndCannotBeResubmitted() throws Exception {
        Map<String, String> unchangedSources = sourceFingerprints();
        assertResult(send("POST", applicationPath() + "/submit", null), "SUBMITTED");
        LocalDateTime submittedAt = applicationTime("submitted_at");
        LocalDateTime responseAfterSubmit = lastResponse();
        assertTrue(inReviewQueue());

        assertResult(send("POST", reviewPath() + "/reject", Map.of("note", REJECT_NOTE)), "REJECTED");

        assertM7("REJECTED");
        assertFalse(inReviewQueue());
        assertStatus("WAITING");
        assertStoredDocument("REJECTED");
        assertEquals(submittedAt, applicationTime("submitted_at"));
        assertNotNull(applicationTime("reviewed_at"));
        assertNull(applicationTime("delivered_at"));
        assertEquals(responseAfterSubmit, lastResponse());
        assertEquals(REJECT_NOTE, jdbc.queryForObject(
                "SELECT reject_note FROM application WHERE id = ?", String.class, applicationId));
        JsonNode detail = data(send("GET", "/api/coordinator/students/" + studentId, null));
        JsonNode logs = detail.path("contactLogs");
        assertTrue(logs.isArray());
        assertEquals(1, logs.size());
        assertEquals("MESSAGE", logs.get(0).path("contactType").asText());
        assertTrue(REJECT_NOTE.equals(logs.get(0).path("note").asText()), "반려 메모 원문이 상담 기록에 노출되어야 한다");
        assertEquals(1L, count("SELECT COUNT(*) FROM contact_log WHERE student_id = ?", studentId));
        assertTrue(unchangedSources.equals(sourceFingerprints()), "반려도 추천 원문과 서류 본문을 보존해야 한다");

        Map<String, String> rejected = ownedFingerprints();
        assertError(send("POST", applicationPath() + "/submit", null), 409, "INVALID_STATUS");
        assertError(send("POST", reviewPath() + "/approve", null), 409, "INVALID_STATUS");
        assertError(send("POST", reviewPath() + "/reject", Map.of("note", REJECT_NOTE)), 409, "INVALID_STATUS");
        assertError(send("POST", reviewPath() + "/deliver", null), 409, "INVALID_STATUS");
        assertTrue(rejected.equals(ownedFingerprints()), "반려 후 요청은 상담 기록·응답 시각·상태를 추가 변경하면 안 된다");
    }

    private void requireOwnedRows() {
        assertTrue(studentId > 0);
        assertEquals(1L, count("SELECT COUNT(*) FROM student WHERE id = ? AND course_id = ? AND name = ?",
                studentId, courseId, studentMarker));
        if (recommendationId != null) {
            assertTrue(recommendationId > 0);
            assertEquals(1L, count("SELECT COUNT(*) FROM recommendation WHERE id = ? AND student_id = ?",
                    recommendationId, studentId));
        }
        if (itemId != null) {
            assertTrue(itemId > 0);
            assertEquals(1L, count("""
                    SELECT COUNT(*) FROM recommendation_item i JOIN recommendation r ON r.id = i.recommendation_id
                    WHERE i.id = ? AND i.recommendation_id = ? AND r.student_id = ? AND i.job_posting_id = ?
                    """, itemId, recommendationId, studentId, jobId));
        }
        if (applicationId != null) {
            assertTrue(applicationId > 0);
            assertEquals(1L, count("""
                    SELECT COUNT(*) FROM application
                    WHERE id = ? AND student_id = ? AND recommendation_item_id = ? AND job_posting_id = ?
                    """, applicationId, studentId, itemId, jobId));
        }
    }

    private void assertM7(String expectedStatus) throws Exception {
        JsonNode result = data(send("GET", "/api/students/" + studentId + "/applications", null));
        JsonNode applications = result.path("applications");
        assertTrue(applications.isArray());
        assertEquals(1, applications.size(), "독립 수료생에게 다른 수료생의 지원서가 섞이면 안 된다");
        JsonNode row = applications.get(0);
        assertEquals(applicationId.longValue(), row.path("applicationId").asLong());
        assertEquals(expectedStatus, row.path("status").asText());
        assertEquals(expectedStatus, jdbc.queryForObject(
                "SELECT status FROM application WHERE id = ?", String.class, applicationId));
        assertEquals(1, row.path("userEditedCount").asInt());
        assertTrue(row.has("submittedAt"), "미제출 초안도 제출 시각 키를 유지해야 한다");
        LocalDateTime submittedAt = applicationTime("submitted_at");
        if (submittedAt == null) {
            assertTrue(row.path("submittedAt").isNull());
        } else {
            assertEquals(submittedAt.atZone(SEOUL).toOffsetDateTime(),
                    OffsetDateTime.parse(row.path("submittedAt").asText()));
        }
        assertTrue(result.path("otherItems").isArray());
        assertEquals(0, result.path("otherItems").size(), "지원서가 있는 추천 항목을 다른 공고로 중복 표시하면 안 된다");
    }

    private void assertStoredDocument(String expectedStatus) throws Exception {
        JsonNode studentView = data(send("GET", applicationPath(), null));
        JsonNode coordinatorView = data(send("GET", reviewPath(), null));
        assertEquals(expectedStatus, studentView.path("status").asText());
        assertEquals(expectedStatus, coordinatorView.path("status").asText());
        assertTrue(savedDocument.equals(studentView.path("document")), "수료생 조회는 저장 문서를 유지해야 한다");
        assertTrue(savedDocument.equals(coordinatorView.path("document")), "검토 조회는 같은 저장 문서를 반환해야 한다");
        assertEquals(1, coordinatorView.path("verification").path("userEditedCount").asInt());
    }

    private void assertStatus(String subStatus) {
        assertEquals("SEEKING", jdbc.queryForObject("SELECT status FROM student_status WHERE student_id = ?",
                String.class, studentId));
        assertEquals(subStatus, jdbc.queryForObject("SELECT sub_status FROM student_status WHERE student_id = ?",
                String.class, studentId));
    }

    private boolean inReviewQueue() throws Exception {
        JsonNode queue = data(send("GET", "/api/coordinator/dashboard", null)).path("reviewQueue");
        assertTrue(queue.isArray());
        return StreamSupport.stream(queue.spliterator(), false)
                .anyMatch(row -> row.path("applicationId").asLong() == applicationId.longValue());
    }

    private String applicationPath() {
        return "/api/applications/" + applicationId;
    }

    private String reviewPath() {
        return "/api/coordinator/applications/" + applicationId;
    }

    private Reply send(String method, String path, Object body) throws Exception {
        String content = body == null ? "" : json.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(12)).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(content, StandardCharsets.UTF_8)).build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Reply(response.statusCode(), json.readTree(response.body()));
    }

    private static JsonNode data(Reply response) {
        assertEquals(200, response.status());
        assertFalse(response.body().has("error"));
        assertTrue(response.body().path("data").isObject());
        return response.body().path("data");
    }

    private void assertResult(Reply response, String status) {
        JsonNode result = data(response);
        assertEquals(applicationId.longValue(), result.path("applicationId").asLong());
        assertEquals(status, result.path("status").asText());
    }

    private static void assertError(Reply response, int status, String code) {
        assertEquals(status, response.status());
        assertEquals(code, response.body().path("error").path("code").asText());
        assertFalse(response.body().has("data"));
    }

    private LocalDateTime applicationTime(String column) {
        assertTrue(List.of("submitted_at", "reviewed_at", "delivered_at").contains(column));
        return jdbc.queryForObject("SELECT " + column + " FROM application WHERE id = ?",
                LocalDateTime.class, applicationId);
    }

    private LocalDateTime lastResponse() {
        return jdbc.queryForObject("SELECT last_response_at FROM student WHERE id = ?", LocalDateTime.class, studentId);
    }

    private long count(String sql, Object... parameters) {
        return jdbc.queryForObject(sql, Long.class, parameters);
    }

    private long insert(String sql, Object... parameters) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        assertEquals(1, jdbc.update(connection -> {
            PreparedStatement prepared = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < parameters.length; i++) prepared.setObject(i + 1, parameters[i]);
            return prepared;
        }, keys));
        assertNotNull(keys.getKey());
        return keys.getKey().longValue();
    }

    private Map<String, String> fingerprints() throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        for (Table table : TABLES) {
            result.put(table.name(), signature("SELECT * FROM " + table.name() + " ORDER BY " + table.key()));
        }
        return result;
    }

    private Map<String, String> sourceFingerprints() throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("recommendation", signature("SELECT * FROM recommendation WHERE id = ?", recommendationId));
        result.put("item", signature("SELECT * FROM recommendation_item WHERE id = ?", itemId));
        result.put("document", signature("SELECT * FROM application_document WHERE application_id = ?", applicationId));
        return result;
    }

    private Map<String, String> ownedFingerprints() throws Exception {
        Map<String, String> result = sourceFingerprints();
        result.put("student", signature("SELECT * FROM student WHERE id = ?", studentId));
        result.put("application", signature("SELECT * FROM application WHERE id = ?", applicationId));
        result.put("contact", signature("SELECT * FROM contact_log WHERE student_id = ? ORDER BY id", studentId));
        result.put("status", signature("SELECT * FROM student_status WHERE student_id = ?", studentId));
        return result;
    }

    private String signature(String sql, Object... parameters) throws Exception {
        byte[] content = json.writeValueAsBytes(jdbc.queryForList(sql, parameters));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private record Table(String name, String key) {}
    private record Reply(int status, JsonNode body) {}
}
