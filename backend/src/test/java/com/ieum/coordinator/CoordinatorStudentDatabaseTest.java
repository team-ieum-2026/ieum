// 주인: BE-B
package com.ieum.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
 * 기존 DB의 SELECT만 실행한다. 모의 행 생성·삭제·초기화, 상담 저장과 상태 재계산을 하지 않는다.
 * IEUM_COORDINATOR_STUDENT_DB_TEST=true일 때만 실행하며 특정 시드 번호나 인원수는 가정하지 않는다.
 * 비교 실패 시에도 개인별 원문·연락처·시각을 출력하지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "IEUM_COORDINATOR_STUDENT_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]",
        "spring.sql.init.mode=never",
        "app.ai.mock=true",
        "app.seoul.sync-cron=-",
        "app.status.recalculate-cron=-"
})
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class CoordinatorStudentDatabaseTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String DISPLAY_STATUS = """
            CASE WHEN CAST(ss.status AS BINARY) IN ('EMPLOYED', 'SEEKING', 'NEED_HELP')
                 THEN ss.status ELSE 'CHECK_REQUIRED' END
            """;
    private static final String DISPLAY_SUB_STATUS = """
            CASE WHEN CAST(ss.status AS BINARY) = 'SEEKING'
                 THEN CASE WHEN CAST(ss.sub_status AS BINARY) IN ('APPLYING', 'OTHER')
                           THEN ss.sub_status ELSE 'WAITING' END ELSE NULL END
            """;
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

    @Autowired private CoordinatorStudentService service;
    @Autowired private CoordinatorDashboardService dashboard;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    @Test
    void existingStudentsFiltersDetailsAndHistoryMatchOneUnchangedReadSnapshot() throws Exception {
        Map<String, String> before = fingerprints();
        try {
            List<Long> studentIds = jdbc.queryForList("SELECT id FROM student ORDER BY id ASC", Long.class);
            verifyPagination(studentIds);
            verifyStatusFiltersAndDashboard(studentIds.size());
            verifyLiteralNameSearch();
            if (!studentIds.isEmpty()) verifyDetail(studentIds.get(0));
        } finally {
            assertTrue(before.equals(fingerprints()), "조회 스냅샷의 기존 행과 저장 시각을 보존해야 한다");
        }
    }

    private void verifyPagination(List<Long> expectedIds) {
        List<Long> actualIds = new ArrayList<>();
        int pages = Math.max(1, (expectedIds.size() + 36) / 37);
        for (int page = 1; page <= pages; page++) {
            var result = service.list(null, null, null, page, 37);
            assertEquals(expectedIds.size(), result.total());
            assertEquals(page, result.page());
            assertEquals(37, result.size());
            actualIds.addAll(result.items().stream().map(CoordinatorStudentResponse.Row::studentId).toList());
        }
        assertEquals(expectedIds, actualIds);
        var pastEnd = service.list(null, null, null, Integer.MAX_VALUE, 100);
        assertEquals(expectedIds.size(), pastEnd.total());
        assertTrue(pastEnd.items().isEmpty());
    }

    private void verifyStatusFiltersAndDashboard(long totalStudents) {
        var summary = dashboard.getDashboard().statusSummary();
        Map<String, Long> dashboardCounts = Map.of("EMPLOYED", summary.employed(),
                "SEEKING", summary.seeking().total(), "NEED_HELP", summary.needHelp(),
                "CHECK_REQUIRED", summary.checkRequired());
        long total = 0;
        for (String status : List.of("EMPLOYED", "SEEKING", "NEED_HELP", "CHECK_REQUIRED")) {
            long expected = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM student s LEFT JOIN student_status ss ON ss.student_id = s.id
                    WHERE (
                    """ + DISPLAY_STATUS + ") = ?", Long.class, status);
            var result = service.list(status, null, null, 1, 100);
            assertEquals(expected, result.total());
            assertEquals(expected, dashboardCounts.get(status));
            assertTrue(result.items().stream().allMatch(row -> status.equals(row.status())));
            total += expected;
        }
        assertEquals(totalStudents, total);
        Map<String, Long> seekingCounts = Map.of("APPLYING", summary.seeking().applying(),
                "WAITING", summary.seeking().waiting(), "OTHER", summary.seeking().other());
        long seekingTotal = 0;
        for (String subStatus : List.of("APPLYING", "WAITING", "OTHER")) {
            long expected = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM student s LEFT JOIN student_status ss ON ss.student_id = s.id
                    WHERE (
                    """ + DISPLAY_SUB_STATUS + ") = ?", Long.class, subStatus);
            var result = service.list("SEEKING", subStatus, null, 1, 100);
            assertEquals(expected, result.total());
            assertEquals(expected, seekingCounts.get(subStatus));
            assertTrue(result.items().stream().allMatch(row -> "SEEKING".equals(row.status())
                    && subStatus.equals(row.subStatus())));
            seekingTotal += expected;
        }
        assertEquals(summary.seeking().total(), seekingTotal);
        assertEquals(0, service.list("EMPLOYED", "APPLYING", null, 1, 100).total());
    }

    private void verifyLiteralNameSearch() {
        String q = "%_!";
        long expected = jdbc.queryForObject(
                "SELECT COUNT(*) FROM student WHERE LOCATE(?, name) > 0", Long.class, q);
        assertEquals(expected, service.list(null, null, q, 1, 100).total());
    }

    private void verifyDetail(Long studentId) throws Exception {
        var detail = service.detail(studentId);
        Map<String, Object> raw = jdbc.queryForMap("""
                SELECT s.name, CONCAT(c.name, ' ', c.cohort) AS course_label, s.completed_on,
                       s.last_response_at, s.email, s.desired_job, s.preferred_region, ss.reason,
                       ss.status AS raw_status, ss.sub_status AS raw_sub_status,
                       CAST(cp.profile AS CHAR) AS profile,
                       (EXISTS(
                           SELECT 1 FROM recommendation r
                           JOIN recommendation_item ri ON ri.recommendation_id = r.id
                           WHERE r.student_id = s.id AND REGEXP_LIKE(ri.pass_note, '[^[:space:]]')
                       ) OR EXISTS(SELECT 1 FROM contact_log cl WHERE cl.student_id = s.id)) AS has_note
                FROM student s
                JOIN course c ON c.id = s.course_id
                LEFT JOIN student_status ss ON ss.student_id = s.id
                LEFT JOIN competency_profile cp ON cp.student_id = s.id
                WHERE s.id = ?
                """, studentId);
        var student = detail.student();
        assertEquals(studentId, student.studentId());
        protectedEqual(raw.get("name"), student.name());
        protectedEqual(raw.get("course_label"), student.courseLabel());
        protectedEqual(((java.sql.Date) raw.get("completed_on")).toLocalDate(), student.completedOn());
        protectedTime(time(raw.get("last_response_at")), student.lastResponseAt());
        protectedEqual(raw.get("email"), student.email());
        protectedEqual(raw.get("desired_job"), student.desiredJob());
        protectedEqual(raw.get("preferred_region"), student.preferredRegion());
        protectedEqual(raw.get("reason"), student.reason());
        String status = (String) raw.get("raw_status");
        String subStatus = (String) raw.get("raw_sub_status");
        String expectedStatus = "EMPLOYED".equals(status) || "SEEKING".equals(status) || "NEED_HELP".equals(status)
                ? status : "CHECK_REQUIRED";
        String expectedSubStatus = !"SEEKING".equals(expectedStatus) ? null
                : "APPLYING".equals(subStatus) || "OTHER".equals(subStatus) ? subStatus : "WAITING";
        protectedEqual(expectedStatus, student.status());
        protectedEqual(expectedSubStatus, student.subStatus());
        boolean hasNote = raw.get("has_note") instanceof Boolean value ? value
                : ((Number) raw.get("has_note")).intValue() != 0;
        assertEquals(hasNote, student.hasNote());
        if (raw.get("profile") == null) {
            assertTrue(detail.competencyProfile() == null, "저장된 역량 원문이 없으면 null을 유지한다");
        }
        else assertTrue(json.readTree((String) raw.get("profile")).equals(detail.competencyProfile()),
                "역량 JSON과 모든 근거 refId를 저장된 값 그대로 반환해야 한다");
        assertEquals(count("student_education", studentId), detail.registered().education());
        assertEquals(count("student_certificate", studentId), detail.registered().certificates());
        assertEquals(count("student_project", studentId), detail.registered().projects());
        assertEquals(count("student_cover_letter", studentId) > 0, detail.registered().hasCoverLetter());
        verifyHistory(studentId, detail.history());
        verifyContactLogs(studentId, detail.contactLogs());
    }

    private void verifyHistory(Long studentId, List<CoordinatorStudentResponse.History> actual) {
        List<Recommendation> expected = jdbc.query("""
                SELECT id, created_at FROM recommendation WHERE student_id = ?
                ORDER BY created_at DESC, id DESC
                """, (row, index) -> new Recommendation(row.getLong("id"), time(row, "created_at")), studentId);
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            var header = expected.get(i);
            var history = actual.get(i);
            assertEquals(header.id(), history.recommendationId());
            protectedTime(header.createdAt(), history.createdAt());
            List<HistoryItem> expectedItems = jdbc.query("""
                    SELECT ri.id, j.company_name, j.title, ri.response, ri.pass_reason, ri.pass_note,
                           ri.responded_at, a.id AS application_id, a.status AS application_status
                    FROM recommendation_item ri
                    JOIN job_posting j ON j.id = ri.job_posting_id
                    LEFT JOIN application a ON a.recommendation_item_id = ri.id AND a.student_id = ?
                    WHERE ri.recommendation_id = ?
                    ORDER BY ri.rank_no ASC, ri.id ASC
                    """, (row, index) -> new HistoryItem(row.getLong("id"), row.getString("company_name"),
                    row.getString("title"), row.getString("response"), row.getString("pass_reason"),
                    row.getString("pass_note"), time(row, "responded_at"),
                    row.getObject("application_id") == null ? null : row.getLong("application_id"),
                    row.getString("application_status")), studentId, header.id());
            assertEquals(expectedItems.size(), history.items().size());
            for (int j = 0; j < expectedItems.size(); j++) {
                var row = expectedItems.get(j);
                var item = history.items().get(j);
                assertEquals(row.id(), item.itemId());
                protectedEqual(row.companyName(), item.companyName());
                protectedEqual(row.title(), item.title());
                protectedEqual(row.response(), item.response());
                protectedEqual(row.passReason(), item.passReason());
                protectedEqual(row.passNote(), item.passNote());
                protectedTime(row.respondedAt(), item.respondedAt());
                assertEquals(row.applicationId(), item.applicationId());
                protectedEqual(row.applicationStatus(), item.applicationStatus());
                if (item.applicationId() != null) {
                    assertEquals(1L, jdbc.queryForObject("""
                            SELECT COUNT(*) FROM application
                            WHERE id = ? AND student_id = ? AND recommendation_item_id = ?
                            """, Long.class, item.applicationId(), studentId, item.itemId()));
                }
            }
        }
    }

    private void verifyContactLogs(Long studentId, List<CoordinatorStudentResponse.ContactLog> actual) {
        List<ContactLog> expected = jdbc.query("""
                SELECT id, contact_type, note, created_at FROM contact_log
                WHERE student_id = ? ORDER BY created_at DESC, id DESC
                """, (row, index) -> new ContactLog(row.getLong("id"), row.getString("contact_type"),
                row.getString("note"), time(row, "created_at")), studentId);
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).id(), actual.get(i).contactLogId());
            protectedEqual(expected.get(i).type(), actual.get(i).contactType());
            protectedEqual(expected.get(i).note(), actual.get(i).note());
            protectedTime(expected.get(i).createdAt(), actual.get(i).createdAt());
        }
    }

    private long count(String table, Long studentId) {
        // table은 위 검증 메서드에 고정된 프로필 테이블 이름만 전달한다.
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE student_id = ?", Long.class, studentId);
    }

    private Map<String, String> fingerprints() throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        for (Table table : TABLES) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM " + table.name() + " ORDER BY " + table.key());
            result.put(table.name(), HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(rows))));
        }
        return result;
    }

    private static LocalDateTime time(ResultSet row, String column) throws SQLException {
        var value = row.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static LocalDateTime time(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        throw new IllegalArgumentException("지원하지 않는 JDBC 시각 반환 타입이다");
    }

    private static void protectedEqual(Object expected, Object actual) {
        assertTrue(java.util.Objects.equals(expected, actual), "응답의 원문과 null 여부가 DB와 같아야 한다");
    }

    private static void protectedTime(LocalDateTime expected, OffsetDateTime actual) {
        protectedEqual(expected == null ? null : expected.atZone(SEOUL).toOffsetDateTime(), actual);
    }

    private record Table(String name, String key) {}
    private record Recommendation(Long id, LocalDateTime createdAt) {}
    private record HistoryItem(Long id, String companyName, String title, String response, String passReason,
                               String passNote, LocalDateTime respondedAt, Long applicationId,
                               String applicationStatus) {}
    private record ContactLog(Long id, String type, String note, LocalDateTime createdAt) {}
}
