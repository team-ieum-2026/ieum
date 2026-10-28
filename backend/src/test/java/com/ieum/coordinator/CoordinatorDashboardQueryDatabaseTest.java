// 주인: BE-B
package com.ieum.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 MySQL에서 운영 매퍼 SQL을 그대로 실행하되 모든 입력 테이블을 상수 CTE로 가린다.
 * 테스트 데이터를 저장하지 않고 SELECT만 수행한다. 기존 DB 행이나 시퀀스를 바꾸지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "IEUM_DASHBOARD_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]",
        "spring.sql.init.mode=never",
        "app.ai.mock=true",
        "app.seoul.sync-cron=-",
        "app.status.recalculate-cron=-"
})
@Transactional(readOnly = true)
class CoordinatorDashboardQueryDatabaseTest {

    private static final String NAMESPACE = "com.ieum.coordinator.CoordinatorDashboardMapper.";
    private static final Pattern LEADING_WITH = Pattern.compile("(?i)^WITH\\s+");

    @Autowired private SqlSessionFactory sqlSessions;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void studentWithoutAStatusRowIsStillReturnedWithItsNullStatusAndResponseTime() {
        List<Map<String, Object>> rows = query("findStudentStates", """
                student AS (
                    SELECT 1 AS id, '모의 학생 가' AS name,
                           CAST('2026-10-27 09:00:00' AS DATETIME) AS last_response_at,
                           CAST('2026-08-31' AS DATE) AS completed_on
                    UNION ALL SELECT 2, '모의 학생 나', CAST('2026-10-26 09:00:00' AS DATETIME),
                                     CAST('2026-08-31' AS DATE)
                    UNION ALL SELECT 3, '모의 학생 다', NULL, CAST('2026-08-31' AS DATE)
                ),
                student_status AS (
                    SELECT 1 AS student_id, 'EMPLOYED' AS status, CAST(NULL AS CHAR(20)) AS sub_status
                    UNION ALL SELECT 2, 'SEEKING', 'WAITING'
                )
                """);

        assertEquals(List.of(1L, 2L, 3L), ids(rows, "student_id"));
        assertEquals("EMPLOYED", rows.get(0).get("status"));
        assertEquals("WAITING", rows.get(1).get("sub_status"));
        assertNull(rows.get(2).get("status"));
        assertNull(rows.get(2).get("sub_status"));
        assertNull(rows.get(2).get("last_response_at"));
    }

    @Test
    void openHelpWithoutAnItemRemainsVisibleAndContactedOrClosedRequestsAreExcluded() {
        List<Map<String, Object>> rows = query("findOpenHelpRequests", """
                student AS (
                    SELECT 1 AS id, '모의 학생 가' AS name, 10 AS course_id
                    UNION ALL SELECT 2, '모의 학생 나', 10
                ),
                course AS (SELECT 10 AS id, '모의 과정' AS name, '모의 기수' AS cohort),
                help_request AS (
                    SELECT 101 AS id, 1 AS student_id, 501 AS recommendation_item_id,
                           'OPEN' AS status, CAST('2026-10-28 10:00:00' AS DATETIME) AS created_at,
                           'SMS' AS contact_method, '모의 도움 메모' AS memo
                    UNION ALL SELECT 102, 2, NULL, 'OPEN', CAST('2026-10-28 10:00:00' AS DATETIME),
                                     NULL, NULL
                    UNION ALL SELECT 103, 1, 501, 'CONTACTED', CAST('2026-10-28 11:00:00' AS DATETIME),
                                     'PHONE', '모의 처리 메모'
                    UNION ALL SELECT 104, 1, 501, 'CLOSED', CAST('2026-10-28 12:00:00' AS DATETIME),
                                     'EMAIL', '모의 종료 메모'
                ),
                recommendation_item AS (SELECT 501 AS id, 701 AS job_posting_id),
                job_posting AS (
                    SELECT 701 AS id, '모의 기업' AS company_name, '모의 공고' AS title, 0 AS is_active
                )
                """);

        assertEquals(List.of(102L, 101L), ids(rows, "help_request_id"));
        assertNull(rows.get(0).get("job_title"));
        assertNull(rows.get(0).get("contact_method"));
        assertNull(rows.get(0).get("memo"));
        assertEquals("모의 과정 모의 기수", rows.get(0).get("course_label"));
        assertEquals("모의 기업 모의 공고", rows.get(1).get("job_title"));
        assertEquals("모의 도움 메모", rows.get(1).get("memo"));
    }

