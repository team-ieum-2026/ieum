// 주인: BE-B
package com.ieum.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

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
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실제 HTTP·MySQL·커밋 후 상태 판정을 독립 모의 지원서로 확인한다.
 * 기존 행은 수정하지 않는다. 생성키와 소유 관계를 확인한 모의 행만 정리하고 전후 17개 테이블 지문을 비교한다.
 */
@EnabledIfEnvironmentVariable(named = "IEUM_COORDINATOR_REVIEW_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]",
        "spring.sql.init.mode=never", "app.ai.mock=true",
        "app.seoul.sync-cron=-", "app.status.recalculate-cron=-"
})
class CoordinatorApplicationDatabaseTest {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    private static final LocalDateTime SUBMITTED_AT = LocalDateTime.of(2026, 10, 27, 8, 0);
    private static final String NOTE = "  모의 보완 요청\n문장 원문 유지  ";
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
    @MockitoSpyBean private CoordinatorApplicationMapper mapper;

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
    void createOnlyAnIndependentMockStudentAndSubmittedApplication() throws Exception {
        courseId = jdbc.queryForObject("SELECT MIN(id) FROM course", Long.class);
        jobId = jdbc.queryForObject("SELECT MIN(id) FROM job_posting", Long.class);
        assumeTrue(courseId != null && jobId != null, "참조할 기존 과정과 공고 한 건씩이 필요하다");
        List<String> engines = jdbc.queryForList("""
                SELECT ENGINE FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN (
                    'student', 'recommendation', 'recommendation_item', 'application',
                    'application_document', 'student_status', 'contact_log')
                """, String.class);
        assertEquals(7, engines.size(), "기존 검토·상태 스키마가 준비되어 있어야 한다");
        assertTrue(engines.stream().allMatch("InnoDB"::equalsIgnoreCase), "원자적 롤백이 가능한 테이블만 사용한다");
        before = fingerprints();
        studentMarker = "모의 검토 검증 " + UUID.randomUUID();
        studentId = insert("""
                INSERT INTO student (course_id, name, completed_on, last_response_at)
                VALUES (?, ?, DATE_SUB(CURDATE(), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 1 HOUR))
                """, courseId, studentMarker);
        recommendationId = insert("INSERT INTO recommendation (student_id, trigger_type) VALUES (?, 'VISIT')",
                studentId);
        itemId = insert("""
                INSERT INTO recommendation_item
                    (recommendation_id, job_posting_id, rank_no, reasons, response, responded_at)
                VALUES (?, ?, 1, '[]', 'APPLY', DATE_SUB(NOW(), INTERVAL 1 HOUR))
                """, recommendationId, jobId);
        applicationId = insert("""
                INSERT INTO application
                    (student_id, recommendation_item_id, job_posting_id, status, submitted_at, reject_note)
                VALUES (?, ?, ?, 'SUBMITTED', ?, '모의 이전 반려 메모')
                """, studentId, itemId, jobId, SUBMITTED_AT);
        savedDocument = json.readTree("""
                {
                  "companyInfoStatus":"PARTIAL", "excludedSentenceCount":2,
                  "sections":[{"section":"COVER_LETTER","sentences":[
                    {"sentenceId":"ai-1","text":"모의 근거 문장","source":"AI",
                     "evidence":[{"refType":"COURSE","refId":%d}]},
                    {"sentenceId":"ai-2","text":"모의 근거 없는 문장","source":"AI","evidence":[]},
                    {"sentenceId":"user-1","text":"모의 직접 수정 문장 가","source":"USER","evidence":[]},
                    {"sentenceId":"user-2","text":"모의 직접 수정 문장 나","source":"USER","evidence":[]}
                  ]}]
                }
                """.formatted(courseId));
        assertEquals(1, jdbc.update("INSERT INTO application_document (application_id, document) VALUES (?, ?)",
                applicationId, json.writeValueAsString(savedDocument)));
    }

