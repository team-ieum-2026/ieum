// 주인: BE-B
package com.ieum.status;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

/** 새 API를 독립 모의 수료생으로 검증하고 자기 자료만 삭제한다. 기존 시드·AI를 사용하지 않는다. */
@EnabledIfEnvironmentVariable(named = "IEUM_STUDENT_INTENT_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]",
        "spring.sql.init.mode=never", "app.ai.mock=true", "app.seoul.sync-cron=-", "app.status.recalculate-cron=-"
})
class StudentStatusDatabaseTest {
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private StatusService status;
    private Long courseId;
    private Long studentId;

    @BeforeEach
    void fixture() {
        courseId = insert("INSERT INTO course(name,cohort,campus,started_on,ended_on) "
                + "VALUES ('상태 연동 검증 과정','검증','모의 캠퍼스','2026-03-01','2026-08-31')");
        studentId = insert("INSERT INTO student(course_id,name,completed_on,last_response_at) "
                + "VALUES (?,'상태 연동 검증 수료생','2026-08-31',NOW(6))", courseId);
    }

    @AfterEach
    void cleanup() {
        if (studentId != null) {
            jdbc.update("DELETE FROM help_request WHERE student_id=?", studentId);
            jdbc.update("DELETE FROM recommendation WHERE student_id=?", studentId);
            jdbc.update("DELETE FROM student WHERE id=?", studentId);
        }
        if (courseId != null) jdbc.update("DELETE FROM course WHERE id=?", courseId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMPLOYED", "RESTING", "SEEKING"})
    void careerChoiceAndIndividualHelpClosureRemainIndependent(String selected) throws Exception {
        long first = help("INTERVIEW_PREPARATION");
        long second = help("CAREER_COUNSELING");
        JsonNode changed = request("PUT", "/api/students/" + studentId + "/status", Map.of("status", selected));
        assertEquals(selected, changed.path("status").asText());
        assertEquals(changed, request("GET", "/api/students/" + studentId + "/status", null));
        JsonNode repeated = request("PUT", "/api/students/" + studentId + "/status", Map.of("status", selected));
        assertEquals(repeated, request("GET", "/api/students/" + studentId + "/status", null));
        JsonNode detail = request("GET", "/api/coordinator/students/" + studentId, null).path("student");
        assertEquals(selected, detail.path("status").asText());
        assertEquals(2, detail.path("openHelpRequestCount").asInt());
        LocalDateTime responseAt = jdbc.queryForObject("SELECT last_response_at FROM student WHERE id=?",
                LocalDateTime.class, studentId);
        var close = Map.of("studentId", studentId, "closeReason", "EMPLOYED");
        JsonNode closed = request("POST", "/api/help-requests/" + first + "/close", close);
        assertEquals("CLOSED", closed.path("status").asText());
        assertEquals(closed, request("POST", "/api/help-requests/" + first + "/close", close));
        JsonNode open = request("GET", "/api/help-requests?studentId=" + studentId, null);
        assertEquals(1, open.size());
        assertEquals(second, open.get(0).path("helpRequestId").asLong());
        assertEquals("CAREER_COUNSELING", open.get(0).path("helpType").asText());
        assertTrue(open.get(0).get("jobTitle").isNull());
        assertEquals(selected, request("GET", "/api/students/" + studentId + "/status", null).path("status").asText());
        assertEquals(responseAt, jdbc.queryForObject("SELECT last_response_at FROM student WHERE id=?",
                LocalDateTime.class, studentId));
    }

    @Test
    void aLaterSavedApplyResumesSeekingAndALaterChoiceWinsAgainWithoutErasingHistory() throws Exception {
        request("PUT", "/api/students/" + studentId + "/status", Map.of("status", "RESTING"));
        long job = jdbc.queryForObject("SELECT MIN(id) FROM job_posting", Long.class);
        long recommendation = insert("INSERT INTO recommendation(student_id,trigger_type) VALUES (?,'VISIT')", studentId);
        long item = insert("INSERT INTO recommendation_item(recommendation_id,job_posting_id,rank_no,reasons,response,responded_at) "
                + "VALUES (?,?,1,'[]','APPLY',?)", recommendation, job, LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        status.recalculateCommitted(studentId);
        assertEquals("SEEKING", request("GET", "/api/students/" + studentId + "/status", null).path("status").asText());
        request("PUT", "/api/students/" + studentId + "/status", Map.of("status", "EMPLOYED"));
        status.recalculateCommitted(studentId);
        assertEquals("EMPLOYED", request("GET", "/api/students/" + studentId + "/status", null).path("status").asText());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM student_intent_history WHERE student_id=?", Integer.class, studentId));
        assertEquals("APPLY", jdbc.queryForObject("SELECT response FROM recommendation_item WHERE id=?", String.class, item));
    }

    private long help(String type) throws Exception {
        return request("POST", "/api/help-requests", Map.of("studentId", studentId, "helpType", type))
                .path("helpRequestId").asLong();
    }

    private JsonNode request(String method, String path, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if (body != null) builder.header("Content-Type", "application/json");
        var response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
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
