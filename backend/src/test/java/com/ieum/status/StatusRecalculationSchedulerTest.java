// 주인: BE-B
package com.ieum.status;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class StatusRecalculationSchedulerTest {

    private final StatusMapper mapper = mock(StatusMapper.class);
    private final StatusService service = mock(StatusService.class);
    private final StatusRecalculationScheduler scheduler = new StatusRecalculationScheduler(mapper, service);

    @Test
    void dailyRunProcessesEveryStudentEvenAfterOneRecalculationFails() {
        when(mapper.findStudentIds()).thenReturn(List.of(1L, 2L, 3L));
        doThrow(new IllegalStateException("simulated student failure")).when(service).recalculateCommitted(2L);

        assertDoesNotThrow(scheduler::recalculateAll);

        InOrder order = inOrder(service);
        order.verify(service).recalculateCommitted(1L);
        order.verify(service).recalculateCommitted(2L);
        order.verify(service).recalculateCommitted(3L);
    }

    @Test
    void missingStudentsDoNotStartAnyStatusTransaction() {
        when(mapper.findStudentIds()).thenReturn(List.of());

        scheduler.recalculateAll();

        verifyNoInteractions(service);
    }

    @Test
    void targetQueryFailureIsIsolatedFromTheScheduler() {
        when(mapper.findStudentIds()).thenThrow(new IllegalStateException("simulated unavailable database"));

        assertDoesNotThrow(scheduler::recalculateAll);

        verifyNoInteractions(service);
    }
}
