// 주인: BE-B
package com.ieum.coordinator;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

/** 실제 HTTP · MySQL 로 상태별 목록을 검증한다. 독립 모의 자료만 만들고 지운다. 다른 행은 결과에서 걸러 본다. */
@EnabledIfEnvironmentVariable(named = "IEUM_COORDINATOR_LIST_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]",
        "spring.sql.init.mode=never", "app.ai.mock=true",
        "app.seoul.sync-cron=-", "app.status.recalculate-cron=-"
})
class CoordinatorListDatabaseTest {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    private static final List<String> TABLES = List.of("course", "student", "recommendation",
            "recommendation_item", "application", "help_request", "job_posting");
    private static final String TAG = "상태별 목록 검증";
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    private Map<String, Long> countsBefore;
    private Long courseId;
    private Long studentId;
    private Long jobId;
    private final Map<String, Long> applications = new LinkedHashMap<>();
    private final Map<String, Long> helps = new LinkedHashMap<>();

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
                VALUES ('EVAL_SEED',?, '모의 기업','모의 공고', '2026-11-06', 1, 1)
                """, "list-test-" + System.nanoTime());
        long recommendation = insert("INSERT INTO recommendation(student_id,trigger_type) VALUES (?,'VISIT')", studentId);
        String[] statuses = {"DRAFT", "SUBMITTED", "REJECTED", "APPROVED", "DELIVERED"};
        for (int i = 0; i < statuses.length; i++) {
            long item = insert("""
                    INSERT INTO recommendation_item(recommendation_id,job_posting_id,rank_no,reasons,response)
                    VALUES (?,?,?,'[]','APPLY')
                    """, recommendation, jobId, i + 1);
            LocalDateTime submitted = "DRAFT".equals(statuses[i]) ? null : LocalDateTime.of(2026, 11, 2, 9, i);
            applications.put(statuses[i], insert("""
                    INSERT INTO application(student_id,recommendation_item_id,job_posting_id,status,submitted_at)
                    VALUES (?,?,?,?,?)
                    """, studentId, item, jobId, statuses[i], submitted));
        }
        helps.put("OPEN", insert("INSERT INTO help_request(student_id,help_type,status,created_at) VALUES (?,'OTHER','OPEN',?)",
                studentId, LocalDateTime.of(2026, 11, 2, 9, 0)));
        helps.put("CONTACTED", insert("INSERT INTO help_request(student_id,status,created_at) VALUES (?,'CONTACTED',?)",
                studentId, LocalDateTime.of(2026, 11, 2, 10, 0)));
        helps.put("CLOSED", insert("""
                INSERT INTO help_request(student_id,help_type,status,created_at,closed_at,close_reason)
                VALUES (?,'CAREER_COUNSELING','CLOSED',?,?,'EMPLOYED')
                """, studentId, LocalDateTime.of(2026, 11, 2, 11, 0), LocalDateTime.of(2026, 11, 2, 12, 0)));
    }

    @AfterEach
    void removeOnlyOwnFixture() {
        if (studentId != null) {
            jdbc.update("DELETE FROM help_request WHERE student_id = ?", studentId);
            jdbc.update("DELETE FROM application WHERE student_id = ?", studentId);
            jdbc.update("DELETE FROM recommendation WHERE student_id = ?", studentId);
            jdbc.update("DELETE FROM student WHERE id = ?", studentId);
        }
        if (jobId != null) jdbc.update("DELETE FROM job_posting WHERE id = ?", jobId);
        if (courseId != null) jdbc.update("DELETE FROM course WHERE id = ?", courseId);
        if (countsBefore != null) assertEquals(countsBefore, counts(), "기존 행 수를 보존해야 한다");
    }

    @Test
    void applicationListFiltersReviewStagesAndCarriesDeadline() throws Exception {
        List<JsonNode> all = own(get("/api/coordinator/applications"), "applicationId", applications.values());
        assertEquals(List.of("SUBMITTED", "REJECTED", "APPROVED", "DELIVERED"),
                all.stream().map(n -> n.path("status").asText()).toList(), "작성 중 서류는 빠지고 제출 순");
        assertEquals(own(get("/api/coordinator/applications?status=ALL"), "applicationId", applications.values()), all);
        JsonNode first = all.get(0);
        assertEquals(Set.of("applicationId", "studentId", "studentName", "jobTitle", "status", "submittedAt",
                "reviewedAt", "deliveredAt", "deadlineOn", "untilFilled"), keys(first));
        assertEquals("모의 기업 모의 공고", first.path("jobTitle").asText());
        assertEquals("2026-11-06", first.path("deadlineOn").asText());
        assertTrue(first.path("untilFilled").asBoolean());
        assertTrue(first.path("submittedAt").asText().endsWith("+09:00"));
        assertTrue(first.get("reviewedAt").isNull());

        for (String status : List.of("SUBMITTED", "REJECTED", "APPROVED", "DELIVERED")) {
            List<JsonNode> one = own(get("/api/coordinator/applications?status=" + status), "applicationId", applications.values());
            assertEquals(1, one.size(), status);
            assertEquals(applications.get(status), one.get(0).path("applicationId").asLong());
        }
        assertError(get("/api/coordinator/applications?status=DRAFT"), 400, "INVALID_REQUEST");

        JsonNode queue = own(get("/api/coordinator/dashboard").path("reviewQueue"), "applicationId",
                applications.values()).get(0);
        assertEquals("2026-11-06", queue.path("deadlineOn").asText());
        assertTrue(queue.path("untilFilled").asBoolean());
    }

    @Test
    void helpRequestListFiltersByStatusAndKeepsCloseInfo() throws Exception {
        List<JsonNode> all = own(get("/api/coordinator/help-requests"), "helpRequestId", helps.values());
        assertEquals(List.of("CLOSED", "CONTACTED", "OPEN"), all.stream().map(n -> n.path("status").asText()).toList(),
                "최근 순");
        JsonNode closed = all.get(0);
        assertEquals(Set.of("helpRequestId", "studentId", "studentName", "courseLabel", "createdAt", "jobTitle",
                "contactMethod", "memo", "helpType", "status", "closedAt", "closeReason", "consultationAt"), keys(closed));
        assertEquals("EMPLOYED", closed.path("closeReason").asText());
        assertTrue(closed.path("closedAt").asText().endsWith("+09:00"));
        assertTrue(closed.get("jobTitle").isNull());
        assertTrue(all.get(1).get("helpType").isNull(), "미분류는 null");

        for (String status : List.of("OPEN", "CONTACTED", "CLOSED")) {
            List<JsonNode> one = own(get("/api/coordinator/help-requests?status=" + status), "helpRequestId", helps.values());
            assertEquals(1, one.size(), status);
            assertEquals(helps.get(status), one.get(0).path("helpRequestId").asLong());
        }
        assertEquals(3, own(get("/api/coordinator/help-requests?status=ALL"), "helpRequestId", helps.values()).size());
        assertError(get("/api/coordinator/help-requests?status=NEED_HELP"), 400, "INVALID_REQUEST");
    }

    private JsonNode get(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(12)).GET().build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ObjectMapper reader = json;
        JsonNode body = reader.readTree(response.body());
        return response.statusCode() == 200 ? body.path("data") : wrap(response.statusCode(), body);
    }

    private JsonNode wrap(int status, JsonNode body) {
        var node = json.createObjectNode();
        node.put("_status", status);
        node.set("_body", body);
        return node;
    }

    private static List<JsonNode> own(JsonNode array, String idField, java.util.Collection<Long> ids) {
        List<JsonNode> result = new ArrayList<>();
        array.forEach(n -> { if (ids.contains(n.path(idField).asLong())) result.add(n); });
        return result;
    }

    private static Set<String> keys(JsonNode node) {
        Set<String> result = new HashSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private static void assertError(JsonNode reply, int status, String code) {
        assertEquals(status, reply.path("_status").asInt());
        assertEquals(code, reply.path("_body").path("error").path("code").asText());
        assertFalse(reply.path("_body").has("data"));
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
