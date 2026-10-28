// 주인: BE-B
package com.ieum.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 운영 BoundSql에 상수 CTE를 결합한다. 실제 행·스키마·시퀀스를 바꾸지 않고 SELECT만 수행한다. */
@EnabledIfEnvironmentVariable(named = "IEUM_COORDINATOR_STUDENT_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]",
        "spring.sql.init.mode=never",
        "app.ai.mock=true",
        "app.seoul.sync-cron=-",
        "app.status.recalculate-cron=-"
})
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class CoordinatorStudentQueryDatabaseTest {

    private static final String NAMESPACE = "com.ieum.coordinator.CoordinatorStudentMapper.";
    private static final Pattern LEADING_WITH = Pattern.compile("(?i)^WITH\\s+");
    private static final String FIXTURES = """
            course AS (SELECT 10 AS id, '모의 과정' AS name, '모의 기수' AS cohort),
            student AS (
                SELECT 1 AS id, 10 AS course_id, '모의 기본' AS name,
                       CAST('2026-08-31' AS DATE) AS completed_on,
                       CAST('2026-10-27 09:00:00' AS DATETIME) AS last_response_at,
                       'mock.student@example.com' AS email, '모의 직무' AS desired_job,
                       '모의 지역' AS preferred_region
                UNION ALL SELECT 2, 10, '모의%_! 학생', CAST('2026-08-31' AS DATE), NULL, NULL, NULL, NULL
                UNION ALL SELECT 3, 10, '모의 무상태', CAST('2026-08-31' AS DATE), NULL, NULL, NULL, NULL
                UNION ALL SELECT 4, 10, '모의 알수없음', CAST('2026-08-31' AS DATE), NULL, NULL, NULL, NULL
                UNION ALL SELECT 5, 10, '모의 소문자', CAST('2026-08-31' AS DATE), NULL, NULL, NULL, NULL
                UNION ALL SELECT 6, 10, '모의 뒤공백', CAST('2026-08-31' AS DATE), NULL, NULL, NULL, NULL
                UNION ALL SELECT 7, 10, '모의 세부 소문자', CAST('2026-08-31' AS DATE), NULL, NULL, NULL, NULL
                UNION ALL SELECT 8, 10, '모의 세부 공백', CAST('2026-08-31' AS DATE), NULL, NULL, NULL, NULL
                UNION ALL SELECT 9, 10, '모의 지원', CAST('2026-08-31' AS DATE), NULL, NULL, NULL, NULL
                UNION ALL SELECT 10, 10, '모의 도움', CAST('2026-08-31' AS DATE), NULL, NULL, NULL, NULL
                UNION ALL SELECT 11, 10, '모의 기타', CAST('2026-08-31' AS DATE), NULL, NULL, NULL, NULL
            ),
            student_status AS (
                SELECT 1 AS student_id, 'EMPLOYED' AS status, 'APPLYING' AS sub_status,
                       '모의 상태 근거' AS reason
                UNION ALL SELECT 2, 'SEEKING', 'LEGACY', '모의 상태 근거'
                UNION ALL SELECT 4, 'UNKNOWN', 'OTHER', '모의 상태 근거'
                UNION ALL SELECT 5, 'seeking', 'OTHER', '모의 상태 근거'
                UNION ALL SELECT 6, 'SEEKING ', 'OTHER', '모의 상태 근거'
                UNION ALL SELECT 7, 'SEEKING', 'other', '모의 상태 근거'
                UNION ALL SELECT 8, 'SEEKING', 'OTHER ', '모의 상태 근거'
                UNION ALL SELECT 9, 'SEEKING', 'APPLYING', '모의 상태 근거'
                UNION ALL SELECT 10, 'NEED_HELP', 'OTHER', '모의 상태 근거'
                UNION ALL SELECT 11, 'SEEKING', 'OTHER', '모의 상태 근거'
            ),
            competency_profile AS (SELECT 1 AS student_id, CAST('{"roles":[]}' AS JSON) AS profile),
            student_education AS (
                SELECT 101 AS id, 1 AS student_id
                UNION ALL SELECT 102, 1
                UNION ALL SELECT 103, 2
            ),
            student_certificate AS (
                SELECT 201 AS id, 1 AS student_id
                UNION ALL SELECT 202, 1
                UNION ALL SELECT 203, 1
                UNION ALL SELECT 204, 2
                UNION ALL SELECT 205, 2
            ),
            student_project AS (
                SELECT 301 AS id, 1 AS student_id
                UNION ALL SELECT 302, 1
                UNION ALL SELECT 303, 1
                UNION ALL SELECT 304, 1
                UNION ALL SELECT 305, 2
            ),
            student_cover_letter AS (
                SELECT 1 AS student_id, '   ' AS content
                UNION ALL SELECT 3, '모의 자기소개서'
            ),
            recommendation AS (
                SELECT 100 AS id, 1 AS student_id, CAST('2026-10-28 09:00:00' AS DATETIME) AS created_at
                UNION ALL SELECT 101, 1, CAST('2026-10-28 09:00:00' AS DATETIME)
                UNION ALL SELECT 102, 1, CAST('2026-10-28 08:00:00' AS DATETIME)
                UNION ALL SELECT 200, 2, CAST('2026-10-28 10:00:00' AS DATETIME)
                UNION ALL SELECT 201, 2, CAST('2026-10-28 11:00:00' AS DATETIME)
                UNION ALL SELECT 300, 3, CAST('2026-10-28 12:00:00' AS DATETIME)
                UNION ALL SELECT 400, 4, CAST('2026-10-28 13:00:00' AS DATETIME)
            ),
            recommendation_item AS (
                SELECT 1001 AS id, 100 AS recommendation_id, 701 AS job_posting_id, 2 AS rank_no,
                       'PASS' AS response, CAST(NULL AS CHAR(30)) AS pass_reason,
                       CAST(NULL AS CHAR(80)) AS pass_note,
                       CAST('2026-10-28 09:00:00' AS DATETIME) AS responded_at
                UNION ALL SELECT 1002, 100, 702, 1, 'NONE', NULL, NULL, NULL
                UNION ALL SELECT 1003, 102, 703, 1, 'HELP', NULL, NULL,
                                 CAST('2026-10-28 08:00:00' AS DATETIME)
                UNION ALL SELECT 2001, 200, 701, 1, 'PASS', 'OTHER', '모의 과거 비고',
                                 CAST('2026-10-28 08:00:00' AS DATETIME)
                UNION ALL SELECT 2002, 201, 702, 1, 'PASS', 'OTHER', '   ',
                                 CAST('2026-10-28 12:00:00' AS DATETIME)
                UNION ALL SELECT 3001, 300, 701, 1, 'PASS', 'OTHER',
                                 CONCAT(' ', CHAR(9 USING utf8mb4), CHAR(10 USING utf8mb4)),
                                 CAST('2026-10-28 12:00:00' AS DATETIME)
                UNION ALL SELECT 4001, 400, 701, 1, 'HELP', NULL, '모의 과거 도움 비고',
                                 CAST('2026-10-28 13:00:00' AS DATETIME)
            ),
            job_posting AS (
                SELECT 701 AS id, '모의 비활성 기업' AS company_name, '모의 공고 가' AS title, 0 AS is_active
                UNION ALL SELECT 702, '모의 활성 기업', '모의 공고 나', 1
                UNION ALL SELECT 703, '모의 다른 비활성 기업', '모의 공고 다', 0
            ),
            application AS (
                SELECT 9001 AS id, 1 AS student_id, 1001 AS recommendation_item_id, 'DRAFT' AS status
                UNION ALL SELECT 9002, 2, 1002, 'APPROVED'
                UNION ALL SELECT 9003, 2, 2001, 'SUBMITTED'
            ),
            application_document AS (
                SELECT CAST(NULL AS UNSIGNED) AS application_id, CAST(NULL AS CHAR) AS document WHERE 1 = 0
            ),
            contact_log AS (
                SELECT 11 AS id, 1 AS student_id, 'PHONE' AS contact_type, '모의 이전 상담' AS note,
                       CAST('2026-10-28 08:00:00' AS DATETIME) AS created_at
                UNION ALL SELECT 12, 1, 'MEETING', NULL, CAST('2026-10-28 09:00:00' AS DATETIME)
                UNION ALL SELECT 13, 1, 'MESSAGE', '모의 최신 상담', CAST('2026-10-28 09:00:00' AS DATETIME)
                UNION ALL SELECT 21, 10, 'PHONE', '모의 다른 학생 상담', CAST('2026-10-28 10:00:00' AS DATETIME)
                UNION ALL SELECT 61, 6, 'PHONE', NULL, CAST('2026-10-28 07:00:00' AS DATETIME)
            )
            """;

    @Autowired private SqlSessionFactory sqlSessions;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void statusNormalizationMatchesDashboardForMissingUnknownAndNoncanonicalValues() {
        List<Map<String, Object>> rows = query("findStudents", filters(null, null, null, 0, 50));

        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L), ids(rows, "student_id"));
        assertEquals(List.of("EMPLOYED", "SEEKING", "CHECK_REQUIRED", "CHECK_REQUIRED", "CHECK_REQUIRED",
                        "CHECK_REQUIRED", "SEEKING", "SEEKING", "SEEKING", "NEED_HELP", "SEEKING"),
                values(rows, "status"));
        assertEquals(Arrays.asList(null, "WAITING", null, null, null, null, "WAITING", "WAITING",
                        "APPLYING", null, "OTHER"), values(rows, "sub_status"));
        assertNull(rows.get(2).get("last_response_at"));
    }

    @Test
    void countsAndPagesApplyTheSameStatusSubstatusAndLiteralNameFilters() {
        assertPage(null, null, null, 2, 3, 11, List.of(3L, 4L, 5L));
        assertPage("CHECK_REQUIRED", null, null, 1, 2, 4, List.of(4L, 5L));
        assertPage("SEEKING", "WAITING", null, 1, 2, 3, List.of(7L, 8L));
        assertPage("SEEKING", "WAITING", "%!%!_!!%", 0, 20, 1, List.of(2L));
        assertPage(null, null, null, 99, 20, 11, List.of());
    }

    @Test
    void noteFlagIncludesPastNonblankNotesOrAnyContactRecordWithoutLeakingBetweenStudents() {
        List<Map<String, Object>> rows = query("findStudents", filters(null, null, null, 0, 50));

        assertTrue(flag(rows.get(0), "has_note"), "상담 기록만 있어도 기록 표시가 필요하다");
        assertTrue(flag(rows.get(1), "has_note"), "최신 메모를 비웠어도 과거 비고 기록은 존재한다");
        assertFalse(flag(rows.get(2), "has_note"), "공백·탭·개행만 있는 메모는 비고가 아니다");
        assertTrue(flag(rows.get(3), "has_note"), "이력에 남은 비고는 응답 종류와 무관하게 표시한다");
        assertFalse(flag(rows.get(4), "has_note"), "다른 학생의 비고나 상담 기록을 가져오면 안 된다");
        assertTrue(flag(rows.get(5), "has_note"), "기존 상담 메모가 null이어도 상담 기록은 존재한다");
        Map<String, Object> detail = query("findStudent", student(1)).get(0);
        assertNotNull(detail.get("competency_profile"));
        assertNull(query("findStudent", student(2)).get(0).get("competency_profile"));
    }

    @Test
    void registeredSummaryCountsEachSourceSeparatelyAndTreatsACoverLetterRowAsPresent() {
        Map<String, Object> first = query("findRegisteredSummary", student(1)).get(0);
        assertEquals(2L, number(first, "education"));
        assertEquals(3L, number(first, "certificates"));
        assertEquals(4L, number(first, "projects"));
        assertTrue(flag(first, "has_cover_letter"), "본문이 비어 있어도 자기소개서 행은 등록된 자료다");

        Map<String, Object> second = query("findRegisteredSummary", student(2)).get(0);
        assertEquals(1L, number(second, "education"));
        assertEquals(2L, number(second, "certificates"));
        assertEquals(1L, number(second, "projects"));
        assertFalse(flag(second, "has_cover_letter"));
    }

    @Test
    void historyKeepsEmptyHeadersRankOrderInactiveJobsAndOnlyTheSameStudentsApplications() {
        List<Map<String, Object>> headers = query("findRecommendations", student(1));
        assertEquals(List.of(101L, 100L, 102L), ids(headers, "recommendation_id"));
        List<Map<String, Object>> items = query("findHistoryItems", student(1));
        assertEquals(List.of(1002L, 1003L, 1001L), ids(items, "item_id"));
        assertEquals(List.of(100L, 102L, 100L), ids(items, "recommendation_id"));
        assertNull(items.get(0).get("application_id"), "다른 학생의 지원서를 붙이면 안 된다");
        assertNull(items.get(0).get("application_status"));
        assertEquals(9001L, number(items.get(2), "application_id"));
        assertEquals("DRAFT", items.get(2).get("application_status"));
        assertEquals("모의 비활성 기업", items.get(2).get("company_name"));
    }

    @Test
    void contactLogsKeepTheStudentsOwnRowsInReverseTimestampAndIdOrderIncludingLegacyNull() {
        List<Map<String, Object>> logs = query("findContactLogs", student(1));

        assertEquals(List.of(13L, 12L, 11L), ids(logs, "contact_log_id"));
        assertEquals(List.of(1L, 1L, 1L), ids(logs, "student_id"));
        assertEquals(List.of("MESSAGE", "MEETING", "PHONE"), values(logs, "contact_type"));
        assertNull(logs.get(1).get("note"));
        assertTrue(query("findContactLogs", student(3)).isEmpty());
    }

    private void assertPage(String status, String subStatus, String namePattern, long offset, int size,
                            long expectedTotal, List<Long> expectedIds) {
        Map<String, Object> parameters = filters(status, subStatus, namePattern, offset, size);
        Map<String, Object> count = query("countStudents", parameters).get(0);
        assertEquals(expectedTotal, ((Number) count.values().iterator().next()).longValue());
        assertEquals(expectedIds, ids(query("findStudents", parameters), "student_id"));
    }

    private List<Map<String, Object>> query(String statementName, Map<String, Object> parameters) {
        MappedStatement statement = sqlSessions.getConfiguration().getMappedStatement(NAMESPACE + statementName);
        assertEquals(SqlCommandType.SELECT, statement.getSqlCommandType());
        BoundSql boundSql = statement.getBoundSql(parameters);
        String productionSql = boundSql.getSql().strip();
        assertTrue(productionSql.matches("(?is)^(SELECT|WITH)\\b.*"));
        assertFalse(productionSql.matches("(?is).*\\b(INSERT|UPDATE|DELETE|REPLACE|CALL|CREATE|DROP|ALTER)\\b.*"));
        Matcher with = LEADING_WITH.matcher(productionSql);
        String combined = with.find()
                ? "WITH " + FIXTURES + ", " + productionSql.substring(with.end())
                : "WITH " + FIXTURES + " " + productionSql;
        return jdbc.query(connection -> {
            PreparedStatement prepared = connection.prepareStatement(combined);
            new DefaultParameterHandler(statement, parameters, boundSql).setParameters(prepared);
            return prepared;
        }, new ColumnMapRowMapper());
    }

    private static Map<String, Object> filters(String status, String subStatus, String namePattern,
                                               long offset, int size) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("subStatus", subStatus);
        result.put("namePattern", namePattern);
        result.put("offset", offset);
        result.put("size", size);
        return result;
    }

    private static Map<String, Object> student(long studentId) {
        return Map.of("studentId", studentId);
    }

    private static List<Long> ids(List<Map<String, Object>> rows, String column) {
        return rows.stream().map(row -> number(row, column)).toList();
    }

    private static List<Object> values(List<Map<String, Object>> rows, String column) {
        return rows.stream().map(row -> row.get(column)).toList();
    }

    private static long number(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).longValue();
    }

    private static boolean flag(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value instanceof Boolean bool ? bool : ((Number) value).intValue() != 0;
    }
}
