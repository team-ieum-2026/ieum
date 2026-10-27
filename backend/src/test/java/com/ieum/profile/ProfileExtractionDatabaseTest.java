// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ieum.ai.AiClient;
import com.ieum.ai.RestAiClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 실제 MySQL + RestAiClient + 로컬 HTTP 응답으로 커밋 이후 재추출을 검증한다.
 * IEUM_PROFILE_DB_TEST=true일 때만 실행한다. Gemini를 호출하거나 기존 시드를 변경하지 않는다.
 * 별도 모의 과정/수료생만 만들고 백그라운드 작업 완료 후 삭제한다.
 */
@EnabledIfEnvironmentVariable(named = "IEUM_PROFILE_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]",
        "spring.sql.init.mode=never",
        "app.ai.mock=false",
        "app.seoul.sync-cron=-",
        "app.status.recalculate-cron=-"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProfileExtractionDatabaseTest {
    private static final HttpFixture AI = new HttpFixture();

    @DynamicPropertySource
    static void localAiServer(DynamicPropertyRegistry properties) {
        properties.add("app.ai.server-url", AI::url);
    }

    @Autowired private ProfileItemService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private AiClient aiClient;
    @MockitoSpyBean private ProfileExtractionWorker worker;

    private final AtomicReference<CountDownLatch> completed = new AtomicReference<>(new CountDownLatch(0));
    private Long courseId;
    private Long studentId;

    @BeforeEach
    void createOwnMockStudentAndObserveWorkerCompletion() {
        assertInstanceOf(RestAiClient.class, aiClient);
        AI.prepare(Response.SUCCESS, false);
        String marker = UUID.randomUUID().toString();
        courseId = insert("""
                INSERT INTO course (name, cohort, campus, started_on, ended_on)
                VALUES (?, '검증', '모의 캠퍼스', '2026-03-01', '2026-08-31')
                """, "모의 추출 검증 과정 " + marker);
        studentId = insert("""
                INSERT INTO student (course_id, name, desired_job, preferred_region, skills, completed_on)
                VALUES (?, '모의 추출 검증 수료생', '백엔드 개발', '서울', '["Java"]', '2026-08-31')
                """, courseId);
        doAnswer(invocation -> {
            CountDownLatch done = completed.get();
            try {
                return invocation.callRealMethod();
            } finally {
                done.countDown();
            }
        }).when(worker).refresh(anyLong());
    }

    @AfterEach
    void removeOnlyOwnFixtureAfterBackgroundWorkStops() throws InterruptedException {
        AI.release();
        assertTrue(completed.get().await(15, TimeUnit.SECONDS),
                "백그라운드 작업이 끝난 뒤에만 모의 데이터를 삭제한다");
        if (studentId != null) jdbc.update("DELETE FROM student WHERE id = ?", studentId);
        if (courseId != null) jdbc.update("DELETE FROM course WHERE id = ?", courseId);
    }

    @AfterAll
    static void stopLocalHttpServer() {
        AI.close();
    }

    @Test
    void sourceCommitsBeforeAiCompletesAndProjectDbIdSurvivesRealHttpAndJsonStorage() throws Exception {
        AI.prepare(Response.SUCCESS, true);
        completed.set(new CountDownLatch(1));
        ObjectNode item = json.createObjectNode().put("title", "모의 연동 검증 프로젝트")
                .put("readme", "Java API를 구현한 모의 프로젝트 원문")
                .put("roleNote", "모의 API 구현");
        item.set("techStack", json.createArrayNode().add("Java"));

        StudentProfileResponse immediate = service.addItem(studentId, new ProfileItemRequest("PROJECTS", item));

        assertNull(immediate.profileUpdatedAt());
        assertNull(immediate.competencyProfile());
        Long projectId = jdbc.queryForObject(
                "SELECT id FROM student_project WHERE student_id = ?", Long.class, studentId);
        assertNotNull(projectId, "AI가 대기 중이어도 원문 항목은 이미 커밋되어야 한다");
        assertEquals(0, competencyCount());
        assertTrue(AI.arrived.await(5, TimeUnit.SECONDS), "실제 HTTP 요청이 도착해야 한다");
        assertEquals("HTTP/1.1", AI.protocol.get());
        JsonNode request = AI.request.get();
        assertEquals(studentId.longValue(), request.path("studentId").asLong());
        assertEquals(projectId.longValue(), request.path("projects").get(0).path("itemId").asLong());

        AI.release();
        awaitCompletion();

        JsonNode saved = competency();
        assertTrue(saved.path("roles").isArray() && saved.path("roles").isEmpty());
        assertTrue(saved.path("achievements").isArray() && saved.path("achievements").isEmpty());
        JsonNode evidence = saved.path("skills").get(0).path("evidence").get(0);
        assertEquals("PROJECT", evidence.path("refType").asText());
        assertEquals(projectId.longValue(), evidence.path("refId").asLong());
        String sourceHash = jdbc.queryForObject(
                "SELECT source_hash FROM competency_profile WHERE student_id = ?", String.class, studentId);
        assertTrue(sourceHash != null && sourceHash.matches("[0-9a-f]{64}"));
    }

    @ParameterizedTest
    @EnumSource(value = Response.class, names = {"UNAVAILABLE", "DISCONNECT"})
    void unavailableAiKeepsCommittedItemAndNextChangeRetriesSuccessfully(Response failure) throws Exception {
        AI.prepare(failure, false);
        completed.set(new CountDownLatch(1));
        service.addItem(studentId, new ProfileItemRequest("CERTIFICATES",
                json.createObjectNode().put("name", "모의 최초 자격증")));
        awaitCompletion();

        Long certificateId = jdbc.queryForObject(
                "SELECT id FROM student_certificate WHERE student_id = ?", Long.class, studentId);
        assertNotNull(certificateId);
        assertEquals("모의 최초 자격증", jdbc.queryForObject(
                "SELECT name FROM student_certificate WHERE id = ?", String.class, certificateId));
        assertEquals(0, competencyCount(), "AI 장애가 원문 저장을 되돌리면 안 된다");

        AI.prepare(Response.SUCCESS, false);
        completed.set(new CountDownLatch(1));
        service.updateItem(studentId, certificateId, new ProfileItemPatchRequest("CERTIFICATES",
                json.createObjectNode().put("name", "모의 수정 자격증")));
        awaitCompletion();

        assertEquals("모의 수정 자격증", jdbc.queryForObject(
                "SELECT name FROM student_certificate WHERE id = ?", String.class, certificateId));
        JsonNode saved = competency();
        assertEquals("모의 수정 자격증", saved.path("certificates").get(0).asText());
        assertEquals(certificateId.longValue(),
                AI.request.get().path("certificates").get(0).path("itemId").asLong());
        assertEquals(certificateId.longValue(),
                saved.path("skills").get(0).path("evidence").get(0).path("refId").asLong());
    }

    private void awaitCompletion() throws InterruptedException {
        assertTrue(completed.get().await(10, TimeUnit.SECONDS), "실제 HTTP 추출 작업이 완료되어야 한다");
    }

    private int competencyCount() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM competency_profile WHERE student_id = ?", Integer.class, studentId);
        return count == null ? 0 : count;
    }

    private JsonNode competency() throws IOException {
        assertEquals(1, competencyCount());
        return json.readTree(jdbc.queryForObject(
                "SELECT profile FROM competency_profile WHERE student_id = ?", String.class, studentId));
    }

    private Long insert(String sql, Object... values) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, key);
        assertNotNull(key.getKey());
        return key.getKey().longValue();
    }

    private enum Response { SUCCESS, UNAVAILABLE, DISCONNECT }

    private static final class HttpFixture {
        private final ObjectMapper json = new ObjectMapper();
        private final HttpServer server;
        private volatile Response response = Response.SUCCESS;
        private volatile CountDownLatch gate = new CountDownLatch(0);
        private volatile CountDownLatch arrived = new CountDownLatch(1);
        private final AtomicReference<JsonNode> request = new AtomicReference<>();
        private final AtomicReference<String> protocol = new AtomicReference<>();

        HttpFixture() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.createContext("/ai/profile/extract", this::extract);
                server.start();
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }

        String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

        void prepare(Response response, boolean block) {
            this.response = response;
            this.gate = new CountDownLatch(block ? 1 : 0);
            this.arrived = new CountDownLatch(1);
            request.set(null);
            protocol.set(null);
        }

        void release() { gate.countDown(); }
        void close() { server.stop(0); }

        private void extract(HttpExchange exchange) throws IOException {
            try {
                JsonNode body = json.readTree(exchange.getRequestBody());
                request.set(body);
                protocol.set(exchange.getProtocol());
                arrived.countDown();
                try {
                    if (!gate.await(10, TimeUnit.SECONDS)) return;
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (response == Response.DISCONNECT) return;
                if (response == Response.UNAVAILABLE) {
                    send(exchange, 503, "{\"error\":{\"code\":\"UNAVAILABLE\",\"message\":\"모의 장애\"}}");
                    return;
                }
                ObjectNode profile = json.createObjectNode();
                profile.set("roles", json.createArrayNode());
                profile.set("achievements", json.createArrayNode());
                ArrayNode certificates = profile.putArray("certificates");
                for (JsonNode certificate : body.path("certificates")) {
                    certificates.add(certificate.path("name").asText());
                }
                ArrayNode skills = profile.putArray("skills");
                String field = body.path("projects").isEmpty() ? "certificates" : "projects";
                if (!body.path(field).isEmpty()) {
                    ObjectNode skill = skills.addObject().put("name", "Java");
                    skill.putArray("evidence").addObject()
                            .put("refType", "projects".equals(field) ? "PROJECT" : "CERTIFICATE")
                            .put("refId", body.path(field).get(0).path("itemId").asLong());
                }
                profile.put("desiredJob", "백엔드 개발");
                profile.put("preferredRegion", "서울");
                ObjectNode result = json.createObjectNode();
                result.putObject("data").set("profile", profile);
                send(exchange, 200, json.writeValueAsString(result));
            } finally {
                exchange.close();
            }
        }

        private static void send(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
