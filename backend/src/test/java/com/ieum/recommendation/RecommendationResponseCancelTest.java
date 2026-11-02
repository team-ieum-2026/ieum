package com.ieum.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import com.ieum.helprequest.HelpRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;

class RecommendationResponseCancelTest {

    private RecommendationMapper mapper;
    private HelpRequestService helpRequests;
    private ApplicationEventPublisher events;
    private RecommendationResponseCancelService service;

    @BeforeEach
    void setUp() {
        mapper = mock(RecommendationMapper.class);
        helpRequests = mock(HelpRequestService.class);
        events = mock(ApplicationEventPublisher.class);
        service = new RecommendationResponseCancelService(mapper, helpRequests, events);
    }

    @Test
    void passCancelResetsItemAndPublishesEvent() {
        item("PASS");
        when(mapper.resetItemResponse(91L, "PASS")).thenReturn(1);

        assertEquals(new ItemResponseResult(91L, "NONE", null), service.cancel(91L, "PASS"));

        verify(mapper, never()).selectApplicationForUpdate(anyLong());
        verify(helpRequests, never()).closeForCancelledResponse(anyLong(), anyLong());
        verify(events).publishEvent(new RecommendationResponseCancelledEvent(7L, 91L, "PASS"));
    }

    @Test
    void applyCancelDeletesApplicationBeforeReview() {
        item("APPLY");
        when(mapper.selectApplicationForUpdate(91L)).thenReturn(application(33L, "DRAFT"));
        when(mapper.deleteApplicationBeforeReview(33L)).thenReturn(1);
        when(mapper.resetItemResponse(91L, "APPLY")).thenReturn(1);

        assertEquals(new ItemResponseResult(91L, "NONE", null), service.cancel(91L, "APPLY"));

        verify(mapper).deleteApplicationBeforeReview(33L);
        verify(events).publishEvent(new RecommendationResponseCancelledEvent(7L, 91L, "APPLY"));
    }

    @Test
    void applyCancelAfterReviewRequestIs409AndKeepsResponse() {
        item("APPLY");
        when(mapper.selectApplicationForUpdate(91L)).thenReturn(application(33L, "SUBMITTED"));
        when(mapper.deleteApplicationBeforeReview(33L)).thenReturn(0);

        var e = assertThrows(BusinessException.class, () -> service.cancel(91L, "APPLY"));

        assertEquals(ErrorCode.INVALID_STATUS, e.getErrorCode());
        verify(mapper, never()).resetItemResponse(anyLong(), anyString());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void helpCancelClosesOpenRequestThenResetsItem() {
        item("HELP");
        when(mapper.resetItemResponse(91L, "HELP")).thenReturn(1);

        service.cancel(91L, "HELP");

        verify(helpRequests).closeForCancelledResponse(7L, 91L);
        verify(events).publishEvent(new RecommendationResponseCancelledEvent(7L, 91L, "HELP"));
    }

    @Test
    void contactedHelpIs409AndKeepsResponse() {
        item("HELP");
        doThrow(new BusinessException(ErrorCode.INVALID_STATUS))
                .when(helpRequests).closeForCancelledResponse(7L, 91L);

        assertEquals(ErrorCode.INVALID_STATUS,
                assertThrows(BusinessException.class, () -> service.cancel(91L, "HELP")).getErrorCode());
        verify(mapper, never()).resetItemResponse(anyLong(), anyString());
    }

    @Test
    void alreadyNoneIsIdempotentWithoutWrites() {
        item("NONE");

        assertEquals(new ItemResponseResult(91L, "NONE", null), service.cancel(91L, "APPLY"));

        verify(mapper, never()).resetItemResponse(anyLong(), anyString());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void differentResponseIs409() {
        item("APPLY");

        assertEquals(ErrorCode.INVALID_STATUS,
                assertThrows(BusinessException.class, () -> service.cancel(91L, "PASS")).getErrorCode());
        verify(mapper, never()).selectApplicationForUpdate(anyLong());
        verify(mapper, never()).resetItemResponse(anyLong(), anyString());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"NONE", "pass", ""})
    void invalidExpectedResponseIs400BeforeLocking(String expected) {
        assertEquals(ErrorCode.INVALID_REQUEST,
                assertThrows(BusinessException.class, () -> service.cancel(91L, expected)).getErrorCode());
        verify(mapper, never()).selectItemOwnerForUpdate(anyLong());
    }

    @Test
    void unknownItemIs404() {
        assertEquals(ErrorCode.RECOMMENDATION_ITEM_NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.cancel(999L, "PASS")).getErrorCode());
    }

    private void item(String response) {
        var owner = new RecommendationRows.ItemOwner();
        owner.setItemId(91L);
        owner.setStudentId(7L);
        owner.setJobPostingId(501L);
        owner.setResponse(response);
        when(mapper.selectItemOwnerForUpdate(91L)).thenReturn(owner);
    }

    private static RecommendationRows.LockedApplication application(Long id, String status) {
        var application = new RecommendationRows.LockedApplication();
        application.setId(id);
        application.setStatus(status);
        return application;
    }
}
