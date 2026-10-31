// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ProfileBatchExtractionServiceTest {
    private ProfileMapper mapper;
    private ProfileExtractionWorker worker;
    private ProfileBatchExtractionService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProfileMapper.class);
        worker = mock(ProfileExtractionWorker.class);
        service = new ProfileBatchExtractionService(mapper, worker);
    }

    @Test
    void emptyTargetsReturnZeroWithoutCallingAiWorker() {
        when(mapper.findStudentIdsWithoutCompetencyProfile()).thenReturn(List.of());

        ProfileBatchExtractionResult result = service.extractMissing();

        assertEquals(0, result.targetCount());
        assertEquals(0, result.extractedCount());
        assertEquals(List.of(), result.failedStudentIds());
        assertEquals(ZoneOffset.ofHours(9), result.extractedAt().getOffset());
        verifyNoInteractions(worker);
    }

    @Test
    void oneFailureDoesNotStopLaterStudentsAndOnlySavedProfilesCount() {
        when(mapper.findStudentIdsWithoutCompetencyProfile()).thenReturn(List.of(1L, 7L, 9L));
        when(worker.extractIfMissing(1L)).thenReturn(true);
        when(worker.extractIfMissing(7L)).thenThrow(new IllegalStateException("모의 AI 장애"));
        when(worker.extractIfMissing(9L)).thenReturn(true);

        ProfileBatchExtractionResult result = service.extractMissing();

        assertEquals(3, result.targetCount());
        assertEquals(2, result.extractedCount());
        assertEquals(List.of(7L), result.failedStudentIds());
        InOrder order = inOrder(worker);
        order.verify(worker).extractIfMissing(1L);
        order.verify(worker).extractIfMissing(7L);
        order.verify(worker).extractIfMissing(9L);
        verifyNoMoreInteractions(worker);
    }

    @Test
    void profileCreatedAfterTargetLookupIsSkippedWithoutCountingFailure() {
        when(mapper.findStudentIdsWithoutCompetencyProfile()).thenReturn(List.of(1L, 2L));
        when(worker.extractIfMissing(1L)).thenReturn(false);
        when(worker.extractIfMissing(2L)).thenReturn(true);

        ProfileBatchExtractionResult result = service.extractMissing();

        assertEquals(2, result.targetCount());
        assertEquals(1, result.extractedCount());
        assertEquals(List.of(), result.failedStudentIds());
    }

    @Test
    void repeatedCallsRetryOnlyRemainingMissingProfilesThenDoNothing() {
        when(mapper.findStudentIdsWithoutCompetencyProfile())
                .thenReturn(List.of(1L, 7L), List.of(7L), List.of());
        when(worker.extractIfMissing(1L)).thenReturn(true);
        when(worker.extractIfMissing(7L))
                .thenThrow(new IllegalStateException("모의 첫 요청 실패"))
                .thenReturn(true);

        ProfileBatchExtractionResult first = service.extractMissing();
        ProfileBatchExtractionResult retry = service.extractMissing();
        ProfileBatchExtractionResult complete = service.extractMissing();

        assertEquals(2, first.targetCount());
        assertEquals(1, first.extractedCount());
        assertEquals(List.of(7L), first.failedStudentIds());
        assertEquals(1, retry.targetCount());
        assertEquals(1, retry.extractedCount());
        assertEquals(List.of(), retry.failedStudentIds());
        assertEquals(0, complete.targetCount());
        assertEquals(0, complete.extractedCount());
        assertEquals(List.of(), complete.failedStudentIds());
        verify(worker).extractIfMissing(1L);
        verify(worker, times(2)).extractIfMissing(7L);
        verifyNoMoreInteractions(worker);
    }

    @Test
    void overlappingRequestsWaitAndNeverRunTwoExtractionsAtOnce() throws Exception {
        when(mapper.findStudentIdsWithoutCompetencyProfile())
                .thenReturn(List.of(1L), List.of(2L));
        CountDownLatch firstExtracting = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondRequested = new CountDownLatch(1);
        CountDownLatch secondExtracting = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        when(worker.extractIfMissing(anyLong())).thenAnswer(invocation -> {
            int count = active.incrementAndGet();
            maximumActive.accumulateAndGet(count, Math::max);
            try {
                if (invocation.<Long>getArgument(0).equals(1L)) {
                    firstExtracting.countDown();
                    assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
                } else {
                    secondExtracting.countDown();
                }
                return true;
            } finally {
                active.decrementAndGet();
            }
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(service::extractMissing);
            assertTrue(firstExtracting.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> {
                secondRequested.countDown();
                return service.extractMissing();
            });
            assertTrue(secondRequested.await(5, TimeUnit.SECONDS));
            assertFalse(secondExtracting.await(200, TimeUnit.MILLISECONDS),
                    "첫 요청이 AI를 기다리는 동안 두 번째 요청은 추출을 시작하면 안 된다");
            releaseFirst.countDown();

            assertEquals(1, first.get(5, TimeUnit.SECONDS).extractedCount());
            assertEquals(1, second.get(5, TimeUnit.SECONDS).extractedCount());
            assertEquals(1, maximumActive.get());
            assertEquals(0, active.get());
            assertEquals(0, secondExtracting.getCount());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
