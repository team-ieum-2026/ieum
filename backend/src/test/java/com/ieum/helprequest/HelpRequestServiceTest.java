// 주인: BE-B
package com.ieum.helprequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import com.ieum.recommendation.RecommendationMapper;
import com.ieum.recommendation.RecommendationRows;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;

class HelpRequestServiceTest {
    private final HelpRequestMapper mapper = mock(HelpRequestMapper.class);
    private final RecommendationMapper recommendations = mock(RecommendationMapper.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final HelpRequestService service = new HelpRequestService(mapper, recommendations, events,
            Clock.fixed(Instant.parse("2026-10-28T01:00:00Z"), ZoneOffset.UTC));
    private final ObjectMapper json = new ObjectMapper();

    private void allowCreation() {
        when(mapper.findStudentId(7L)).thenReturn(7L);
        var owner = new RecommendationRows.ItemOwner();
        owner.setStudentId(7L);
        owner.setItemId(11L);
        when(recommendations.selectItemOwner(11L)).thenReturn(owner);
        when(recommendations.updateItemResponse(11L, "HELP", null, null)).thenReturn(1);
    }

    @Test
    void creationLocksItemBeforeStudentAndPublishesAfterGeneratedKey() {
        allowCreation();
        when(mapper.insert(any())).thenAnswer(call -> {
            HelpRequestData.Row row = call.getArgument(0);
            assertEquals(LocalDateTime.of(2026, 10, 28, 10, 0), row.getCreatedAt());
            row.setHelpRequestId(19L);
            return 1;
        });

        assertEquals(new HelpRequestResult(19L, "OPEN"), service.create(new HelpRequestCreateRequest(7L, 11L)));

        var order = inOrder(recommendations, mapper, events);
        order.verify(recommendations).updateItemResponse(11L, "HELP", null, null);
        order.verify(mapper).touchLastResponse(7L, LocalDateTime.of(2026, 10, 28, 10, 0));
        order.verify(mapper).insert(any());
        order.verify(events).publishEvent(new HelpRequestChangedEvent(7L, 19L));
    }

    @Test
    void failedInsertDoesNotPublishAnEvent() {
        allowCreation();
        when(mapper.insert(any())).thenThrow(new IllegalStateException("모의 저장 실패"));
        assertThrows(IllegalStateException.class, () -> service.create(new HelpRequestCreateRequest(7L, 11L)));
        verifyNoInteractions(events);
    }

    @Test
    void wrongOwnerDoesNotChangeAnySource() {
        allowCreation();
        when(mapper.findStudentId(8L)).thenReturn(8L);
        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.create(new HelpRequestCreateRequest(8L, 11L)));
        assertEquals(ErrorCode.INVALID_REQUEST, failure.getErrorCode());
        verify(recommendations, never()).updateItemResponse(any(), any(), any(), any());
        verify(mapper, never()).touchLastResponse(any(), any());
        verifyNoInteractions(events);
    }

    @Test
    void partialPatchKeepsOmittedFieldsAndDoesNotTouchStudentResponse() throws Exception {
        var row = existing("OPEN");
        row.setContactMethod("SMS");
        row.setMemo("모의 기존 메모");
        when(mapper.findForUpdate(19L)).thenReturn(row);
        when(mapper.updateDetails(any())).thenReturn(1);
        var patch = json.readValue("{\"memo\":null}", HelpRequestUpdateRequest.class);

        assertEquals(new HelpRequestUpdateResult(19L, "OPEN", "SMS", null), service.update(19L, patch));

        verify(mapper, never()).touchLastResponse(any(), any());
        verify(events).publishEvent(new HelpRequestChangedEvent(7L, 19L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPEN", "CONTACTED"})
    void sameCoordinatorStatusIsIdempotentAndStillRefreshesStatus(String status) {
        when(mapper.findForUpdate(19L)).thenReturn(existing(status));
        assertEquals(new HelpRequestResult(19L, status),
                service.updateStatus(19L, new HelpRequestStatusUpdateRequest(status)));
        verify(mapper, never()).updateStatus(any(), any());
        verify(mapper, never()).touchLastResponse(any(), any());
        verify(events).publishEvent(new HelpRequestChangedEvent(7L, 19L));
    }

    @Test
    void closedExistingRequestCannotBeReopenedThroughP0Api() {
        when(mapper.findForUpdate(19L)).thenReturn(existing("CLOSED"));
        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.updateStatus(19L, new HelpRequestStatusUpdateRequest("OPEN")));
        assertEquals(ErrorCode.INVALID_STATUS, failure.getErrorCode());
        verifyNoInteractions(events);
    }

    private HelpRequestData.Row existing(String status) {
        var row = new HelpRequestData.Row();
        row.setHelpRequestId(19L);
        row.setStudentId(7L);
        row.setStatus(status);
        return row;
    }
}
