// 주인: BE-B
package com.ieum.helprequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 실제 HTTP·MySQL·커밋 후 상태 갱신을 검증한다. 독립 모의 자료만 만들고 삭제한다. */
@EnabledIfEnvironmentVariable(named = "IEUM_HELP_REQUEST_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]",
        "spring.sql.init.mode=never", "app.ai.mock=true",
        "app.seoul.sync-cron=-", "app.status.recalculate-cron=-"
})
class HelpRequestDatabaseTest {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    private static final List<String> TABLES = List.of("course", "student", "recommendation",
            "recommendation_item", "help_request", "student_status");
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @MockitoSpyBean private HelpRequestMapper mapper;
    private Map<String, Long> countsBefore;
    private Long courseId;
    private Long studentId;
    private Long otherStudentId;
    private Long jobId;

    @BeforeEach
    void createIndependentMockFixture() {
        List<String> engines = jdbc.queryForList("""
                SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME IN ('course','student','recommendation','recommendation_item',
                                   'help_request','student_status')
                """, String.class);
        assertEquals(6, engines.size());
        assertTrue(engines.stream().allMatch("InnoDB"::equalsIgnoreCase));
        jobId = jdbc.queryForObject("SELECT MIN(id) FROM job_posting", Long.class);
        assertNotNull(jobId, "외래 키로 참조할 기존 모의 공고가 한 건 필요하다");
        countsBefore = counts();
        courseId = insert("""
                INSERT INTO course(name,cohort,campus,started_on,ended_on)
                VALUES ('도움 요청 검증 과정','검증','모의 캠퍼스','2026-03-01','2026-08-31')
                """);
        studentId = student("도움 요청 검증 A");
        otherStudentId = student("도움 요청 검증 B");
    }

    @AfterEach
    void removeOnlyOwnFixture() {
        for (Long id : new Long[]{studentId, otherStudentId}) {
            if (id == null) continue;
            jdbc.update("DELETE FROM help_request WHERE student_id = ?", id);
            jdbc.update("DELETE FROM recommendation WHERE student_id = ?", id);
            jdbc.update("DELETE FROM student WHERE id = ?", id);
        }
        if (courseId != null) jdbc.update("DELETE FROM course WHERE id = ?", courseId);
        if (countsBefore != null) assertEquals(countsBefore, counts(), "기존 행 수를 보존해야 한다");
    }

