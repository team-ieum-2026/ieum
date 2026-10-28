// 주인: BE-B
package com.ieum.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 로컬 DB에서 SELECT만 수행한다. 행 생성·수정·삭제·초기화와 상태 재계산을 하지 않는다.
 * IEUM_DASHBOARD_DB_TEST=true일 때만 실행하며 인원수나 시드 구성은 가정하지 않는다.
 * 전후 전체 행(시각 포함)의 메모리 지문을 비교하고 개인별 원문은 검증 실패에도 출력하지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "IEUM_DASHBOARD_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]",
        "spring.sql.init.mode=never",
        "app.ai.mock=true",
        "app.seoul.sync-cron=-",
        "app.status.recalculate-cron=-"
})
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class CoordinatorDashboardDatabaseTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
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

    @Autowired private CoordinatorDashboardService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    @Test
    void dashboardMatchesExistingDbWithoutChangingAnyRowsOrTimestamps() throws Exception {
        Map<String, String> before = fingerprints();
        try {
            JsonNode dashboard = json.valueToTree(service.getDashboard());
            OffsetDateTime asOf = OffsetDateTime.parse(dashboard.path("asOf").asText());
            assertEquals(ZoneOffset.ofHours(9), asOf.getOffset());
            verifySummary(dashboard);
            verifyOpenHelpRequests(dashboard.path("helpRequests"));
            verifyReviewQueue(dashboard.path("reviewQueue"));
            verifyCallList(dashboard.path("callList"), asOf);
            verifyLatestPassNotes(dashboard.path("notesToCheck"));
        } finally {
            assertTrue(before.equals(fingerprints()), "대시보드 SELECT 전후 기존 행과 저장 시각이 같아야 한다");
        }
    }

    private void verifySummary(JsonNode dashboard) {
        Map<String, Object> expected = jdbc.queryForMap("""
                SELECT COUNT(*) AS total_students,
                       COALESCE(SUM(CASE WHEN ss.status = 'EMPLOYED' THEN 1 ELSE 0 END), 0) AS employed,
                       COALESCE(SUM(CASE WHEN ss.status = 'NEED_HELP' THEN 1 ELSE 0 END), 0) AS need_help,
                       COALESCE(SUM(CASE WHEN ss.status IN ('EMPLOYED', 'SEEKING', 'NEED_HELP')
                                         THEN 0 ELSE 1 END), 0) AS check_required,
                       COALESCE(SUM(CASE WHEN ss.status = 'SEEKING' THEN 1 ELSE 0 END), 0) AS seeking,
                       COALESCE(SUM(CASE WHEN ss.status = 'SEEKING' AND ss.sub_status = 'APPLYING'
                                         THEN 1 ELSE 0 END), 0) AS applying,
                       COALESCE(SUM(CASE WHEN ss.status = 'SEEKING' AND ss.sub_status = 'OTHER'
                                         THEN 1 ELSE 0 END), 0) AS other,
                       COALESCE(SUM(CASE WHEN ss.status = 'SEEKING'
                                           AND (ss.sub_status IS NULL OR ss.sub_status NOT IN ('APPLYING', 'OTHER'))
                                         THEN 1 ELSE 0 END), 0) AS waiting
                FROM student s
                LEFT JOIN student_status ss ON ss.student_id = s.id
                """);
        JsonNode summary = dashboard.path("statusSummary");
        JsonNode seeking = summary.path("SEEKING");
        assertEquals(number(expected, "total_students"), count(dashboard, "totalStudents"));
        assertEquals(number(expected, "employed"), count(summary, "EMPLOYED"));
        assertEquals(number(expected, "need_help"), count(summary, "NEED_HELP"));
        assertEquals(number(expected, "check_required"), count(summary, "CHECK_REQUIRED"));
        assertEquals(number(expected, "seeking"), count(seeking, "total"));
        assertEquals(number(expected, "applying"), count(seeking, "APPLYING"));
        assertEquals(number(expected, "waiting"), count(seeking, "WAITING"));
        assertEquals(number(expected, "other"), count(seeking, "OTHER"));
        assertEquals(count(dashboard, "totalStudents"), count(summary, "EMPLOYED")
                + count(summary, "NEED_HELP") + count(summary, "CHECK_REQUIRED") + count(seeking, "total"));
    }

    private void verifyOpenHelpRequests(JsonNode actual) {
        assertTrue(actual.isArray(), "도움 요청 목록은 빈 경우에도 배열이어야 한다");
        List<Help> expected = jdbc.query("""
                SELECT h.id, h.student_id, h.created_at, h.contact_method, h.memo,
                       h.recommendation_item_id, s.name AS student_name,
                       CONCAT(c.name, ' ', c.cohort) AS course_label,
                       CONCAT(j.company_name, ' ', j.title) AS job_title
                FROM help_request h
                JOIN student s ON s.id = h.student_id
                JOIN course c ON c.id = s.course_id
                LEFT JOIN recommendation_item ri ON ri.id = h.recommendation_item_id
                LEFT JOIN job_posting j ON j.id = ri.job_posting_id
                WHERE h.status = 'OPEN'
                ORDER BY h.created_at DESC, h.id DESC
                """, (row, index) -> new Help(row.getLong("id"), row.getLong("student_id"),
                time(row, "created_at"), row.getString("contact_method"), row.getString("memo"),
                row.getString("student_name"), row.getString("course_label"), row.getString("job_title"),
                row.getObject("recommendation_item_id") == null));
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            Help row = expected.get(i);
            JsonNode item = actual.get(i);
            assertEquals(row.id(), item.path("helpRequestId").asLong());
            assertEquals(row.studentId(), item.path("studentId").asLong());
            protectedText(row.studentName(), item.path("studentName"));
            protectedText(row.courseLabel(), item.path("courseLabel"));
            protectedText(row.jobTitle(), item.path("jobTitle"));
            protectedText(row.contactMethod(), item.path("contactMethod"));
            protectedText(row.memo(), item.path("memo"));
            protectedTime(row.createdAt(), item.path("createdAt"));
            if (row.withoutRecommendation()) {
                assertTrue(item.path("jobTitle").isNull(), "연결 공고가 없는 열린 요청도 null 공고와 함께 포함한다");
            }
        }
    }

    private void verifyReviewQueue(JsonNode actual) {
        assertTrue(actual.isArray(), "검토 목록은 빈 경우에도 배열이어야 한다");
        List<Review> expected = jdbc.query("""
                SELECT id, submitted_at FROM application
                WHERE status = 'SUBMITTED'
                ORDER BY submitted_at ASC, id ASC
                """, (row, index) -> new Review(row.getLong("id"), time(row, "submitted_at")));
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).id(), actual.get(i).path("applicationId").asLong());
            protectedTime(expected.get(i).submittedAt(), actual.get(i).path("submittedAt"));
        }
    }

    private void verifyCallList(JsonNode actual, OffsetDateTime asOf) {
        assertTrue(actual.isArray(), "전화 확인 목록은 빈 경우에도 배열이어야 한다");
        List<Call> expected = jdbc.query("""
                SELECT s.id, s.last_response_at, s.completed_on
                FROM student s
                LEFT JOIN student_status ss ON ss.student_id = s.id
                WHERE ss.status IS NULL OR ss.status NOT IN ('EMPLOYED', 'SEEKING', 'NEED_HELP')
                ORDER BY COALESCE(s.last_response_at, s.completed_on) ASC, s.id ASC
                """, (row, index) -> new Call(row.getLong("id"), time(row, "last_response_at"),
                row.getDate("completed_on").toLocalDate()));
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            Call row = expected.get(i);
            JsonNode item = actual.get(i);
            assertEquals(row.id(), item.path("studentId").asLong());
            protectedTime(row.lastResponseAt(), item.path("lastResponseAt"));
            LocalDateTime effective = row.lastResponseAt() == null
                    ? row.completedOn().atStartOfDay() : row.lastResponseAt();
            assertEquals(Math.max(0, Duration.between(effective, asOf.toLocalDateTime()).toDays()),
                    count(item, "daysSinceResponse"));
        }
    }

    private void verifyLatestPassNotes(JsonNode actual) {
        assertTrue(actual.isArray(), "비고 확인 목록은 빈 경우에도 배열이어야 한다");
        List<Note> expected = jdbc.query("""
                SELECT latest.student_id, latest.pass_note
                FROM (
                    SELECT r.student_id, ri.pass_note, ri.responded_at,
                           ROW_NUMBER() OVER (
                               PARTITION BY r.student_id ORDER BY ri.responded_at DESC, ri.id DESC
                           ) AS row_no
                    FROM recommendation r
                    JOIN recommendation_item ri ON ri.recommendation_id = r.id
                    WHERE ri.response = 'PASS'
                ) latest
                WHERE latest.row_no = 1 AND REGEXP_LIKE(latest.pass_note, '[^[:space:]]')
                ORDER BY latest.responded_at DESC, latest.student_id ASC
                """, (row, index) -> new Note(row.getLong("student_id"), row.getString("pass_note")));
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).id(), actual.get(i).path("studentId").asLong());
            protectedText(expected.get(i).note(), actual.get(i).path("passNote"));
        }
    }

    private Map<String, String> fingerprints() throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        for (Table table : TABLES) {
            // 식별자는 테스트 코드의 고정 목록만 사용한다. 행 값은 문자열 보간이나 로그에 넣지 않는다.
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM " + table.name() + " ORDER BY " + table.key());
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(rows));
            result.put(table.name(), HexFormat.of().formatHex(digest));
        }
        return result;
    }

    private static long number(Map<String, Object> values, String key) {
        return ((Number) values.get(key)).longValue();
    }

    private static long count(JsonNode parent, String field) {
        assertTrue(parent.path(field).isIntegralNumber(), "집계와 경과 일수는 정수로 반환한다");
        return parent.path(field).asLong();
    }

    private static LocalDateTime time(ResultSet row, String column) throws SQLException {
        var value = row.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static void protectedText(String expected, JsonNode actual) {
        assertTrue(expected == null ? actual.isNull() : actual.isTextual() && expected.equals(actual.asText()),
                "응답의 등록 원문과 null 여부가 DB와 같아야 한다");
    }

    private static void protectedTime(LocalDateTime expected, JsonNode actual) {
        if (expected == null) {
            assertTrue(actual.isNull(), "저장 시각이 없으면 키를 유지하고 null을 반환한다");
        } else {
            assertTrue(actual.isTextual()
                            && expected.atZone(SEOUL).toOffsetDateTime().equals(OffsetDateTime.parse(actual.asText())),
                    "저장 시각을 서울 시각으로 변환하고 원래 시각을 보존한다");
        }
    }

    private record Table(String name, String key) {}
    private record Help(long id, long studentId, LocalDateTime createdAt, String contactMethod, String memo,
                        String studentName, String courseLabel, String jobTitle, boolean withoutRecommendation) {}
    private record Review(long id, LocalDateTime submittedAt) {}
    private record Call(long id, LocalDateTime lastResponseAt, LocalDate completedOn) {}
    private record Note(long id, String note) {}
}
