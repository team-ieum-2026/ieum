package com.ieum.recommendation;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

/** 실제 HTTP · MySQL 로 응답 취소를 검증한다. 독립 모의 자료만 만들고 지운다. AI 는 고정 응답. */
@EnabledIfEnvironmentVariable(named = "IEUM_RESPONSE_CANCEL_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]",
        "spring.sql.init.mode=never", "app.ai.mock=true",
        "app.seoul.sync-cron=-", "app.status.recalculate-cron=-"
})
class RecommendationResponseCancelDatabaseTest {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    private static final List<String> TABLES = List.of("course", "student", "recommendation",
            "recommendation_item", "application", "application_document", "help_request", "student_status");
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    private Map<String, Long> countsBefore;
    private Long courseId;
    private Long studentId;
    private Long jobId;

    @BeforeEach
    void createIndependentMockFixture() {
        jobId = jdbc.queryForObject("SELECT MIN(id) FROM job_posting", Long.class);
        assertNotNull(jobId, "외래 키로 참조할 기존 모의 공고가 한 건 필요하다");
        countsBefore = counts();
        courseId = insert("""
                INSERT INTO course(name,cohort,campus,started_on,ended_on)
                VALUES ('응답 취소 검증 과정','검증','모의 캠퍼스','2026-03-01','2026-08-31')
                """);
        studentId = insert("INSERT INTO student(course_id,name,completed_on,last_response_at) VALUES (?,?,?,?)",
                courseId, "응답 취소 검증", LocalDate.of(2026, 8, 31),
                LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(2).truncatedTo(ChronoUnit.SECONDS));
    }

    @AfterEach
    void removeOnlyOwnFixture() {
        if (studentId != null) {
            jdbc.update("DELETE FROM help_request WHERE student_id = ?", studentId);
            jdbc.update("DELETE FROM application WHERE student_id = ?", studentId);
            jdbc.update("DELETE FROM recommendation WHERE student_id = ?", studentId);
            jdbc.update("DELETE FROM student WHERE id = ?", studentId);
        }
        if (courseId != null) jdbc.update("DELETE FROM course WHERE id = ?", courseId);
        if (countsBefore != null) assertEquals(countsBefore, counts(), "기존 행 수를 보존해야 한다");
    }

    @Test
    void passCancelClearsReasonAndAllowsNewResponse() throws Exception {
        long item = item();
        assertEquals(200, respond(item, Map.of("response", "PASS", "passReason", "OTHER", "passNote", "모의 메모")).status());
        LocalDateTime responseAt = lastResponse();

        Reply cancelled = cancel(item, "PASS");
        assertEquals(200, cancelled.status());
        assertEquals(3, cancelled.body().path("data").size());
        assertEquals("NONE", cancelled.body().path("data").path("response").asText());
        assertTrue(cancelled.body().path("data").get("applicationId").isNull());
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT response, pass_reason, pass_note, responded_at FROM recommendation_item WHERE id = ?", item);
        assertEquals("NONE", row.get("response"));
        assertNull(row.get("pass_reason"));
        assertNull(row.get("pass_note"));
        assertNull(row.get("responded_at"));
        assertEquals(responseAt, lastResponse(), "취소는 마지막 응답 시각을 바꾸지 않는다");
        assertNotNull(status(), "취소 뒤 상태를 다시 계산한다");

        assertEquals(200, cancel(item, "PASS").status(), "이미 NONE 이면 그대로 성공");
        assertEquals(200, respond(item, Map.of("response", "PASS", "passReason", "JOB_MISMATCH")).status());
    }

    @Test
    void applyCancelDeletesDraftAndReapplyCreatesNewApplication() throws Exception {
        long item = item();
        Reply applied = respond(item, Map.of("response", "APPLY"));
        assertEquals(200, applied.status());
        long first = applied.body().path("data").path("applicationId").asLong();
        jdbc.update("INSERT INTO application_document(application_id, document) VALUES (?, '{\"sections\":[]}') ON DUPLICATE KEY UPDATE document = document", first);
        jdbc.update("UPDATE application SET status = 'DRAFT' WHERE id = ?", first);

        assertEquals(200, cancel(item, "APPLY").status());
        assertEquals(0, count("SELECT COUNT(*) FROM application WHERE id = ?", first));
        assertEquals(0, count("SELECT COUNT(*) FROM application_document WHERE application_id = ?", first));
        assertEquals("NONE", response(item));

        Reply again = respond(item, Map.of("response", "APPLY"));
        assertEquals(200, again.status());
        assertNotEquals(first, again.body().path("data").path("applicationId").asLong());
    }

    @Test
    void submittedApplicationAndContactedHelpAre409AndKeepEverything() throws Exception {
        long applyItem = item();
        long application = respond(applyItem, Map.of("response", "APPLY")).body().path("data").path("applicationId").asLong();
        jdbc.update("UPDATE application SET status = 'SUBMITTED', submitted_at = NOW() WHERE id = ?", application);
        assertError(cancel(applyItem, "APPLY"), 409, "INVALID_STATUS");
        assertEquals("APPLY", response(applyItem));
        assertEquals("SUBMITTED", jdbc.queryForObject("SELECT status FROM application WHERE id = ?", String.class, application));

        long helpItem = item();
        long help = send("POST", "/api/help-requests", Map.of("studentId", studentId, "recommendationItemId", helpItem))
                .body().path("data").path("helpRequestId").asLong();
        assertEquals(200, send("PATCH", "/api/coordinator/help-requests/" + help, Map.of("status", "CONTACTED")).status());
        assertError(cancel(helpItem, "HELP"), 409, "INVALID_STATUS");
        assertEquals("HELP", response(helpItem));
        assertEquals("CONTACTED", jdbc.queryForObject("SELECT status FROM help_request WHERE id = ?", String.class, help));
    }

