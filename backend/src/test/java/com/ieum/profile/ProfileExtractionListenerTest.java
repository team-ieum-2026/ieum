// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.TransactionTemplate;

class ProfileExtractionListenerTest {
    private final ProfileExtractionWorker worker = mock(ProfileExtractionWorker.class);
    private final QueuedExecutor executor = new QueuedExecutor();
    private final ProfileExtractionListener listener = new ProfileExtractionListener(worker, executor);
    private final TransactionTemplate transactions = new TransactionTemplate(new ProfileTestTransactionManager());
    private AnnotationConfigApplicationContext context;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean(TransactionalEventListenerFactory.class);
        context.registerBean(ProfileExtractionListener.class, () -> listener);
        context.refresh();
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void queuesExtractionOnlyAfterCommitAndDoesNotWaitForAi() {
        transactions.executeWithoutResult(status -> {
            context.publishEvent(new ProfileChangedEvent(7L));
            assertEquals(0, executor.size());
            verifyNoInteractions(worker);
        });

        assertEquals(1, executor.size());
        verifyNoInteractions(worker);
        executor.runNext();
        verify(worker).refresh(7L);
    }

    @Test
    void rollbackDoesNotTriggerAi() {
        transactions.executeWithoutResult(status -> {
            context.publishEvent(new ProfileChangedEvent(7L));
            status.setRollbackOnly();
        });

        assertEquals(0, executor.size());
        verifyNoInteractions(worker);
    }

    @Test
    void repeatedQueuedChangesToSameStudentAreCoalesced() {
        transactions.executeWithoutResult(status -> {
            context.publishEvent(new ProfileChangedEvent(7L));
            context.publishEvent(new ProfileChangedEvent(7L));
            context.publishEvent(new ProfileChangedEvent(8L));
        });

        assertEquals(2, executor.size());
        executor.runNext();
        executor.runNext();
        verify(worker).refresh(7L);
        verify(worker).refresh(8L);
    }

    @Test
    void failedAiDoesNotEscapeAndNextChangeRetries() {
        doThrow(new IllegalStateException("simulated unavailable server")).doNothing().when(worker).refresh(7L);

        publishCommitted(7L);
        executor.runNext();
        publishCommitted(7L);
        executor.runNext();

        verify(worker, times(2)).refresh(7L);
    }

    @Test
    void executorShutdownDoesNotFailAlreadyCommittedSave() {
        executor.shutdown();
        publishCommitted(7L);
        assertEquals(0, executor.size());
        verifyNoInteractions(worker);
    }

    private void publishCommitted(Long studentId) {
        transactions.executeWithoutResult(status -> context.publishEvent(new ProfileChangedEvent(studentId)));
    }

    private static final class QueuedExecutor extends AbstractExecutorService {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private boolean shutdown;

        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> remaining = List.copyOf(tasks);
            tasks.clear();
            return remaining;
        }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown && tasks.isEmpty(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
        @Override public void execute(Runnable task) {
            if (shutdown) throw new RejectedExecutionException();
            tasks.add(task);
        }
        int size() { return tasks.size(); }
        void runNext() {
            assertTrue(!tasks.isEmpty());
            tasks.remove().run();
        }
    }
}