    @Test
    void realHttpFlowCommitsHelpAndUpdatesStatusWithoutAiOrChangingCoordinatorResponseTime() throws Exception {
        long item = item();
        LocalDateTime original = lastResponse();
        Reply created = send("POST", "/api/help-requests", createBody(item));
        assertEquals(200, created.status());
        assertEquals(2, created.body().path("data").size());
        long help = created.body().path("data").path("helpRequestId").asLong();
        assertTrue(help > 0);
        assertEquals("HELP", jdbc.queryForObject(
                "SELECT response FROM recommendation_item WHERE id = ?", String.class, item));
        assertTrue(lastResponse().isAfter(original));
        LocalDateTime responseAt = lastResponse();
        assertStatus("NEED_HELP");

        Reply details = send("PATCH", "/api/help-requests/" + help,
                Map.of("contactMethod", "SMS", "memo", "모의 지원서 상담 요청"));
        assertEquals(200, details.status());
        assertEquals("SMS", details.body().path("data").path("contactMethod").asText());
        Reply cleared = send("PATCH", "/api/help-requests/" + help, "{\"memo\":null}");
        assertEquals(200, cleared.status());
        assertEquals(4, cleared.body().path("data").size());
        assertTrue(cleared.body().path("data").get("memo").isNull());
        assertEquals("SMS", cleared.body().path("data").path("contactMethod").asText());
        assertEquals(responseAt, lastResponse());
        assertStatus("NEED_HELP");

        assertEquals(200, changeStatus(help, "CONTACTED").status());
        assertStatus("SEEKING");
        assertEquals("WAITING", jdbc.queryForObject(
                "SELECT sub_status FROM student_status WHERE student_id = ?", String.class, studentId));
        assertEquals(responseAt, lastResponse());
        assertNull(jdbc.queryForObject("SELECT closed_at FROM help_request WHERE id = ?",
                LocalDateTime.class, help));
        assertEquals(200, changeStatus(help, "CONTACTED").status());
        assertEquals(200, changeStatus(help, "OPEN").status());
        assertStatus("NEED_HELP");
        assertEquals(responseAt, lastResponse());

        JsonNode api = send("GET", "/v3/api-docs", null).body();
        assertTrue(api.path("paths").has("/api/help-requests"));
        assertTrue(api.path("paths").has("/api/help-requests/{helpRequestId}"));
        assertTrue(api.path("paths").has("/api/coordinator/help-requests/{helpRequestId}"));
        JsonNode properties = api.path("components").path("schemas")
                .path("HelpRequestUpdateRequest").path("properties");
        var schemaFile = java.nio.file.Path.of("build", "help-request-contract", "patch-schema.json");
        java.nio.file.Files.createDirectories(schemaFile.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(schemaFile.toFile(), properties);
        assertNullableString(properties.path("contactMethod"));
        assertNullableString(properties.path("memo"));
        assertFalse(properties.has("valid"));
    }

    @Test
    void oneRemainingOpenHelpKeepsNeedHelpAndNewerStudentTimestampIsPreserved() throws Exception {
        LocalDateTime future = LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(2)
                .truncatedTo(ChronoUnit.SECONDS);
        jdbc.update("UPDATE student SET last_response_at = ? WHERE id = ?", future, studentId);
        Reply first = send("POST", "/api/help-requests", createBody(item()));
        Reply second = send("POST", "/api/help-requests", createBody(item()));
        assertEquals(200, first.status());
        assertEquals(200, second.status());
        long firstId = first.body().path("data").path("helpRequestId").asLong();
        long secondId = second.body().path("data").path("helpRequestId").asLong();
        assertEquals(200, changeStatus(firstId, "CONTACTED").status());
        assertStatus("NEED_HELP");
        assertEquals(200, changeStatus(secondId, "CONTACTED").status());
        assertStatus("SEEKING");
        assertEquals(future, lastResponse());
    }

    @Test
    void ownershipMissingRowsAndDuplicateRequestsUseExistingErrorsWithoutExtraWrites() throws Exception {
        long item = item();
        LocalDateTime before = lastResponse();
        Reply wrongOwner = send("POST", "/api/help-requests",
                Map.of("studentId", otherStudentId, "recommendationItemId", item));
        assertError(wrongOwner, 400, "INVALID_REQUEST");
        assertEquals(before, lastResponse());
        assertEquals(0, helpCount());
        assertError(send("POST", "/api/help-requests",
                Map.of("studentId", Long.MAX_VALUE, "recommendationItemId", item)), 404, "STUDENT_NOT_FOUND");
        assertError(send("POST", "/api/help-requests", createBody(Long.MAX_VALUE)),
                404, "RECOMMENDATION_ITEM_NOT_FOUND");
        assertError(send("PATCH", "/api/help-requests/" + Long.MAX_VALUE, "{\"memo\":null}"),
                404, "NOT_FOUND");
        assertEquals(200, send("POST", "/api/help-requests", createBody(item)).status());
        assertError(send("POST", "/api/help-requests", createBody(item)), 409, "ALREADY_RESPONDED");
        assertEquals(1, helpCount());
    }

    @Test
    void failingHelpInsertRollsBackItemAndStudentWrites() throws Exception {
        long item = item();
        LocalDateTime before = lastResponse();
        doThrow(new IllegalStateException("모의 도움 요청 저장 실패")).when(mapper).insert(any());

        assertError(send("POST", "/api/help-requests", createBody(item)), 500, "INTERNAL_ERROR");

        assertEquals("NONE", jdbc.queryForObject(
                "SELECT response FROM recommendation_item WHERE id = ?", String.class, item));
        assertNull(jdbc.queryForObject("SELECT responded_at FROM recommendation_item WHERE id = ?",
                LocalDateTime.class, item));
        assertEquals(before, lastResponse());
        assertEquals(0, helpCount());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM student_status WHERE student_id = ?", Integer.class, studentId));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentDuplicateOrPassAndHelpHaveExactlyOneWinner(boolean competingPass) throws Exception {
        long item = item();
        CountDownLatch gate = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                assertTrue(gate.await(5, TimeUnit.SECONDS));
                return send("POST", "/api/help-requests", createBody(item));
            });
            var second = executor.submit(() -> {
                assertTrue(gate.await(5, TimeUnit.SECONDS));
                return competingPass ? send("POST", "/api/recommendation-items/" + item + "/response",
                        Map.of("response", "PASS", "passReason", "JOB_MISMATCH"))
                        : send("POST", "/api/help-requests", createBody(item));
            });
            gate.countDown();
            Reply a = first.get(15, TimeUnit.SECONDS);
            Reply b = second.get(15, TimeUnit.SECONDS);
            assertEquals(List.of(200, 409), java.util.stream.Stream.of(a.status(), b.status()).sorted().toList());
            assertError(a.status() == 409 ? a : b, 409, "ALREADY_RESPONDED");
            String response = jdbc.queryForObject(
                    "SELECT response FROM recommendation_item WHERE id = ?", String.class, item);
            assertEquals("HELP".equals(response) ? 1 : 0, helpCount());
            assertStatus("HELP".equals(response) ? "NEED_HELP" : "SEEKING");
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    private long student(String name) {
        return insert("""
                INSERT INTO student(course_id,name,completed_on,last_response_at) VALUES (?,?,?,?)
                """, courseId, name, java.time.LocalDate.of(2026, 8, 31),
                LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(2).truncatedTo(ChronoUnit.SECONDS));
    }