    @Test
    void submittedApplicationsRemainQueuedWithoutDocumentsAndWithInactiveJobs() {
        List<Map<String, Object>> rows = query("findReviewQueue", """
                student AS (
                    SELECT 1 AS id, '모의 학생 가' AS name
                    UNION ALL SELECT 2, '모의 학생 나'
                ),
                job_posting AS (
                    SELECT 701 AS id, '모의 비활성 기업' AS company_name, '모의 공고 가' AS title,
                           0 AS is_active
                    UNION ALL SELECT 702, '모의 활성 기업', '모의 공고 나', 1
                ),
                application AS (
                    SELECT 201 AS id, 1 AS student_id, 701 AS job_posting_id, 'SUBMITTED' AS status,
                           CAST('2026-10-28 09:00:00' AS DATETIME) AS submitted_at
                    UNION ALL SELECT 202, 2, 702, 'SUBMITTED', CAST('2026-10-28 09:00:00' AS DATETIME)
                    UNION ALL SELECT 203, 1, 701, 'APPROVED', CAST('2026-10-28 08:00:00' AS DATETIME)
                    UNION ALL SELECT 204, 2, 701, 'DRAFT', CAST('2026-10-28 08:00:00' AS DATETIME)
                    UNION ALL SELECT 205, 1, 702, 'SUBMITTED', CAST('2026-10-28 10:00:00' AS DATETIME)
                ),
                application_document AS (
                    SELECT CAST(NULL AS UNSIGNED) AS application_id, CAST(NULL AS CHAR) AS document
                    WHERE 1 = 0
                )
                """);

        assertEquals(List.of(201L, 202L, 205L), ids(rows, "application_id"));
        assertEquals("모의 비활성 기업 모의 공고 가", rows.get(0).get("job_title"));
    }

