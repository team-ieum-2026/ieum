// 주인: BE-B
package com.ieum.status;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StudentStatusServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 12, 0);
    private final StatusMapper mapper = mock(StatusMapper.class);
    private final StatusService service = new StatusService(mapper,
            Clock.fixed(Instant.parse("2026-11-02T03:00:00Z"), ZoneOffset.UTC));

    @ParameterizedTest
    @ValueSource(strings = {"EMPLOYED", "SEEKING", "RESTING"})
    void storesTheChoiceBeforeRecalculationAndKeepsOpenRequests(String selected) {
        var student = new StatusData.Student();
        student.setStudentId(7L);
        student.setInactivitySince(NOW);
        when(mapper.lockStudent(7L)).thenReturn(student);
        when(mapper.findResponsesUnlocked(7L)).thenReturn(List.of());
        when(mapper.findOpenHelpRequestIdsUnlocked(7L)).thenReturn(List.of(11L, 12L));
        when(mapper.findActiveApplicationIdsUnlocked(7L)).thenReturn(List.of());
        when(mapper.findLatestRecommendationIdUnlocked(7L)).thenReturn(null);
        var stored = new AtomicReference<StatusData.Intent>();
        when(mapper.insertIntent(any())).thenAnswer(call -> { stored.set(call.getArgument(0)); return 1; });
        when(mapper.findLatestIntentUnlocked(7L)).thenAnswer(call -> stored.get());
        var current = new StatusData.CurrentStatus();
        current.setStudentId(7L);
        current.setStatus(selected);
        current.setUpdatedAt(NOW);
        when(mapper.findStudentStatus(7L)).thenReturn(current);

        var result = service.selectStatus(7L, new StudentStatusRequest(selected));

        assertEquals(selected, result.status());
        assertEquals(7L, stored.get().getStudentId());
        assertEquals(NOW, stored.get().getSelectedAt());
        assertEquals("+09:00", result.updatedAt().getOffset().toString());
        var order = inOrder(mapper);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).insertIntent(any());
        order.verify(mapper).updateLastResponseAt(7L, NOW);
        order.verify(mapper).upsertStatus(eq(7L), argThat(d -> selected.equals(d.status())));
        verify(mapper, never()).findResponses(any());
        verify(mapper, never()).findOpenHelpRequestIds(any());
        // 상태 선택 서비스는 도움 요청을 종료하는 매퍼에 의존하지 않는다.
    }

    @Test
    void rejectedChoiceCannotCreateHistory() {
        var failure = assertThrows(BusinessException.class,
                () -> service.selectStatus(7L, new StudentStatusRequest(null)));
        assertEquals(ErrorCode.INVALID_REQUEST, failure.getErrorCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void failedHistoryInsertCannotChangeTheResponseTimeOrStatus() {
        when(mapper.lockStudent(7L)).thenReturn(new StatusData.Student());
        when(mapper.insertIntent(any())).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.selectStatus(7L, new StudentStatusRequest("RESTING")));
        verify(mapper, never()).updateLastResponseAt(any(), any());
        verify(mapper, never()).upsertStatus(any(), any());
    }
}