    @AfterEach
    void removeOnlyVerifiedOwnedRowsAndKeepAllOriginalRowsAndTimes() throws Exception {
        try {
            if (studentId != null) {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    requireOwnedRows();
                    if (applicationId != null) {
                        jdbc.update("DELETE FROM application_document WHERE application_id = ?", applicationId);
                        assertEquals(1, jdbc.update("DELETE FROM application WHERE id = ? AND student_id = ?",
                                applicationId, studentId));
                    }
                    // 이 테스트 경로는 도움 요청을 만들지 않는다. 예상 밖의 행을 추측으로 삭제하지 않는다.
                    assertEquals(0L, count("SELECT COUNT(*) FROM help_request WHERE student_id = ?", studentId));
                    for (Long id : jdbc.queryForList("SELECT id FROM contact_log WHERE student_id = ?", Long.class,
                            studentId)) {
                        assertTrue(id > 0);
                        assertEquals(1L, count("SELECT COUNT(*) FROM contact_log WHERE id = ? AND student_id = ?",
                                id, studentId));
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
                assertTrue(before.equals(fingerprints()), "기존 17개 테이블의 행과 저장 시각을 모두 보존해야 한다");
            }
        }
    }

    @Test
    void detailReturnsTheStoredSnapshotAndVerificationWithoutChangingAnyFixtureRow() throws Exception {
        Map<String, String> unchanged = ownedFingerprints();

        Reply response = send("GET", base(), null);

        assertEquals(200, response.status());
        JsonNode data = response.body().path("data");
        assertEquals(applicationId.longValue(), data.path("applicationId").asLong());
        assertEquals(studentId.longValue(), data.path("student").path("studentId").asLong());
        assertEquals(jobId.longValue(), data.path("job").path("jobPostingId").asLong());
        assertTrue(savedDocument.equals(data.get("document")), "저장한 서류 스냅샷을 그대로 반환해야 한다");
        JsonNode verification = data.path("verification");
        assertEquals(2, verification.path("aiSentenceCount").asInt());
        assertEquals(1, verification.path("aiSentenceWithEvidence").asInt());
        assertEquals(2, verification.path("excludedSentenceCount").asInt());
        assertEquals(2, verification.path("userEditedCount").asInt());
        assertEquals("PARTIAL", verification.path("companyInfoStatus").asText());
        assertTrue(unchanged.equals(ownedFingerprints()), "GET은 상태와 원문·저장 시각을 변경하지 않아야 한다");
    }

    @Test
    void approveThenDeliverCommitsStatusAndLeavesTheStudentResponseAndDocumentUntouched() throws Exception {
        Map<String, String> sources = sourceFingerprints();
        LocalDateTime responseAt = lastResponse();
        assertTrue(inReviewQueue());

        assertResult(send("POST", base() + "/approve", null), "APPROVED");

        assertEquals("APPROVED", applicationStatus());
        LocalDateTime reviewedAt = applicationTime("reviewed_at");
        assertNotNull(reviewedAt);
        assertNull(applicationTime("delivered_at"));
        assertNull(jdbc.queryForObject("SELECT reject_note FROM application WHERE id = ?", String.class, applicationId));
        assertStudentStatus("APPLYING");
        assertFalse(inReviewQueue());
        assertError(send("POST", base() + "/approve", null), 409, "INVALID_STATUS");

        assertResult(send("POST", base() + "/deliver", null), "DELIVERED");

        assertEquals("DELIVERED", applicationStatus());
        assertEquals(reviewedAt, applicationTime("reviewed_at"));
        assertNotNull(applicationTime("delivered_at"));
        assertEquals(SUBMITTED_AT, applicationTime("submitted_at"));
        assertStudentStatus("APPLYING");
        assertError(send("POST", base() + "/deliver", null), 409, "INVALID_STATUS");
        assertEquals(responseAt, lastResponse());
        assertEquals(0L, contactCount());
        assertTrue(sources.equals(sourceFingerprints()), "검토 전이는 학생·추천 응답·문서를 변경하지 않아야 한다");
    }

    @Test
    void rejectionPreservesTheNoteAndCreatesOneMessageAuditBeforeRecalculatingWaiting() throws Exception {
        Map<String, String> sources = sourceFingerprints();

        assertResult(send("POST", base() + "/reject", Map.of("note", NOTE)), "REJECTED");

        assertEquals("REJECTED", applicationStatus());
        assertEquals(NOTE, jdbc.queryForObject("SELECT reject_note FROM application WHERE id = ?",
                String.class, applicationId));
        assertEquals(1L, contactCount());
        assertEquals("MESSAGE", jdbc.queryForObject("SELECT contact_type FROM contact_log WHERE student_id = ?",
                String.class, studentId));
        assertEquals(NOTE, jdbc.queryForObject("SELECT note FROM contact_log WHERE student_id = ?",
                String.class, studentId));
        assertEquals(applicationTime("reviewed_at"), jdbc.queryForObject(
                "SELECT created_at FROM contact_log WHERE student_id = ?", LocalDateTime.class, studentId));
        assertEquals(SUBMITTED_AT, applicationTime("submitted_at"));
        assertNull(applicationTime("delivered_at"));
        assertStudentStatus("WAITING");
        Map<String, String> afterFirst = ownedFingerprints();

        assertError(send("POST", base() + "/reject", Map.of("note", NOTE)), 409, "INVALID_STATUS");
        assertError(send("POST", base() + "/approve", null), 409, "INVALID_STATUS");

        assertEquals(1L, contactCount());
        assertTrue(afterFirst.equals(ownedFingerprints()), "반복 반려로 감사 기록이나 시각이 추가 변경되면 안 된다");
        assertTrue(sources.equals(sourceFingerprints()), "반려는 등록 원문과 학생 응답 시각을 보존해야 한다");
    }

    @Test
    void badRequestsMissingApplicationsAndInvalidTransitionsLeaveAllFixtureRowsUnchanged() throws Exception {
        Map<String, String> unchanged = ownedFingerprints();
        long missing = Long.MAX_VALUE;
        assertEquals(0L, count("SELECT COUNT(*) FROM application WHERE id = ?", missing));

        assertError(send("POST", base() + "/approve", Map.of("note", "모의 잘못된 본문")),
                400, "INVALID_REQUEST");
        assertError(send("POST", base() + "/reject", Map.of("note", " \t\n ")), 400, "INVALID_REQUEST");
        assertError(send("GET", "/api/coordinator/applications/" + missing, null), 404, "APPLICATION_NOT_FOUND");
        assertError(send("POST", "/api/coordinator/applications/" + missing + "/approve", null),
                404, "APPLICATION_NOT_FOUND");
        assertError(send("POST", base() + "/deliver", null), 409, "INVALID_STATUS");

        assertTrue(unchanged.equals(ownedFingerprints()), "잘못된 요청은 상태·문서·응답·상담 기록을 남기지 않아야 한다");
    }

    @Test
    void failedOrZeroRowAuditInsertRollsBackRejectionAndCannotTriggerACommittedStatusChange() throws Exception {
        Map<String, String> unchanged = ownedFingerprints();
        doThrow(new IllegalStateException("simulated audit insert failure")).when(mapper)
                .insertRejectContact(eq(studentId), eq(NOTE), any(LocalDateTime.class));

        assertError(send("POST", base() + "/reject", Map.of("note", NOTE)), 500, "INTERNAL_ERROR");
        assertTrue(unchanged.equals(ownedFingerprints()), "감사 기록 예외 시 반려 갱신까지 롤백해야 한다");

        doReturn(0).when(mapper).insertRejectContact(eq(studentId), eq(NOTE), any(LocalDateTime.class));

        assertError(send("POST", base() + "/reject", Map.of("note", NOTE)), 500, "INTERNAL_ERROR");
        assertTrue(unchanged.equals(ownedFingerprints()), "감사 기록 0행도 반려와 상태 이벤트를 커밋하면 안 된다");
    }

    @Test
    void simultaneousApproveAndRejectHaveOneWinnerAndAuditMatchesTheFinalState() throws Exception {
        Map<String, String> sources = sourceFingerprints();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var approve = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return send("POST", base() + "/approve", null);
            });
            var reject = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return send("POST", base() + "/reject", Map.of("note", NOTE));
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            Reply first = approve.get(15, TimeUnit.SECONDS);
            Reply second = reject.get(15, TimeUnit.SECONDS);
            assertEquals(List.of(200, 409), List.of(first.status(), second.status()).stream().sorted().toList());
            assertError(first.status() == 409 ? first : second, 409, "INVALID_STATUS");
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
        String finalStatus = applicationStatus();
        assertTrue("APPROVED".equals(finalStatus) || "REJECTED".equals(finalStatus));
        assertEquals("REJECTED".equals(finalStatus) ? 1L : 0L, contactCount());
        if ("REJECTED".equals(finalStatus)) {
            assertEquals(NOTE, jdbc.queryForObject("SELECT note FROM contact_log WHERE student_id = ?",
                    String.class, studentId));
        }
        assertStudentStatus("APPROVED".equals(finalStatus) ? "APPLYING" : "WAITING");
        assertNotNull(applicationTime("reviewed_at"));
        assertNull(applicationTime("delivered_at"));
        assertTrue(sources.equals(sourceFingerprints()), "경쟁 처리도 학생·추천 응답·문서를 변경하면 안 된다");
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