    @Test
    void latestPassNotesRespectClearingNonPassResponsesTimestampTiesAndStudentOrdering() {
        List<Map<String, Object>> rows = query("findLatestPassNotes", """
                student AS (
                    SELECT 1 AS id, '모의 학생 가' AS name
                    UNION ALL SELECT 2, '모의 학생 나'
                    UNION ALL SELECT 3, '모의 학생 다'
                    UNION ALL SELECT 4, '모의 학생 라'
                    UNION ALL SELECT 5, '모의 학생 마'
                    UNION ALL SELECT 6, '모의 학생 바'
                    UNION ALL SELECT 7, '모의 학생 사'
                    UNION ALL SELECT 8, '모의 학생 아'
                    UNION ALL SELECT 9, '모의 학생 자'
                ),
                recommendation AS (
                    SELECT 301 AS id, 1 AS student_id
                    UNION ALL SELECT 302, 2
                    UNION ALL SELECT 303, 3
                    UNION ALL SELECT 304, 4
                    UNION ALL SELECT 305, 5
                    UNION ALL SELECT 306, 6
                    UNION ALL SELECT 307, 7
                    UNION ALL SELECT 308, 8
                    UNION ALL SELECT 309, 9
                ),
                recommendation_item AS (
                    SELECT 11 AS id, 301 AS recommendation_id, 'PASS' AS response,
                           '모의 이전 메모 가' AS pass_note,
                           CAST('2026-10-28 08:00:00' AS DATETIME) AS responded_at
                    UNION ALL SELECT 12, 301, 'PASS', NULL, CAST('2026-10-28 09:00:00' AS DATETIME)
                    UNION ALL SELECT 21, 302, 'PASS', '모의 이전 메모 나',
                                     CAST('2026-10-28 08:00:00' AS DATETIME)
                    UNION ALL SELECT 22, 302, 'PASS', '   ', CAST('2026-10-28 09:00:00' AS DATETIME)
                    UNION ALL SELECT 31, 303, 'PASS', '모의 확인 메모',
                                     CAST('2026-10-28 09:00:00' AS DATETIME)
                    UNION ALL SELECT 32, 303, 'HELP', '모의 제외 도움 메모',
                                     CAST('2026-10-28 11:00:00' AS DATETIME)
                    UNION ALL SELECT 33, 303, 'APPLY', '모의 제외 지원 메모',
                                     CAST('2026-10-28 12:00:00' AS DATETIME)
                    UNION ALL SELECT 41, 304, 'PASS', '모의 이전 동률 메모',
                                     CAST('2026-10-28 09:00:00' AS DATETIME)
                    UNION ALL SELECT 42, 304, 'PASS', '모의 최신 동률 메모',
                                     CAST('2026-10-28 09:00:00' AS DATETIME)
                    UNION ALL SELECT 51, 305, 'PASS', '모의 가장 최근 메모',
                                     CAST('2026-10-28 10:00:00' AS DATETIME)
                    UNION ALL SELECT 61, 306, 'PASS', '모의 학생 순서 메모',
                                     CAST('2026-10-28 09:00:00' AS DATETIME)
                    UNION ALL SELECT 71, 307, 'HELP', '모의 제외 메모',
                                     CAST('2026-10-28 13:00:00' AS DATETIME)
                    UNION ALL SELECT 81, 308, 'PASS', '모의 이전 탭 메모',
                                     CAST('2026-10-28 08:00:00' AS DATETIME)
                    UNION ALL SELECT 82, 308, 'PASS', CHAR(9 USING utf8mb4),
                                     CAST('2026-10-28 14:00:00' AS DATETIME)
                    UNION ALL SELECT 91, 309, 'PASS', '모의 이전 개행 메모',
                                     CAST('2026-10-28 08:00:00' AS DATETIME)
                    UNION ALL SELECT 92, 309, 'PASS', CHAR(10 USING utf8mb4),
                                     CAST('2026-10-28 14:00:00' AS DATETIME)
                )
                """);

        assertEquals(List.of(5L, 3L, 4L, 6L), ids(rows, "student_id"));
        assertEquals(List.of("모의 가장 최근 메모", "모의 확인 메모", "모의 최신 동률 메모", "모의 학생 순서 메모"),
                rows.stream().map(row -> row.get("pass_note")).toList());
    }

    private List<Map<String, Object>> query(String statementName, String fixtureTables) {
        MappedStatement statement = sqlSessions.getConfiguration().getMappedStatement(NAMESPACE + statementName);
        assertEquals(SqlCommandType.SELECT, statement.getSqlCommandType());
        String productionSql = statement.getBoundSql(null).getSql().strip();
        assertTrue(productionSql.matches("(?is)^(SELECT|WITH)\\b.*"));
        assertFalse(productionSql.matches("(?is).*\\b(INSERT|UPDATE|DELETE|REPLACE|CALL|CREATE|DROP|ALTER)\\b.*"));
        Matcher with = LEADING_WITH.matcher(productionSql);
        String combined = with.find()
                ? "WITH " + fixtureTables + ", " + productionSql.substring(with.end())
                : "WITH " + fixtureTables + " " + productionSql;
        return jdbc.queryForList(combined);
    }

    private static List<Long> ids(List<Map<String, Object>> rows, String column) {
        return rows.stream().map(row -> ((Number) row.get(column)).longValue()).toList();
    }
}
