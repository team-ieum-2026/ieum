// 주인: BE-B
package com.ieum.coordinator;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

/** 자기 모의 수료생만 만들고 정리한다. 패스 응답은 실제 HTTP로 저장하며 AI를 호출하지 않는다. */
@EnabledIfEnvironmentVariable(named = "IEUM_PASS_FOLLOW_UP_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]",
        "spring.sql.init.mode=never", "app.ai.mock=true", "app.seoul.sync-cron=-", "app.status.recalculate-cron=-"
})
class CoordinatorPassFollowUpDatabaseTest {
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    private Long courseId, studentId, jobId;

    @BeforeEach
    void fixture() {
        jobId = jdbc.queryForObject("SELECT MIN(id) FROM job_posting", Long.class);
        courseId = insert("INSERT INTO course(name,cohort,campus,started_on,ended_on) "
                + "VALUES ('연속 패스 검증 과정','검증','모의 캠퍼스','2026-03-01','2026-08-31')");
        studentId = insert("INSERT INTO student(course_id,name,completed_on,last_response_at) "
                + "VALUES (?,'연속 패스 검증 수료생','2026-08-31',NOW(6))", courseId);
    }

    @AfterEach
    void cleanup() {
        if (studentId != null) {
            jdbc.update("DELETE FROM recommendation WHERE student_id=?", studentId);
            jdbc.update("DELETE FROM contact_log WHERE student_id=?", studentId);
            jdbc.update("DELETE FROM student WHERE id=?", studentId);
        }
        if (courseId != null) jdbc.update("DELETE FROM course WHERE id=?", courseId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMPLOYED", "RESTING", "SEEKING"})
    void threeOrdinaryPassesCountAfterTheLatestChoiceAndContactStartsANewStreak(String state) throws Exception {
        request("PUT", "/api/students/" + studentId + "/status", Map.of("status", state));
        pass("JOB_MISMATCH"); pass("CONDITION_MISMATCH");
        assertFlag(2, false);
        long thirdItem = pass("SKILL_GAP");
        assertFlag(3, true);
        assertEquals(state, request("GET", "/api/students/" + studentId + "/status", null).path("status").asText());
        JsonNode dashboard = request("GET", "/api/coordinator/dashboard", null);
        assertTrue(containsStudent(dashboard.path("passFollowUpList")));
        assertFalse(containsStudent(dashboard.path("helpRequests")), "연속 패스가 도움 요청을 자동 생성하면 안 된다");

        var duplicate = raw("POST", "/api/recommendation-items/" + thirdItem + "/response",
                Map.of("response", "PASS", "passReason", "SKILL_GAP"));
        assertEquals(409, duplicate.statusCode());
        assertFlag(3, true);

        request("POST", "/api/coordinator/students/" + studentId + "/contact-logs",
                Map.of("contactType", "PHONE", "note", "모의 연속 패스 사유 확인"));
        assertFlag(0, false);
        assertEquals(state, request("GET", "/api/students/" + studentId + "/status", null).path("status").asText());
        pass("OTHER"); pass("JOB_MISMATCH"); assertFlag(2, false);
        pass("CONDITION_MISMATCH"); assertFlag(3, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALREADY_EMPLOYED", "TAKING_BREAK"})
    void employmentOrRestPassResetsTheStreakRatherThanBeingSkipped(String reason) throws Exception {
        pass("OTHER"); pass("JOB_MISMATCH");
        assertFlag(2, false);
        long help = request("POST", "/api/help-requests",
                Map.of("studentId", studentId, "helpType", "CAREER_COUNSELING")).path("helpRequestId").asLong();
        pass(reason);
        assertFlag(0, false);
        String expected = "ALREADY_EMPLOYED".equals(reason) ? "EMPLOYED" : "RESTING";
        assertEquals(expected, request("GET", "/api/students/" + studentId + "/status", null).path("status").asText());
        pass("OTHER"); assertFlag(1, false);
        pass("CONDITION_MISMATCH"); assertFlag(2, false);
        pass("SKILL_GAP"); assertFlag(3, true);
        pass(reason); assertFlag(0, false);
        JsonNode open = request("GET", "/api/help-requests?studentId=" + studentId, null);
        assertEquals(1, open.size());
        assertEquals(help, open.get(0).path("helpRequestId").asLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMPLOYED", "RESTING"})
    void aDirectEmploymentOrRestChoiceAlsoResetsPassesAndPreservesOpenHelp(String selected) throws Exception {
        pass("OTHER"); pass("JOB_MISMATCH"); pass("CONDITION_MISMATCH"); assertFlag(3, true);
        long help = request("POST", "/api/help-requests",
                Map.of("studentId", studentId, "helpType", "INTERVIEW_PREPARATION")).path("helpRequestId").asLong();
        request("PUT", "/api/students/" + studentId + "/status", Map.of("status", selected));
        assertFlag(0, false);
        assertEquals(selected, request("GET", "/api/students/" + studentId + "/status", null).path("status").asText());
        JsonNode open = request("GET", "/api/help-requests?studentId=" + studentId, null);
        assertEquals(1, open.size());
        assertEquals(help, open.get(0).path("helpRequestId").asLong());
        pass("OTHER"); assertFlag(1, false);
        pass("SKILL_GAP"); assertFlag(2, false);
        pass("JOB_MISMATCH"); assertFlag(3, true);
    }

    @Test
    void seekingChoiceDoesNotClearOrdinaryPasses() throws Exception {
        pass("OTHER"); pass("JOB_MISMATCH");
        request("PUT", "/api/students/" + studentId + "/status", Map.of("status", "SEEKING"));
        assertFlag(2, false);
        pass("CONDITION_MISMATCH"); assertFlag(3, true);
    }

    @Test
    void equalTimestampEmploymentChoiceResetsEarlierPassesAndCountsOnlyNewerResponses() throws Exception {
        var at = LocalDateTime.of(2026, 11, 2, 12, 0, 0);
        item("PASS", at); item("PASS", at); item("PASS", at); assertFlag(3, true);
        insert("INSERT INTO student_intent_history(student_id,status,selected_at) VALUES (?,'EMPLOYED',?)", studentId, at);
        assertFlag(0, false);
        item("PASS", at.plusNanos(1_000)); assertFlag(1, false);
        item("PASS", at.plusNanos(2_000)); assertFlag(2, false);
        item("PASS", at.plusNanos(3_000)); assertFlag(3, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPLY", "HELP"})
    void aDifferentResponseBreaksTheConsecutivePassSequence(String response) throws Exception {
        pass("OTHER"); pass("OTHER"); pass("OTHER"); assertFlag(3, true);
        // 응답 판정만 검증하므로 AI 생성·도움 요청 생성 없이 커밋된 원 응답을 준비한다.
        item(response, LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")));
        assertFlag(0, false);
        pass("JOB_MISMATCH"); pass("OTHER"); assertFlag(2, false);
    }

    @Test
    void responseTimesItemIdTiesAndSubsecondContactOrderAreRespected() throws Exception {
        var at = LocalDateTime.of(2026, 11, 2, 12, 0, 0);
        item("PASS", at.plusNanos(300_000));
        item("PASS", at.plusNanos(200_000));
        item("HELP", at.plusNanos(100_000));
        item("PASS", at.plusNanos(400_000));
        item("NONE", null);
        assertFlag(3, true);
        item("HELP", at.plusNanos(400_000));
        assertFlag(0, false);
        item("PASS", at.plusNanos(400_000));
        item("PASS", at.plusNanos(400_000));
        item("PASS", at.plusNanos(400_000));
        assertFlag(3, true);
        insert("INSERT INTO contact_log(student_id,contact_type,note,created_at) VALUES (?,'PHONE','모의 접촉',?)",
                studentId, at.plusNanos(500_000));
        assertFlag(0, false);
        item("PASS", at.plusNanos(600_000));
        item("PASS", at.plusNanos(700_000));
        item("PASS", at.plusNanos(800_000));
        assertFlag(3, true);
    }

    private void assertFlag(long count, boolean expected) throws Exception {
        JsonNode detail = request("GET", "/api/coordinator/students/" + studentId, null).path("student");
        assertEquals(count, detail.path("consecutivePassCount").asLong());
        assertEquals(expected, detail.path("needsPassFollowUp").asBoolean());
        JsonNode list = request("GET", "/api/coordinator/students?q=연속%20패스%20검증%20수료생", null).path("items");
        assertEquals(1, list.size());
        assertEquals(count, list.get(0).path("consecutivePassCount").asLong());
        assertEquals(expected, containsStudent(request("GET", "/api/coordinator/pass-follow-ups", null)));
    }

    private boolean containsStudent(JsonNode items) {
        for (JsonNode row : items) if (row.path("studentId").asLong() == studentId) return true;
        return false;
    }

    private long pass(String reason) throws Exception {
        long item = item("NONE", null);
        request("POST", "/api/recommendation-items/" + item + "/response",
                Map.of("response", "PASS", "passReason", reason));
        return item;
    }

    private long item(String response, LocalDateTime respondedAt) {
        long rec = insert("INSERT INTO recommendation(student_id,trigger_type) VALUES (?,'VISIT')", studentId);
        return insert("INSERT INTO recommendation_item(recommendation_id,job_posting_id,rank_no,reasons,response,responded_at) "
                + "VALUES (?,?,1,'[]',?,?)", rec, jobId, response, respondedAt);
    }

    private HttpResponse<String> raw(String method, String path, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if (body != null) builder.header("Content-Type", "application/json");
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode request(String method, String path, Object body) throws Exception {
        var response = raw(method, path, body);
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body()).path("data");
    }

    private long insert(String sql, Object... args) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i=0; i<args.length; i++) statement.setObject(i+1, args[i]);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }
}