    private long item() {
        long recommendation = insert("INSERT INTO recommendation(student_id,trigger_type) VALUES (?,'VISIT')", studentId);
        return insert("""
                INSERT INTO recommendation_item(recommendation_id,job_posting_id,rank_no,reasons)
                VALUES (?,?,1,'[]')
                """, recommendation, jobId);
    }

    private Map<String, Object> createBody(long item) {
        return Map.of("studentId", studentId, "recommendationItemId", item);
    }

    private Reply changeStatus(long help, String status) throws Exception {
        return send("PATCH", "/api/coordinator/help-requests/" + help, Map.of("status", status));
    }

    private Reply send(String method, String path, Object body) throws Exception {
        String content = body instanceof String text ? text : body == null ? "" : json.writeValueAsString(body);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(12)).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(content, StandardCharsets.UTF_8)).build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Reply(response.statusCode(), json.readTree(response.body()));
    }

    private LocalDateTime lastResponse() {
        return jdbc.queryForObject("SELECT last_response_at FROM student WHERE id = ?", LocalDateTime.class, studentId);
    }

    private int helpCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM help_request WHERE student_id = ?", Integer.class, studentId);
    }

    private void assertStatus(String expected) {
        assertEquals(expected, jdbc.queryForObject(
                "SELECT status FROM student_status WHERE student_id = ?", String.class, studentId));
    }

    private static void assertError(Reply response, int status, String code) {
        assertEquals(status, response.status());
        assertEquals(code, response.body().path("error").path("code").asText());
        assertFalse(response.body().has("data"));
    }

    private static void assertNullableString(JsonNode property) {
        JsonNode type = property.path("type");
        if (type.isArray()) {
            var types = new java.util.HashSet<String>();
            type.forEach(value -> types.add(value.asText()));
            assertEquals(java.util.Set.of("string", "null"), types);
        } else {
            assertEquals("string", type.asText());
            assertTrue(property.path("nullable").asBoolean());
        }
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