    private String base() {
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

    private void assertResult(Reply response, String status) {
        assertEquals(200, response.status());
        assertEquals(applicationId.longValue(), response.body().path("data").path("applicationId").asLong());
        assertEquals(status, response.body().path("data").path("status").asText());
    }

    private static void assertError(Reply response, int status, String code) {
        assertEquals(status, response.status());
        assertEquals(code, response.body().path("error").path("code").asText());
        assertFalse(response.body().has("data"));
    }

    private boolean inReviewQueue() throws Exception {
        Reply dashboard = send("GET", "/api/coordinator/dashboard", null);
        assertEquals(200, dashboard.status());
        return StreamSupport.stream(dashboard.body().path("data").path("reviewQueue").spliterator(), false)
                .anyMatch(row -> row.path("applicationId").asLong() == applicationId.longValue());
    }

    private void assertStudentStatus(String subStatus) {
        assertEquals("SEEKING", jdbc.queryForObject("SELECT status FROM student_status WHERE student_id = ?",
                String.class, studentId));
        assertEquals(subStatus, jdbc.queryForObject("SELECT sub_status FROM student_status WHERE student_id = ?",
                String.class, studentId));
    }

    private String applicationStatus() {
        return jdbc.queryForObject("SELECT status FROM application WHERE id = ?", String.class, applicationId);
    }

    private LocalDateTime applicationTime(String column) {
        assertTrue(List.of("submitted_at", "reviewed_at", "delivered_at").contains(column));
        return jdbc.queryForObject("SELECT " + column + " FROM application WHERE id = ?",
                LocalDateTime.class, applicationId);
    }

    private LocalDateTime lastResponse() {
        return jdbc.queryForObject("SELECT last_response_at FROM student WHERE id = ?", LocalDateTime.class, studentId);
    }

    private long contactCount() {
        return count("SELECT COUNT(*) FROM contact_log WHERE student_id = ?", studentId);
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
        result.put("student", signature("SELECT * FROM student WHERE id = ?", studentId));
        result.put("recommendation", signature("SELECT * FROM recommendation WHERE id = ?", recommendationId));
        result.put("item", signature("SELECT * FROM recommendation_item WHERE id = ?", itemId));
        result.put("document", signature("SELECT * FROM application_document WHERE application_id = ?", applicationId));
        return result;
    }

    private Map<String, String> ownedFingerprints() throws Exception {
        Map<String, String> result = sourceFingerprints();
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
