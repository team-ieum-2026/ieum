// 주인: BE-B
package com.ieum.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기존 MySQL에 시드의 최소 상태 원천을 새 ID로 복제하고 전량 롤백한다.
 * 기존 학생 1~50을 덮어쓰거나 seed.sql을 실행하지 않는다. AUTO_INCREMENT 공백은 남을 수 있다.
 */
@EnabledIfEnvironmentVariable(named = "IEUM_STATUS_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]",
        "spring.sql.init.mode=never",
        "app.ai.mock=true",
        "app.seoul.sync-cron=-",
        "app.status.recalculate-cron=-"
})
class StatusSeedDatabaseTest {

    private static final List<String> TABLES = List.of("student", "recommendation", "recommendation_item",
            "help_request", "application", "student_status");

    @Autowired
    private StatusMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void clonedSeedFiftyMatchEveryExpectedStatusAndAggregateAndLeaveExistingRowsUntouched() throws IOException {
        requireTransactionalTables();
        Long courseId = jdbc.queryForObject("SELECT MIN(id) FROM course", Long.class);
        Long jobId = jdbc.queryForObject("SELECT MIN(id) FROM job_posting", Long.class);
        assertNotNull(courseId, "One existing course is needed for the fixture foreign key");
        assertNotNull(jobId, "One existing job is needed for the fixture foreign keys");
        StatusSeedFixture fixture = StatusSeedFixture.load();
        assertEquals(50, fixture.students.size());
        assertEquals(StatusSeedFixture.STATUS_COUNTS, StatusSeedFixture.statusCounts(fixture.expected));
        assertEquals(StatusSeedFixture.SEEKING_COUNTS, StatusSeedFixture.seekingCounts(fixture.expected));

        Map<String, List<Map<String, Object>>> before = snapshot();
        List<Long> existingStudentIds = jdbc.queryForList("SELECT id FROM student ORDER BY id", Long.class);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                // Rollback is set before the first write, even if later assertions or parsing fail.
                transaction.setRollbackOnly();
                Map<Long, Long> studentIds = copySources(fixture, courseId, jobId);
                assertEquals(50, studentIds.size());
                assertTrue(studentIds.values().stream().allMatch(id -> !existingStudentIds.contains(id)),
                        "Only newly generated student IDs may be used");
                StatusService service = new StatusService(mapper, fixture.clock());
                Map<Long, StatusSeedFixture.Expected> actual = new LinkedHashMap<>();

                for (StatusSeedFixture.Student student : fixture.students) {
                    long clonedId = studentIds.get(student.id());
                    service.recalculate(clonedId);
                    actual.put(student.id(), jdbc.queryForObject("""
                            SELECT status, sub_status FROM student_status WHERE student_id = ?
                            """, (rs, row) -> new StatusSeedFixture.Expected(
                            rs.getString("status"), rs.getString("sub_status")), clonedId));
                    assertEquals(student.lastResponseAt(), jdbc.queryForObject(
                            "SELECT last_response_at FROM student WHERE id = ?",
                            java.time.LocalDateTime.class, clonedId));
                }

                assertEquals(fixture.expected, actual, "All 50 seed status/subStatus pairs must match in MySQL");
                assertEquals(StatusSeedFixture.STATUS_COUNTS, StatusSeedFixture.statusCounts(actual));
                assertEquals(StatusSeedFixture.SEEKING_COUNTS, StatusSeedFixture.seekingCounts(actual));
                assertEquals(StatusSeedFixture.STATUS_COUNTS, storedCounts(studentIds, false));
                assertEquals(StatusSeedFixture.SEEKING_COUNTS, storedCounts(studentIds, true));
            });
        } finally {
            // Never include source rows, names or connection values in an assertion failure.
            assertTrue(before.equals(snapshot()), "Rollback must preserve every existing row and timestamp");
        }
    }

    private Map<Long, Long> copySources(StatusSeedFixture fixture, long courseId, long jobId) {
        Map<Long, Long> studentIds = new LinkedHashMap<>();
        for (StatusSeedFixture.Student student : fixture.students) {
            studentIds.put(student.id(), insert("""
                    INSERT INTO student (course_id, name, completed_on, last_response_at)
                    VALUES (?, '상태 검증 모의', ?, ?)
                    """, courseId, student.completedOn(), student.lastResponseAt()));
        }
        Map<Long, Long> recommendationIds = new LinkedHashMap<>();
        for (StatusSeedFixture.Recommendation recommendation : fixture.recommendations) {
            recommendationIds.put(recommendation.id(), insert("""
                    INSERT INTO recommendation (student_id, trigger_type, created_at) VALUES (?, ?, ?)
                    """, studentIds.get(recommendation.studentId()), recommendation.triggerType(),
                    recommendation.createdAt()));
        }
        Map<Long, Long> itemIds = new LinkedHashMap<>();
        for (StatusSeedFixture.Item item : fixture.items) {
            itemIds.put(item.id(), insert("""
                    INSERT INTO recommendation_item
                        (recommendation_id, job_posting_id, rank_no, reasons, response, pass_reason, responded_at)
                    VALUES (?, ?, ?, '[]', ?, ?, ?)
                    """, recommendationIds.get(item.recommendationId()), jobId, item.rank(), item.response(),
                    item.passReason(), item.respondedAt()));
        }
        for (StatusSeedFixture.Help help : fixture.helpRequests) {
            jdbc.update("""
                    INSERT INTO help_request (student_id, recommendation_item_id, status, created_at)
                    VALUES (?, ?, ?, ?)
                    """, studentIds.get(help.studentId()), help.recommendationItemId() == null
                    ? null : itemIds.get(help.recommendationItemId()), help.status(), help.createdAt());
        }
        for (StatusSeedFixture.Application application : fixture.applications) {
            jdbc.update("""
                    INSERT INTO application
                        (student_id, recommendation_item_id, job_posting_id, status,
                         submitted_at, reviewed_at, delivered_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, studentIds.get(application.studentId()), itemIds.get(application.recommendationItemId()),
                    jobId, application.status(), application.submittedAt(), application.reviewedAt(),
                    application.deliveredAt());
        }
        return studentIds;
    }

    private Map<String, Long> storedCounts(Map<Long, Long> studentIds, boolean seekingOnly) {
        // IDs come only from JDBC generated keys, never from a caller or an environment value.
        String placeholders = String.join(",", java.util.Collections.nCopies(studentIds.size(), "?"));
        String column = seekingOnly ? "sub_status" : "status";
        String sql = "SELECT " + column + " AS category, COUNT(*) AS total FROM student_status"
                + " WHERE student_id IN (" + placeholders + ")"
                + (seekingOnly ? " AND status = 'SEEKING'" : "") + " GROUP BY " + column;
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            counts.put(rs.getString("category"), rs.getLong("total"));
        }, studentIds.values().toArray());
        return counts;
    }

    private void requireTransactionalTables() {
        List<String> engines = jdbc.queryForList("""
                SELECT ENGINE FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME IN ('student', 'recommendation', 'recommendation_item',
                                     'help_request', 'application', 'student_status')
                """, String.class);
        assertEquals(TABLES.size(), engines.size(), "Existing status tables must be present");
        assertTrue(engines.stream().allMatch("InnoDB"::equalsIgnoreCase),
                "Only transactional tables can preserve existing data with rollback");
    }

    private long insert(String sql, Object... values) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        assertEquals(1, jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            return statement;
        }, keys));
        assertNotNull(keys.getKey());
        return keys.getKey().longValue();
    }

    private Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : TABLES) {
            String key = "student_status".equals(table) ? "student_id" : "id";
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM " + table + " ORDER BY " + key)
                    .stream().map(row -> {
                        Map<String, Object> normalized = new LinkedHashMap<>();
                        row.forEach((column, value) -> normalized.put(column,
                                value instanceof byte[] bytes ? Base64.getEncoder().encodeToString(bytes) : value));
                        return normalized;
                    }).toList();
            result.put(table, rows);
        }
        return result;
    }
}
