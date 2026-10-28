// 주인: BE-B
package com.ieum.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * IEUM_STATUS_DB_TEST=true일 때만 기존 로컬 MySQL을 사용한다.
 * 모든 쓰기는 외부 트랜잭션에서 롤백하며 기존 원문은 실패 메시지에 출력하지 않는다.
 * 실제 시드와 스키마는 바꾸지 않는다. InnoDB AUTO_INCREMENT 공백은 남을 수 있다.
 */
@EnabledIfEnvironmentVariable(named = "IEUM_STATUS_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]",
        "spring.sql.init.mode=never",
        "app.ai.mock=true",
        "app.seoul.sync-cron=-",
        "app.status.recalculate-cron=-"
})
class StatusDatabaseTest {

    private static final long STUDENT_ID = 1L;
    private static final long SECOND_STUDENT_ID = 2L;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired
    private StatusService service;

    @Autowired
    private StatusMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactions;
    private long jobPostingId;

    @BeforeEach
    void requireExistingSeedAndTransactionalTables() {
        List<String> engines = jdbc.queryForList("""
                SELECT ENGINE FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME IN ('student', 'student_status', 'recommendation',
                                     'recommendation_item', 'help_request', 'application')
                """, String.class);
        assertEquals(6, engines.size(), "기존 스키마가 준비되어 있어야 한다");
        assertTrue(engines.stream().allMatch("InnoDB"::equalsIgnoreCase),
                "기존 데이터를 보호하기 위해 InnoDB 테이블에서만 실행한다");
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM student WHERE id IN (1, 2)", Long.class),
                "기존 시드 수료생 1, 2가 있어야 한다");
        Long existingJob = jdbc.queryForObject("SELECT MIN(id) FROM job_posting", Long.class);
        assertNotNull(existingJob, "외래 키에 사용할 기존 공고 한 건이 있어야 한다");
        jobPostingId = existingJob;
        transactions = new TransactionTemplate(transactionManager);
    }

    @Test
    void employedResponseHasPriorityOverHelpStalenessApplicationsAndLaterOtherPass() {
        rolledBack(tx -> {
            neutralize(STUDENT_ID, now().minusDays(10));
            item(STUDENT_ID, "PASS", "ALREADY_EMPLOYED", now().minusDays(12));
            item(STUDENT_ID, "PASS", "OTHER", now().minusDays(11));
            help(STUDENT_ID, "OPEN");
            application(STUDENT_ID, "SUBMITTED");

            service.recalculate(STUDENT_ID);

            assertStored(STUDENT_ID, "EMPLOYED", null);
        });
    }

    @Test
    void openHelpHasPriorityOverStalenessApplicationsAndOtherPass() {
        rolledBack(tx -> {
            neutralize(STUDENT_ID, now().minusDays(10));
            item(STUDENT_ID, "PASS", "OTHER", now().minusDays(9));
            application(STUDENT_ID, "APPROVED");
            help(STUDENT_ID, "OPEN");

            service.recalculate(STUDENT_ID);

            assertStored(STUDENT_ID, "NEED_HELP", null);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONTACTED", "CLOSED"})
    void resolvedHelpDoesNotPreventSevenDayCheckRequired(String helpStatus) {
        rolledBack(tx -> {
            neutralize(STUDENT_ID, now().minusDays(7).minusMinutes(1));
            help(STUDENT_ID, helpStatus);
            application(STUDENT_ID, "DELIVERED");
            item(STUDENT_ID, "PASS", "OTHER", now().minusDays(6));

            service.recalculate(STUDENT_ID);

            assertStored(STUDENT_ID, "CHECK_REQUIRED", null);
        });
    }

    @Test
    void justUnderSevenDaysRemainsSeekingAndRecalculateDoesNotChangeResponseTime() {
        rolledBack(tx -> {
            LocalDateTime lastResponse = now().minusDays(7).plusMinutes(1);
            neutralize(STUDENT_ID, lastResponse);
            LocalDateTime storedTime = lastResponse(STUDENT_ID);

            service.recalculate(STUDENT_ID);

            assertStored(STUDENT_ID, "SEEKING", "WAITING");
            assertEquals(storedTime, lastResponse(STUDENT_ID));
        });
    }

    @Test
    void latestSavedEmptyRecommendationRequiresCheckingAndNewNonemptyRecommendationClearsIt() {
        rolledBack(tx -> {
            neutralize(STUDENT_ID, now().minusHours(1));
            LocalDateTime responseAt = lastResponse(STUDENT_ID);
            insert("""
                    INSERT INTO recommendation (student_id, trigger_type, created_at)
                    VALUES (?, 'REFRESH', ?)
                    """, STUDENT_ID, now());

            service.recalculate(STUDENT_ID);

            assertStored(STUDENT_ID, "CHECK_REQUIRED", null);
            assertEquals("맞는 공고 없음", jdbc.queryForObject(
                    "SELECT reason FROM student_status WHERE student_id = ?", String.class, STUDENT_ID));
            assertEquals(responseAt, lastResponse(STUDENT_ID));

            item(STUDENT_ID, "NONE", null, null);
            service.recalculate(STUDENT_ID);

            assertStored(STUDENT_ID, "SEEKING", "WAITING");
        });
    }

    @Test
    void olderEmptyRecommendationWithHigherIdDoesNotOverrideNewerNonemptyRecommendation() {
        rolledBack(tx -> {
            neutralize(STUDENT_ID, now().minusHours(1));
            insert("""
                    INSERT INTO recommendation (student_id, trigger_type, created_at)
                    VALUES (?, 'REFRESH', ?)
                    """, STUDENT_ID, now().minusDays(1));

            service.recalculate(STUDENT_ID);

            assertStored(STUDENT_ID, "SEEKING", "WAITING");
        });
    }

    @Test
    void recommendationContainingAnInactiveJobIsNotAStoredEmptyRecommendation() {
        Long inactiveJobId = jdbc.queryForObject(
                "SELECT MIN(id) FROM job_posting WHERE is_active = 0", Long.class);
        assertNotNull(inactiveJobId, "기존 시드의 비활성 공고 한 건이 있어야 한다");
        rolledBack(tx -> {
            neutralize(STUDENT_ID, now().minusHours(1));
            long recommendationId = insert("""
                    INSERT INTO recommendation (student_id, trigger_type, created_at)
                    VALUES (?, 'REFRESH', ?)
                    """, STUDENT_ID, now());
            insert("""
                    INSERT INTO recommendation_item (recommendation_id, job_posting_id, rank_no, reasons)
                    VALUES (?, ?, 1, '[]')
                    """, recommendationId, inactiveJobId);

            service.recalculate(STUDENT_ID);

            assertStored(STUDENT_ID, "SEEKING", "WAITING");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTED", "APPROVED", "DELIVERED"})
    void activeApplicationHasPriorityOverOtherPass(String applicationStatus) {
        rolledBack(tx -> {
            neutralize(STUDENT_ID, now().minusDays(1));
            application(STUDENT_ID, applicationStatus);
            item(STUDENT_ID, "PASS", "OTHER", now().minusHours(1));

            service.recalculate(STUDENT_ID);

            assertStored(STUDENT_ID, "SEEKING", "APPLYING");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"GENERATING", "DRAFT", "REJECTED", "FAILED"})
    void nonSubmittedApplicationDoesNotCountAsApplying(String applicationStatus) {
        rolledBack(tx -> {
            neutralize(STUDENT_ID, now().minusDays(1));
            application(STUDENT_ID, applicationStatus);

            service.recalculate(STUDENT_ID);

            assertStored(STUDENT_ID, "SEEKING", "WAITING");
        });
    }

    @Test
    void latestPassUsesResponseTimeAcrossRecommendationsAndIgnoresLaterNonPass() {
        rolledBack(tx -> {
            neutralize(STUDENT_ID, now().minusHours(1));
            // ID순과 시각순을 뒤집어, 추천 묶음을 넘어 최근 PASS를 찾는지 검증한다.
            item(STUDENT_ID, "PASS", "OTHER", now().minusHours(2));
            item(STUDENT_ID, "PASS", "JOB_MISMATCH", now().minusHours(3));
            item(STUDENT_ID, "APPLY", null, now().minusHours(1));

            service.recalculate(STUDENT_ID);

            assertStored(STUDENT_ID, "SEEKING", "OTHER");
            item(STUDENT_ID, "PASS", "SKILL_GAP", now().minusMinutes(30));
            service.recalculate(STUDENT_ID);
            assertStored(STUDENT_ID, "SEEKING", "WAITING");
        });
    }

    @Test
    void latestPassWithTheSameResponseTimestampUsesTheHigherItemId() {
        rolledBack(tx -> {
            neutralize(STUDENT_ID, now().minusHours(1));
            LocalDateTime sameTimestamp = now().minusMinutes(30);
            item(STUDENT_ID, "PASS", "OTHER", sameTimestamp);
            item(STUDENT_ID, "PASS", "JOB_MISMATCH", sameTimestamp);

            service.recalculate(STUDENT_ID);

            assertStored(STUDENT_ID, "SEEKING", "WAITING");
            item(STUDENT_ID, "PASS", "OTHER", sameTimestamp);
            service.recalculate(STUDENT_ID);
            assertStored(STUDENT_ID, "SEEKING", "OTHER");
        });
    }

    @Test
    void anotherStudentsResponsesHelpAndApplicationsDoNotAffectTargetStudent() {
        rolledBack(tx -> {
            neutralize(STUDENT_ID, now().minusHours(1));
            neutralize(SECOND_STUDENT_ID, now().minusHours(1));
            item(SECOND_STUDENT_ID, "PASS", "ALREADY_EMPLOYED", now().minusMinutes(30));
            help(SECOND_STUDENT_ID, "OPEN");
            application(SECOND_STUDENT_ID, "SUBMITTED");

            service.recalculate(STUDENT_ID);

            assertStored(STUDENT_ID, "SEEKING", "WAITING");
        });
    }

    @Test
    void absentStatusIsInsertedAndExistingStatusIsUpdatedWithoutDuplicateRows() {
        rolledBack(tx -> {
            neutralize(STUDENT_ID, now().minusHours(1));
            jdbc.update("DELETE FROM student_status WHERE student_id = ?", STUDENT_ID);

            service.recalculate(STUDENT_ID);
            assertStored(STUDENT_ID, "SEEKING", "WAITING");
            help(STUDENT_ID, "OPEN");
            service.recalculate(STUDENT_ID);

            assertStored(STUDENT_ID, "NEED_HELP", null);
            assertEquals(1L, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM student_status WHERE student_id = ?", Long.class, STUDENT_ID));
        });
    }

    @Test
    void recordResponseKeepsNewerTimestampAndRecalculatesInsideCallerTransaction() {
        rolledBack(tx -> {
            neutralize(STUDENT_ID, now().minusHours(2));
            LocalDateTime originalTime = lastResponse(STUDENT_ID);
            service.recordResponse(STUDENT_ID, originalTime.minusDays(1));
            assertEquals(originalTime, lastResponse(STUDENT_ID), "늦게 도착한 이벤트로 시각을 되돌리지 않는다");

            LocalDateTime newerTime = originalTime.plusHours(1);
            help(STUDENT_ID, "OPEN");
            service.recordResponse(STUDENT_ID, newerTime);

            assertEquals(newerTime, lastResponse(STUDENT_ID));
            assertStored(STUDENT_ID, "NEED_HELP", null);
            service.recordResponse(STUDENT_ID, newerTime);
            assertEquals(newerTime, lastResponse(STUDENT_ID), "같은 시각의 이벤트도 시각을 유지한다");
        });
    }

    @Test
    void responseEventReplacesCompletionFallbackWithTheActualResponseTime() {
        rolledBack(tx -> {
            neutralize(STUDENT_ID, null);
            completedOn(STUDENT_ID, now().toLocalDate().minusDays(30));
            service.recalculate(STUDENT_ID);
            assertStored(STUDENT_ID, "CHECK_REQUIRED", null);
            LocalDateTime respondedAt = now().minusMinutes(1);

            service.recordResponse(STUDENT_ID, respondedAt);

            assertEquals(respondedAt, lastResponse(STUDENT_ID));
            assertStored(STUDENT_ID, "SEEKING", "WAITING");
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 7, 8})
    void nullResponseUsesCompletionDateAtSeoulMidnightIncludingTheSevenDayBoundary(int days) {
        // DATE를 DATETIME으로 읽는 실제 MySQL 매핑과 정확한 자정 경계를 고정된 서울 시각으로 검증한다.
        LocalDateTime at = LocalDateTime.of(2026, 10, 27, 0, 0);
        rolledBack(tx -> {
            neutralize(STUDENT_ID, null);
            completedOn(STUDENT_ID, at.toLocalDate().minusDays(days));

            serviceAt(at).recalculate(STUDENT_ID);

            assertStored(STUDENT_ID, days >= 7 ? "CHECK_REQUIRED" : "SEEKING", days >= 7 ? null : "WAITING");
            assertNull(lastResponse(STUDENT_ID), "수료일을 마지막 응답 시각에 대신 저장하지 않는다");
        });
    }

    @Test
    void recentResponseTakesPrecedenceOverAnOldCompletionDate() {
        rolledBack(tx -> {
            LocalDateTime response = now().minusHours(1);
            neutralize(STUDENT_ID, response);
            completedOn(STUDENT_ID, now().toLocalDate().minusDays(30));

            service.recalculate(STUDENT_ID);

            assertStored(STUDENT_ID, "SEEKING", "WAITING");
            assertEquals(response, lastResponse(STUDENT_ID));
        });
    }

    @Test
    void anExistingResponseIsUsedEvenWhenCompletionDateIsMoreRecent() {
        rolledBack(tx -> {
            neutralize(STUDENT_ID, now().minusDays(8));
            completedOn(STUDENT_ID, now().toLocalDate());

            service.recalculate(STUDENT_ID);

            assertStored(STUDENT_ID, "CHECK_REQUIRED", null);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMPLOYED", "NEED_HELP"})
    void employmentAndOpenHelpHavePriorityOverStaleCompletionWithoutAResponse(String status) {
        rolledBack(tx -> {
            neutralize(STUDENT_ID, null);
            completedOn(STUDENT_ID, now().toLocalDate().minusDays(30));
            if ("EMPLOYED".equals(status)) {
                item(STUDENT_ID, "PASS", "ALREADY_EMPLOYED", now().minusDays(1));
                help(STUDENT_ID, "OPEN");
            } else {
                help(STUDENT_ID, "OPEN");
            }

            service.recalculate(STUDENT_ID);

            assertStored(STUDENT_ID, status, null);
            assertNull(lastResponse(STUDENT_ID));
        });
    }

    @Test
    void completionDateCanRequireCheckingEvenWithoutAnyRecommendationHistory() {
        long studentCount = jdbc.queryForObject("SELECT COUNT(*) FROM student", Long.class);
        long statusCount = jdbc.queryForObject("SELECT COUNT(*) FROM student_status", Long.class);
        try {
            rolledBack(tx -> {
                long studentId = insert("""
                        INSERT INTO student (course_id, name, completed_on)
                        SELECT course_id, '상태 검증 모의', ? FROM student WHERE id = ?
                        """, now().toLocalDate().minusDays(30), STUDENT_ID);
                assertEquals(0L, count("SELECT COUNT(*) FROM recommendation WHERE student_id = ?", studentId));
                assertNull(lastResponse(studentId));

                service.recalculate(studentId);

                assertStored(studentId, "CHECK_REQUIRED", null);
            });
        } finally {
            assertEquals(studentCount, jdbc.queryForObject("SELECT COUNT(*) FROM student", Long.class));
            assertEquals(statusCount, jdbc.queryForObject("SELECT COUNT(*) FROM student_status", Long.class));
        }
    }

    @Test
    void missingStudentIsRejectedBeforeStatusOrResponseTimeCanBeWritten() {
        rolledBack(tx -> {
            long missingId = Long.MAX_VALUE;
            assertEquals(0L, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM student WHERE id = ?", Long.class, missingId));

            BusinessException recalculateFailure = assertThrows(BusinessException.class,
                    () -> service.recalculate(missingId));
            BusinessException responseFailure = assertThrows(BusinessException.class,
                    () -> service.recordResponse(missingId, now()));

            assertEquals(ErrorCode.STUDENT_NOT_FOUND, recalculateFailure.getErrorCode());
            assertEquals(ErrorCode.STUDENT_NOT_FOUND, responseFailure.getErrorCode());
            assertEquals(0L, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM student_status WHERE student_id = ?", Long.class, missingId));
        });
    }

    @Test
    void repeatedCallsInParentTransactionSeeNewSourceRowsAndDoNotReuseEarlierMyBatisResult() {
        rolledBack(tx -> {
            neutralize(STUDENT_ID, now().minusHours(1));
            // 부모 트랜잭션에 일반 SELECT 및 MyBatis 조회 결과가 이미 있는 상황.
            jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_item", Long.class);
            service.recalculate(STUDENT_ID);
            assertStored(STUDENT_ID, "SEEKING", "WAITING");

            item(STUDENT_ID, "PASS", "ALREADY_EMPLOYED", now().minusMinutes(30));
            service.recalculate(STUDENT_ID);

            assertStored(STUDENT_ID, "EMPLOYED", null);
        });
    }

    @Test
    void existingStudentsMatchCurrentSourceDataRatherThanHardCodedSeedCounts() {
        List<Map<String, Object>> previousStatuses = rows("SELECT * FROM student_status ORDER BY student_id");
        try {
            rolledBack(tx -> {
                List<Long> ids = jdbc.queryForList("SELECT id FROM student ORDER BY id", Long.class);
                assertTrue(ids.contains(STUDENT_ID) && ids.contains(SECOND_STUDENT_ID));
                for (long id : ids) {
                    Expected before = expectedFromSources(id, now());
                    service.recalculate(id);
                    Expected after = expectedFromSources(id, now());
                    Expected actual = stored(id);
                    assertTrue(actual.equals(before) || actual.equals(after),
                            "학생 " + id + "의 상태가 현재 원천 데이터의 판정과 일치해야 한다");
                }
            });
        } finally {
            assertTrue(previousStatuses.equals(rows("SELECT * FROM student_status ORDER BY student_id")),
                    "전체 학생 상태와 갱신 시각도 롤백 후 원래 값이어야 한다");
        }
    }

    private Expected expectedFromSources(long studentId, LocalDateTime at) {
        if (count("""
                SELECT COUNT(*) FROM recommendation_item i
                JOIN recommendation r ON r.id = i.recommendation_id
                WHERE r.student_id = ? AND i.response = 'PASS' AND i.pass_reason = 'ALREADY_EMPLOYED'
                """, studentId) > 0) {
            return new Expected("EMPLOYED", null);
        }
        if (count("SELECT COUNT(*) FROM help_request WHERE student_id = ? AND status = 'OPEN'", studentId) > 0) {
            return new Expected("NEED_HELP", null);
        }
        List<Long> latestItemCounts = jdbc.queryForList("""
                SELECT COUNT(i.id)
                FROM recommendation r
                LEFT JOIN recommendation_item i ON i.recommendation_id = r.id
                WHERE r.student_id = ?
                GROUP BY r.id, r.created_at
                ORDER BY r.created_at DESC, r.id DESC
                LIMIT 1
                """, Long.class, studentId);
        if (!latestItemCounts.isEmpty() && latestItemCounts.get(0) == 0L) {
            return new Expected("CHECK_REQUIRED", null);
        }
        LocalDateTime inactivitySince = lastResponse(studentId);
        if (inactivitySince == null) {
            LocalDate completed = jdbc.queryForObject("SELECT completed_on FROM student WHERE id = ?",
                    LocalDate.class, studentId);
            assertNotNull(completed);
            inactivitySince = completed.atStartOfDay();
        }
        if (!inactivitySince.isAfter(at.minusDays(7))) {
            return new Expected("CHECK_REQUIRED", null);
        }
        if (count("""
                SELECT COUNT(*) FROM application
                WHERE student_id = ? AND status IN ('SUBMITTED', 'APPROVED', 'DELIVERED')
                """, studentId) > 0) {
            return new Expected("SEEKING", "APPLYING");
        }
        List<String> lastPass = jdbc.queryForList("""
                SELECT i.pass_reason FROM recommendation_item i
                JOIN recommendation r ON r.id = i.recommendation_id
                WHERE r.student_id = ? AND i.response = 'PASS'
                ORDER BY i.responded_at DESC, i.id DESC LIMIT 1
                """, String.class, studentId);
        return new Expected("SEEKING", !lastPass.isEmpty() && "OTHER".equals(lastPass.get(0)) ? "OTHER" : "WAITING");
    }

    private void neutralize(long studentId, LocalDateTime lastResponse) {
        assertEquals(studentId, jdbc.queryForObject(
                "SELECT id FROM student WHERE id = ? FOR UPDATE", Long.class, studentId));
        jdbc.update("""
                UPDATE recommendation_item i JOIN recommendation r ON r.id = i.recommendation_id
                SET i.response = 'NONE', i.pass_reason = NULL, i.pass_note = NULL, i.responded_at = NULL
                WHERE r.student_id = ?
                """, studentId);
        jdbc.update("UPDATE help_request SET status = 'CLOSED' WHERE student_id = ?", studentId);
        jdbc.update("UPDATE application SET status = 'DRAFT' WHERE student_id = ?", studentId);
        jdbc.update("UPDATE student SET last_response_at = ? WHERE id = ?", lastResponse, studentId);
        // 기존 DB가 0건 추천을 포함해도 각 테스트는 명시적으로 0건을 만드는 경우만 그 신호를 갖는다.
        item(studentId, "NONE", null, null);
    }

    private void completedOn(long studentId, LocalDate completed) {
        assertEquals(1, jdbc.update("UPDATE student SET completed_on = ? WHERE id = ?", completed, studentId));
    }

    private StatusService serviceAt(LocalDateTime at) {
        // 외부 rolledBack 트랜잭션 안에서 실제 Mapper를 그대로 사용한다.
        return new StatusService(mapper, Clock.fixed(at.atZone(SEOUL).toInstant(), SEOUL));
    }

    private long item(long studentId, String response, String passReason, LocalDateTime respondedAt) {
        long recommendationId = insert("""
                INSERT INTO recommendation (student_id, trigger_type, created_at) VALUES (?, 'VISIT', ?)
                """, studentId, now());
        return insert("""
                INSERT INTO recommendation_item
                    (recommendation_id, job_posting_id, rank_no, reasons, response, pass_reason, responded_at)
                VALUES (?, ?, 1, '[]', ?, ?, ?)
                """, recommendationId, jobPostingId, response, passReason, respondedAt);
    }

    private void help(long studentId, String status) {
        jdbc.update("INSERT INTO help_request (student_id, status) VALUES (?, ?)", studentId, status);
    }

    private void application(long studentId, String status) {
        long itemId = item(studentId, "APPLY", null, now().minusHours(1));
        jdbc.update("""
                INSERT INTO application (student_id, recommendation_item_id, job_posting_id, status)
                VALUES (?, ?, ?, ?)
                """, studentId, itemId, jobPostingId, status);
    }

    private long insert(String sql, Object... values) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        assertEquals(1, jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            return statement;
        }, keys));
        assertNotNull(keys.getKey(), "테스트 행의 생성된 키가 반환되어야 한다");
        return keys.getKey().longValue();
    }

    private void rolledBack(Consumer<TransactionStatus> work) {
        Baseline before = baseline();
        try {
            transactions.executeWithoutResult(tx -> {
                tx.setRollbackOnly();
                work.accept(tx);
            });
        } finally {
            assertBaselineUnchanged(before);
        }
    }

    private Baseline baseline() {
        return new Baseline(List.of(
                rows("SELECT * FROM student WHERE id IN (1, 2) ORDER BY id"),
                rows("SELECT * FROM student_status WHERE student_id IN (1, 2) ORDER BY student_id"),
                rows("SELECT * FROM recommendation WHERE student_id IN (1, 2) ORDER BY id"),
                rows("""
                        SELECT i.* FROM recommendation_item i
                        JOIN recommendation r ON r.id = i.recommendation_id
                        WHERE r.student_id IN (1, 2) ORDER BY i.id
                        """),
                rows("SELECT * FROM help_request WHERE student_id IN (1, 2) ORDER BY id"),
                rows("SELECT * FROM application WHERE student_id IN (1, 2) ORDER BY id")));
    }

    private void assertBaselineUnchanged(Baseline expected) {
        assertTrue(expected.equals(baseline()), "롤백 후 학생 1, 2와 관련 원문 행이 모두 보존되어야 한다");
    }

    private List<Map<String, Object>> rows(String sql, Object... args) {
        return jdbc.queryForList(sql, args).stream().map(row -> {
            Map<String, Object> normalized = new LinkedHashMap<>();
            row.forEach((key, value) -> normalized.put(key,
                    value instanceof byte[] bytes ? Base64.getEncoder().encodeToString(bytes) : value));
            return normalized;
        }).toList();
    }

    private long count(String sql, long studentId) {
        return jdbc.queryForObject(sql, Long.class, studentId);
    }

    private LocalDateTime lastResponse(long studentId) {
        return jdbc.queryForObject("SELECT last_response_at FROM student WHERE id = ?",
                LocalDateTime.class, studentId);
    }

    private Expected stored(long studentId) {
        return jdbc.queryForObject("SELECT status, sub_status FROM student_status WHERE student_id = ?",
                (rs, rowNumber) -> new Expected(rs.getString("status"), rs.getString("sub_status")), studentId);
    }

    private void assertStored(long studentId, String status, String subStatus) {
        assertEquals(new Expected(status, subStatus), stored(studentId));
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(SEOUL).withNano(0);
    }

    private record Expected(String status, String subStatus) {}

    private record Baseline(List<List<Map<String, Object>>> tables) {}
}
