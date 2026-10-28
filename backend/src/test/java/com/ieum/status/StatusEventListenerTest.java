// 주인: BE-B
package com.ieum.status;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ieum.application.ApplicationSubmittedEvent;
import com.ieum.coordinator.ApplicationReviewChangedEvent;
import com.ieum.helprequest.HelpRequestChangedEvent;
import com.ieum.recommendation.RecommendationCreatedEvent;
import com.ieum.recommendation.RecommendationRespondedEvent;
import java.sql.Connection;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 Spring 이벤트·트랜잭션 프록시로 원 저장과 상태 저장의 커밋을 분리한다. */
class StatusEventListenerTest {

    private final StatusMapper mapper = mock(StatusMapper.class);
    private final DataSource dataSource = mock(DataSource.class);
    private final Connection originalConnection = mock(Connection.class);
    private final Connection statusConnection = mock(Connection.class);
    private AnnotationConfigApplicationContext context;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() throws Exception {
        when(dataSource.getConnection()).thenReturn(originalConnection, statusConnection);
        when(originalConnection.getAutoCommit()).thenReturn(true);
        when(statusConnection.getAutoCommit()).thenReturn(true);
        when(mapper.findLatestRecommendationIdUnlocked(any())).thenReturn(null);
        PlatformTransactionManager manager = new DataSourceTransactionManager(dataSource);
        transactions = new TransactionTemplate(manager);
        context = new AnnotationConfigApplicationContext();
        context.register(TransactionConfiguration.class);
        context.registerBean(PlatformTransactionManager.class, () -> manager);
        context.registerBean(TransactionalEventListenerFactory.class);
        context.registerBean(StatusService.class, () -> new StatusService(mapper));
        context.registerBean(StatusEventListener.class);
        context.refresh();
    }

    @AfterEach
    void close() {
        context.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESPONDED", "SUBMITTED", "HELP_REQUEST", "REVIEW", "CREATED_EMPTY", "CREATED_NONEMPTY"})
    void everyCommittedSourceEventRecalculatesWithAnotherConnection(String eventType) throws Exception {
        var student = new StatusData.Student();
        student.setStudentId(7L);
        student.setInactivitySince(LocalDateTime.now().plusDays(1));
        when(mapper.lockStudent(7L)).thenAnswer(invocation -> {
            verify(originalConnection).commit();
            ConnectionHolder holder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
            assertSame(statusConnection, holder.getConnection());
            return student;
        });

        transactions.executeWithoutResult(status -> {
            context.publishEvent(event(eventType));
            verifyNoInteractions(mapper);
        });

        verify(mapper).upsertStatus(eq(7L), argThat(decision ->
                "SEEKING".equals(decision.status()) && "WAITING".equals(decision.subStatus())));
        verify(mapper, never()).updateLastResponseAt(any(), any());
        verify(statusConnection).commit();
        verify(statusConnection).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESPONDED", "SUBMITTED", "HELP_REQUEST", "REVIEW", "CREATED_EMPTY", "CREATED_NONEMPTY"})
    void rolledBackOriginalWritesDoNotRecalculateStatus(String eventType) throws Exception {
        transactions.executeWithoutResult(status -> {
            context.publishEvent(event(eventType));
            status.setRollbackOnly();
        });

        verifyNoInteractions(mapper);
        verify(originalConnection).rollback();
        verify(statusConnection, never()).commit();
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESPONDED", "SUBMITTED", "HELP_REQUEST", "REVIEW", "CREATED_EMPTY", "CREATED_NONEMPTY"})
    void failedStatusWriteCannotFailAnyAlreadyCommittedSource(String eventType) throws Exception {
        when(mapper.lockStudent(7L)).thenThrow(new IllegalStateException("simulated unavailable database"));

        assertDoesNotThrow(() -> transactions.executeWithoutResult(status ->
                context.publishEvent(event(eventType))));

        verify(originalConnection).commit();
        verify(statusConnection).rollback();
        verify(mapper, never()).upsertStatus(any(), any());
    }

    @Test
    void oneFailedEventDoesNotStopAnotherCommittedStudentsRecalculation() throws Exception {
        Connection nextStatusConnection = mock(Connection.class);
        when(nextStatusConnection.getAutoCommit()).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(originalConnection, statusConnection, nextStatusConnection);
        when(mapper.lockStudent(7L)).thenThrow(new IllegalStateException("simulated unavailable student"));
        var nextStudent = new StatusData.Student();
        nextStudent.setStudentId(8L);
        nextStudent.setInactivitySince(LocalDateTime.now().plusDays(1));
        when(mapper.lockStudent(8L)).thenReturn(nextStudent);

        assertDoesNotThrow(() -> transactions.executeWithoutResult(status -> {
            context.publishEvent(new HelpRequestChangedEvent(7L, 14L));
            context.publishEvent(new ApplicationReviewChangedEvent(8L, 15L));
        }));

        verify(originalConnection).commit();
        verify(statusConnection).rollback();
        verify(nextStatusConnection).commit();
        verify(mapper).upsertStatus(eq(8L), any(StatusDecision.class));
    }

    private static Object event(String eventType) {
        return switch (eventType) {
            case "RESPONDED" -> new RecommendationRespondedEvent(7L, 11L, 12L, "PASS", null);
            case "SUBMITTED" -> new ApplicationSubmittedEvent(7L, 13L);
            case "HELP_REQUEST" -> new HelpRequestChangedEvent(7L, 14L);
            case "REVIEW" -> new ApplicationReviewChangedEvent(7L, 13L);
            case "CREATED_EMPTY" -> new RecommendationCreatedEvent(7L, 12L, "VISIT", 0);
            case "CREATED_NONEMPTY" -> new RecommendationCreatedEvent(7L, 12L, "REFRESH", 3);
            default -> throw new IllegalArgumentException("알 수 없는 테스트 이벤트");
        };
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionConfiguration {}
}
