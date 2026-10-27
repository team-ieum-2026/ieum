// 주인: BE-B
package com.ieum.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기존 응답의 item→student 잠금과 상태 배치의 student 잠금을 실제 MySQL에서 겹친다.
 * 두 쓰기 트랜잭션 모두 롤백한다. 원천 데이터나 상태 행을 커밋하지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "IEUM_STATUS_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]",
        "spring.sql.init.mode=never",
        "app.ai.mock=true",
        "app.seoul.sync-cron=-",
        "app.status.recalculate-cron=-"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StatusConcurrencyDatabaseTest {

    @Autowired private StatusService service;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;
    @Autowired private SqlSessionTemplate sqlSession;
    @MockitoSpyBean private StatusMapper mapper;

    @Test
    void backgroundRecalculationDoesNotDeadlockAnItemFirstResponseTransaction() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.setQueryTimeout(8);
        List<String> engines = jdbc.queryForList("""
                SELECT ENGINE FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME IN ('student', 'student_status', 'recommendation', 'recommendation_item')
                """, String.class);
        assertEquals(4, engines.size(), "기존 상태 판정 스키마가 준비되어 있어야 한다");
        assertTrue(engines.stream().allMatch("InnoDB"::equalsIgnoreCase),
                "모든 테스트 쓰기를 롤백할 수 있는 InnoDB에서만 실행한다");
        List<Owner> owners = jdbc.query("""
                SELECT i.id, r.student_id
                FROM recommendation_item i
                JOIN recommendation r ON r.id = i.recommendation_id
                ORDER BY i.id LIMIT 1
                """, (rs, row) -> new Owner(rs.getLong("id"), rs.getLong("student_id")));
        assertEquals(1, owners.size(), "잠금 순서 검증에 사용할 기존 추천 응답 한 건이 있어야 한다");
        Owner owner = owners.get(0);
        List<List<Map<String, Object>>> before = snapshot(jdbc, owner);
        CountDownLatch itemLocked = new CountDownLatch(1);
        CountDownLatch studentLocked = new CountDownLatch(1);
        CountDownLatch studentUpdateStarted = new CountDownLatch(1);
        // Spring spy가 감싼 인터페이스 대신 같은 스레드의 트랜잭션을 쓰는 원본 MyBatis 프록시다.
        StatusMapper delegate = sqlSession.getMapper(StatusMapper.class);

        doAnswer(invocation -> {
            StatusData.Student student = delegate.lockStudent(owner.studentId());
            studentLocked.countDown();
            await(studentUpdateStarted, "원 응답의 student 갱신 시도가 시작되어야 한다");
            return student;
        }).when(mapper).lockStudent(owner.studentId());
        doAnswer(invocation -> {
            delegate.upsertStatus(invocation.getArgument(0, Long.class),
                    invocation.getArgument(1, StatusDecision.class));
            // 실제 상태 INSERT/UPDATE까지 확인하되 별도 트랜잭션 역시 반드시 롤백한다.
            throw new RollbackForVerification();
        }).when(mapper).upsertStatus(eq(owner.studentId()), any(StatusDecision.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> response = executor.submit(() -> {
                TransactionTemplate source = new TransactionTemplate(transactionManager);
                source.setTimeout(8);
                source.executeWithoutResult(status -> {
                    status.setRollbackOnly();
                    // BE-A 응답 저장과 같은 순서다. 외래 키는 변경하지 않는다.
                    assertEquals(1, jdbc.update("""
                            UPDATE recommendation_item
                            SET response = 'PASS', pass_reason = 'OTHER', pass_note = NULL, responded_at = NOW()
                            WHERE id = ?
                            """, owner.itemId()));
                    itemLocked.countDown();
                    await(studentLocked, "상태 재계산이 student 행을 먼저 잠가야 한다");
                    studentUpdateStarted.countDown();
                    assertEquals(1, jdbc.update(
                            "UPDATE student SET last_response_at = NOW() WHERE id = ?", owner.studentId()));
                });
            });
            await(itemLocked, "원 응답이 recommendation_item 행을 먼저 잠가야 한다");
            Future<?> recalculation = executor.submit(() ->
                    assertThrows(RollbackForVerification.class,
                            () -> service.recalculateCommitted(owner.studentId())));

            // 과거 student→item 잠금 구현이면 교착으로 둘 중 하나가 DB 예외를 받는다.
            recalculation.get(10, TimeUnit.SECONDS);
            response.get(10, TimeUnit.SECONDS);
        } finally {
            studentLocked.countDown();
            studentUpdateStarted.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "모든 검증 트랜잭션이 끝나야 한다");
            assertTrue(before.equals(snapshot(jdbc, owner)), "기존 수료생·응답·상태 행이 모두 보존되어야 한다");
        }
    }

    private static void await(CountDownLatch latch, String message) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), message);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 검증 대기가 중단되었어요.", exception);
        }
    }

    private static List<List<Map<String, Object>>> snapshot(JdbcTemplate jdbc, Owner owner) {
        return List.of(
                rows(jdbc, "SELECT * FROM student WHERE id = ?", owner.studentId()),
                rows(jdbc, "SELECT * FROM recommendation_item WHERE id = ?", owner.itemId()),
                rows(jdbc, "SELECT * FROM student_status WHERE student_id = ?", owner.studentId()));
    }

    private static List<Map<String, Object>> rows(JdbcTemplate jdbc, String sql, long id) {
        return jdbc.queryForList(sql, id).stream().map(row -> {
            Map<String, Object> normalized = new LinkedHashMap<>();
            row.forEach((key, value) -> normalized.put(key,
                    value instanceof byte[] bytes ? Base64.getEncoder().encodeToString(bytes) : value));
            return normalized;
        }).toList();
    }

    private record Owner(long itemId, long studentId) {}

    private static final class RollbackForVerification extends RuntimeException {}
}
