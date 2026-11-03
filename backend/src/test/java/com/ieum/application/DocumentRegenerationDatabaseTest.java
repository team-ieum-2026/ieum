// 주인: BE-A
package com.ieum.application;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

/** 실제 HTTP · MySQL 로 실패 서류 다시 생성과 중단된 생성 복구를 검증한다. AI 는 고정 응답. 독립 모의 자료만 만들고 지운다. */
@EnabledIfEnvironmentVariable(named = "IEUM_DOCUMENT_REGENERATE_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]",
        "spring.sql.init.mode=never", "app.ai.mock=true",
        "app.seoul.sync-cron=-", "app.status.recalculate-cron=-", "app.document.recover-delay-ms=3600000"
})
class DocumentRegenerationDatabaseTest {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    private static final List<String> TABLES = List.of("course", "student", "recommendation",
            "recommendation_item", "application", "application_document", "job_posting");
    private static final String TAG = "서류 다시 생성 검증";
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private DocumentGenerationRecovery recovery;
    private Map<String, Long> countsBefore;
    private Long courseId;
    private Long studentId;
    private Long jobId;
    private Long recommendationId;
    private int rank;

    @BeforeEach
    void createIndependentMockFixture() {
        countsBefore = counts();
        courseId = insert("""
                INSERT INTO course(name,cohort,campus,started_on,ended_on)
                VALUES (?,'검증','모의 캠퍼스','2026-03-01','2026-08-31')
                """, TAG);
        studentId = insert("INSERT INTO student(course_id,name,completed_on) VALUES (?,?,?)",
                courseId, TAG, LocalDate.of(2026, 8, 31));
        jobId = insert("""
                INSERT INTO job_posting(source,external_id,company_name,title,deadline_on,until_filled,is_active)
                VALUES ('EVAL_SEED',?, '모의 기업','모의 공고', '2026-11-30', 0, 1)
                """, "regenerate-test-" + System.nanoTime());
        recommendationId = insert("INSERT INTO recommendation(student_id,trigger_type) VALUES (?,'VISIT')", studentId);
    }

    @AfterEach
    void removeOnlyOwnFixture() {
        if (studentId != null) {
            jdbc.update("DELETE FROM application WHERE student_id = ?", studentId);
            jdbc.update("DELETE FROM recommendation WHERE student_id = ?", studentId);
            jdbc.update("DELETE FROM student WHERE id = ?", studentId);
        }
        if (jobId != null) jdbc.update("DELETE FROM job_posting WHERE id = ?", jobId);
        if (courseId != null) jdbc.update("DELETE FROM course WHERE id = ?", courseId);
        if (countsBefore != null) assertEquals(countsBefore, counts(), "기존 행 수를 보존해야 한다");
    }

    @Test
    void failedDocumentIsRegeneratedToDraftOnlyOnce() throws Exception {
        long id = application("FAILED");

        List<Future<JsonNode>> replies = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Callable<JsonNode> call = () -> post("/api/applications/" + id + "/regenerate");
            for (int i = 0; i < 4; i++) replies.add(pool.submit(call));
            int ok = 0;
            for (Future<JsonNode> reply : replies) {
                JsonNode body = reply.get();
                if (body.has("_status")) {
                    assertError(body, 409, "INVALID_STATUS");
                } else {
                    ok++;
                    assertEquals(id, body.path("applicationId").asLong());
                    assertEquals("GENERATING", body.path("status").asText());
                }
            }
            assertEquals(1, ok, "동시에 눌러도 한 번만 시작");
        } finally {
            pool.shutdownNow();
        }

        String status = null;
        for (int i = 0; i < 100; i++) {
            status = jdbc.queryForObject("SELECT status FROM application WHERE id = ?", String.class, id);
            if (!"GENERATING".equals(status)) break;
            Thread.sleep(100);
        }
        assertEquals("DRAFT", status);
        JsonNode detail = get("/api/applications/" + id);
        assertTrue(detail.path("document").path("sections").isArray(), "다시 만든 서류를 조회할 수 있다");

        assertError(post("/api/applications/" + id + "/regenerate"), 409, "INVALID_STATUS");
        assertError(post("/api/applications/999999999/regenerate"), 404, "APPLICATION_NOT_FOUND");
    }

    @Test
    void generatingIsConflictWithMessage() throws Exception {
        long id = application("GENERATING");

        JsonNode reply = post("/api/applications/" + id + "/regenerate");

        assertError(reply, 409, "INVALID_STATUS");
        assertEquals("이미 서류를 만들고 있어요.", reply.path("_body").path("error").path("message").asText());
        assertEquals("GENERATING", jdbc.queryForObject("SELECT status FROM application WHERE id = ?", String.class, id));
    }

    @Test
    void interruptedGenerationBecomesFailedAndCanBeRegenerated() throws Exception {
        long stale = application("GENERATING");
        long fresh = application("GENERATING");
        jdbc.update("UPDATE application SET updated_at = NOW() - INTERVAL 10 MINUTE WHERE id = ?", stale);

        recovery.recover();

        assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM application WHERE id = ?", String.class, stale));
        assertEquals("GENERATING", jdbc.queryForObject("SELECT status FROM application WHERE id = ?", String.class, fresh),
                "5분 안의 생성은 건드리지 않는다");
        JsonNode reply = post("/api/applications/" + stale + "/regenerate");
        assertEquals("GENERATING", reply.path("status").asText());
    }

    private long application(String status) {
        long item = insert("""
                INSERT INTO recommendation_item(recommendation_id,job_posting_id,rank_no,reasons,response)
                VALUES (?,?,?,'[]','APPLY')
                """, recommendationId, jobId, ++rank);
        return insert("INSERT INTO application(student_id,recommendation_item_id,job_posting_id,status) VALUES (?,?,?,?)",
                studentId, item, jobId, status);
    }

    private JsonNode get(String path) throws Exception {
        return send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(12)).GET().build());
    }

    private JsonNode post(String path) throws Exception {
        return send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(12)).POST(HttpRequest.BodyPublishers.noBody()).build());
    }

    private JsonNode send(HttpRequest request) throws Exception {
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode body = json.readTree(response.body());
        if (response.statusCode() == 200) return body.path("data");
        var node = json.createObjectNode();
        node.put("_status", response.statusCode());
        node.set("_body", body);
        return node;
    }

    private static void assertError(JsonNode reply, int status, String code) {
        assertEquals(status, reply.path("_status").asInt());
        assertEquals(code, reply.path("_body").path("error").path("code").asText());
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
}