    @Test
    void helpCancelClosesOpenRequestKeepsHistoryAndAllowsNewRequest() throws Exception {
        long item = item();
        long help = send("POST", "/api/help-requests", Map.of("studentId", studentId, "recommendationItemId", item))
                .body().path("data").path("helpRequestId").asLong();

        assertEquals(200, cancel(item, "HELP").status());
        Map<String, Object> row = jdbc.queryForMap("SELECT status, close_reason, closed_at FROM help_request WHERE id = ?", help);
        assertEquals("CLOSED", row.get("status"));
        assertEquals("NO_LONGER_NEEDED", row.get("close_reason"));
        assertNotNull(row.get("closed_at"));
        assertEquals("NONE", response(item));

        Reply again = send("POST", "/api/help-requests", Map.of("studentId", studentId, "recommendationItemId", item));
        assertEquals(200, again.status());
        assertNotEquals(help, again.body().path("data").path("helpRequestId").asLong());
        assertEquals(200, cancel(item, "HELP").status(), "가장 최근 요청만 닫는다");
        assertEquals(2, count("SELECT COUNT(*) FROM help_request WHERE student_id = ? AND status = 'CLOSED'", studentId));
    }

    @Test
    void validationErrorsUseCommonJson() throws Exception {
        long item = item();
        assertEquals(200, respond(item, Map.of("response", "PASS", "passReason", "OTHER")).status());
        assertError(cancel(item, "APPLY"), 409, "INVALID_STATUS");
        assertError(send("DELETE", "/api/recommendation-items/" + item + "/response", null), 400, "INVALID_REQUEST");
        assertError(cancel(item, "NONE"), 400, "INVALID_REQUEST");
        assertError(cancel(Long.MAX_VALUE, "PASS"), 404, "RECOMMENDATION_ITEM_NOT_FOUND");
        assertEquals("PASS", response(item));
    }

    @Test
    void concurrentCancelAndSubmitHaveExactlyOneWinner() throws Exception {
        long item = item();
        long application = respond(item, Map.of("response", "APPLY")).body().path("data").path("applicationId").asLong();
        jdbc.update("INSERT INTO application_document(application_id, document) VALUES (?, '{\"sections\":[]}') ON DUPLICATE KEY UPDATE document = document", application);
        jdbc.update("UPDATE application SET status = 'DRAFT' WHERE id = ?", application);
        CountDownLatch gate = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var cancelling = executor.submit(() -> {
                assertTrue(gate.await(5, TimeUnit.SECONDS));
                return cancel(item, "APPLY");
            });
            var submitting = executor.submit(() -> {
                assertTrue(gate.await(5, TimeUnit.SECONDS));
                return send("POST", "/api/applications/" + application + "/submit", null);
            });
            gate.countDown();
            Reply a = cancelling.get(15, TimeUnit.SECONDS);
            Reply b = submitting.get(15, TimeUnit.SECONDS);
            assertEquals(List.of(200, 409), Stream.of(a.status(), b.status()).sorted().toList());
            if (a.status() == 200) {
                assertEquals("NONE", response(item));
                assertEquals(0, count("SELECT COUNT(*) FROM application WHERE id = ?", application));
            } else {
                assertEquals("APPLY", response(item));
                assertEquals("SUBMITTED", jdbc.queryForObject("SELECT status FROM application WHERE id = ?", String.class, application));
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    private long item() {
        long recommendation = insert("INSERT INTO recommendation(student_id,trigger_type) VALUES (?,'VISIT')", studentId);
        int rank = count("SELECT COUNT(*) FROM recommendation_item WHERE recommendation_id = ?", recommendation) + 1;
        return insert("INSERT INTO recommendation_item(recommendation_id,job_posting_id,rank_no,reasons) VALUES (?,?,?,'[]')",
                recommendation, jobId, rank);
    }

    private Reply respond(long item, Map<String, Object> body) throws Exception {
        return send("POST", "/api/recommendation-items/" + item + "/response", body);
    }

    private Reply cancel(long item, String expected) throws Exception {
        return send("DELETE", "/api/recommendation-items/" + item + "/response?expectedResponse=" + expected, null);
    }

    private Reply send(String method, String path, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(12)).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Reply(response.statusCode(), json.readTree(response.body()));
    }

    private String response(long item) {
        return jdbc.queryForObject("SELECT response FROM recommendation_item WHERE id = ?", String.class, item);
    }

    private String status() {
        return jdbc.query("SELECT status FROM student_status WHERE student_id = ?",
                rs -> rs.next() ? rs.getString(1) : null, studentId);
    }

    private LocalDateTime lastResponse() {
        return jdbc.queryForObject("SELECT last_response_at FROM student WHERE id = ?", LocalDateTime.class, studentId);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private static void assertError(Reply response, int status, String code) {
        assertEquals(status, response.status());
        assertEquals(code, response.body().path("error").path("code").asText());
        assertFalse(response.body().has("data"));
    }

    private long insert(String sql, Object... values) {
        var keys = new GeneratedKeyHolder();
        assertEquals(1, jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, keys));
        assertNotNull(keys.getKey());
        return keys.getKey().longValue();
    }

    private Map<String, Long> counts() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String table : TABLES) result.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
        return result;
    }

    private record Reply(int status, JsonNode body) {}
}
