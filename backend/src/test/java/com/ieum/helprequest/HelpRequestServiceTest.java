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
import java.util.List;
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

    @ParameterizedTest
    @ValueSource(strings = {"RESUME_COVER_LETTER", "JOB_APPLICATION", "INTERVIEW_PREPARATION",
            "CAREER_COUNSELING", "OTHER"})
    void standaloneHelpRequiresNoRecommendationAndUpdatesOnlyStudentResponse(String helpType) {
        when(mapper.findStudentId(7L)).thenReturn(7L);
        when(mapper.insert(any())).thenAnswer(call -> {
            HelpRequestData.Row row = call.getArgument(0);
            assertNull(row.getRecommendationItemId());
            assertEquals(helpType, row.getHelpType());
            row.setHelpRequestId(19L);
            return 1;
        });

        assertEquals(new HelpRequestResult(19L, "OPEN"),
                service.create(new HelpRequestCreateRequest(7L, null, helpType)));

        verifyNoInteractions(recommendations);
        verify(mapper).touchLastResponse(7L, LocalDateTime.of(2026, 10, 28, 10, 0));
        verify(events).publishEvent(new HelpRequestChangedEvent(7L, 19L));
    }

    @Test
    void missingOrInvalidHelpTypeDoesNotWriteAnySource() {
        for (var request : List.of(new HelpRequestCreateRequest(7L, null),
                new HelpRequestCreateRequest(7L, null, "resume"),
                new HelpRequestCreateRequest(7L, 11L, ""),
                new HelpRequestCreateRequest(7L, -1L, "OTHER"))) {
            BusinessException failure = assertThrows(BusinessException.class, () -> service.create(request));
            assertEquals(ErrorCode.INVALID_REQUEST, failure.getErrorCode());
        }
        verifyNoInteractions(mapper, recommendations, events);
    }

    @Test
    void standaloneHelpStillRequiresAnExistingStudent() {
        when(mapper.findStudentId(7L)).thenReturn(null);
        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.create(new HelpRequestCreateRequest(7L, null, "OTHER")));
        assertEquals(ErrorCode.STUDENT_NOT_FOUND, failure.getErrorCode());
        verify(mapper, never()).insert(any());
        verify(mapper, never()).touchLastResponse(any(), any());
        verifyNoInteractions(recommendations, events);
    }

    @Test
    void duplicateRecommendationResponseDoesNotCreateAnotherHelp() {
        allowCreation();
        when(recommendations.updateItemResponse(11L, "HELP", null, null)).thenReturn(0);
        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.create(new HelpRequestCreateRequest(7L, 11L, "JOB_APPLICATION")));
        assertEquals(ErrorCode.ALREADY_RESPONDED, failure.getErrorCode());
        verify(mapper, never()).insert(any());
        verify(mapper, never()).touchLastResponse(any(), any());
        verifyNoInteractions(events);
    }

    @Test
    void listKeepsLegacyNullsAndConvertsStoredTimesToSeoul() {
        when(mapper.findStudentId(7L)).thenReturn(7L);
        var row = existing("CLOSED");
        row.setCreatedAt(LocalDateTime.of(2026, 10, 27, 8, 0));
        row.setClosedAt(LocalDateTime.of(2026, 10, 28, 9, 0));
        row.setCloseReason("NO_LONGER_NEEDED");
        when(mapper.findByStudent(7L, null)).thenReturn(List.of(row));

        var result = service.list(7L, null);

        assertEquals(List.of(new HelpRequestListItem(19L, 7L, null, null, "CLOSED", null, null,
                row.getCreatedAt().atOffset(ZoneOffset.ofHours(9)),
                row.getClosedAt().atOffset(ZoneOffset.ofHours(9)), "NO_LONGER_NEEDED", null)), result);
        verifyNoInteractions(recommendations, events);
        verify(mapper, never()).touchLastResponse(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPEN", "CONTACTED", "CLOSED"})
    void listForwardsEachAllowedStatusWithoutMutation(String status) {
        when(mapper.findStudentId(7L)).thenReturn(7L);
        when(mapper.findByStudent(7L, status)).thenReturn(List.of());
        assertTrue(service.list(7L, status).isEmpty());
        verify(mapper).findByStudent(7L, status);
        verifyNoInteractions(recommendations, events);
    }

    @Test
    void listRejectsInvalidStudentAndStatusAndChecksStudentExistence() {
        when(mapper.findStudentId(7L)).thenReturn(null);
        assertEquals(ErrorCode.INVALID_REQUEST, assertThrows(BusinessException.class,
                () -> service.list(0L, "OPEN")).getErrorCode());
        assertEquals(ErrorCode.INVALID_REQUEST, assertThrows(BusinessException.class,
                () -> service.list(7L, "null")).getErrorCode());
        assertEquals(ErrorCode.INVALID_REQUEST, assertThrows(BusinessException.class,
                () -> service.list(7L, "")).getErrorCode());
        assertEquals(ErrorCode.STUDENT_NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.list(7L, "OPEN")).getErrorCode());
        verify(mapper, never()).findByStudent(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMPLOYED", "NO_LONGER_NEEDED", "OTHER"})
    void closeLocksOnlyTheOwnedRequestAndPreservesResponseTime(String reason) {
        var row = existing("OPEN");
        when(mapper.findForUpdate(19L)).thenReturn(row);
        when(mapper.close(row)).thenReturn(1);

        var result = service.close(19L, new HelpRequestCloseRequest(7L, reason));

        assertEquals(new HelpRequestCloseResult(19L, "CLOSED",
                LocalDateTime.of(2026, 10, 28, 10, 0).atOffset(ZoneOffset.ofHours(9)), reason), result);
        var order = inOrder(mapper, events);
        order.verify(mapper).findForUpdate(19L);
        order.verify(mapper).close(row);
        order.verify(events).publishEvent(new HelpRequestChangedEvent(7L, 19L));
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(recommendations);
    }

    @Test
    void closeRetryPreservesTheFirstReasonAndTimestamp() {
        var row = existing("CLOSED");
        row.setClosedAt(LocalDateTime.of(2026, 10, 26, 8, 0));
        row.setCloseReason("EMPLOYED");
        when(mapper.findForUpdate(19L)).thenReturn(row);

        assertEquals(new HelpRequestCloseResult(19L, "CLOSED",
                        row.getClosedAt().atOffset(ZoneOffset.ofHours(9)), "EMPLOYED"),
                service.close(19L, new HelpRequestCloseRequest(7L, "OTHER")));

        verify(mapper, never()).close(any());
        verify(mapper, never()).touchLastResponse(any(), any());
        verify(events).publishEvent(new HelpRequestChangedEvent(7L, 19L));
    }

    @Test
    void closeRejectsAnotherStudentEvenWhenTheRequestWasAlreadyClosed() {
        when(mapper.findForUpdate(19L)).thenReturn(existing("CLOSED"));
        assertEquals(ErrorCode.INVALID_REQUEST, assertThrows(BusinessException.class,
                () -> service.close(19L, new HelpRequestCloseRequest(8L, "OTHER"))).getErrorCode());
        verify(mapper, never()).close(any());
        verifyNoInteractions(events);
    }

    @Test
    void contactedRequestCannotBeClosedByStudentAndMissingRequestReturnsNotFound() {
        when(mapper.findForUpdate(19L)).thenReturn(existing("CONTACTED"));
        assertEquals(ErrorCode.INVALID_STATUS, assertThrows(BusinessException.class,
                () -> service.close(19L, new HelpRequestCloseRequest(7L, "OTHER"))).getErrorCode());
        assertThrows(HelpRequestNotFoundException.class,
                () -> service.close(20L, new HelpRequestCloseRequest(7L, "OTHER")));
        verify(mapper, never()).close(any());
        verifyNoInteractions(events);
    }

    @Test
    void closeRejectsInvalidInputBeforeLockingAnyRow() {
        assertThrows(BusinessException.class, () -> service.close(0L, new HelpRequestCloseRequest(7L, "OTHER")));
        assertThrows(BusinessException.class, () -> service.close(19L, null));
        assertThrows(BusinessException.class, () -> service.close(19L, new HelpRequestCloseRequest(0L, "OTHER")));
        assertThrows(BusinessException.class, () -> service.close(19L, new HelpRequestCloseRequest(7L, null)));
        assertThrows(BusinessException.class, () -> service.close(19L, new HelpRequestCloseRequest(7L, "OPEN")));
        verifyNoInteractions(mapper, recommendations, events);
    }

    @Test
    void failedCloseDoesNotPublishAnEvent() {
        when(mapper.findForUpdate(19L)).thenReturn(existing("OPEN"));
        assertEquals(ErrorCode.INTERNAL_ERROR, assertThrows(BusinessException.class,
                () -> service.close(19L, new HelpRequestCloseRequest(7L, "OTHER"))).getErrorCode());
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
