package com.ieum.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import com.ieum.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class RecommendationItemResponseTest {

    private RecommendationMapper mapper;
    private ApplicationEventPublisher events;
    private RecommendationItemService service;

    @BeforeEach
    void setUp() {
        mapper = mock(RecommendationMapper.class);
        events = mock(ApplicationEventPublisher.class);
        service = new RecommendationItemService(mapper, mock(ProfileService.class), new ObjectMapper(), events);
        var owner = new RecommendationRows.ItemOwner();
        owner.setItemId(91L);
        owner.setStudentId(7L);
        owner.setJobPostingId(501L);
        owner.setResponse("NONE");
        when(mapper.selectItemOwner(91L)).thenReturn(owner);
    }

    @Test
    void applyCreatesApplicationAndPublishesEvent() {
        when(mapper.updateItemResponse(91L, "APPLY", null, null)).thenReturn(1);
        doAnswer(inv -> {
            inv.<RecommendationRows.NewApplication>getArgument(0).setId(33L);
            return 1;
        }).when(mapper).insertApplication(any());

        ItemResponseResult result = service.respond(91L, new ItemResponseRequest("APPLY", null, null));

        assertEquals(new ItemResponseResult(91L, "APPLY", 33L), result);
        verify(mapper).touchLastResponse(7L);
        var event = ArgumentCaptor.forClass(RecommendationRespondedEvent.class);
        verify(events).publishEvent(event.capture());
        assertEquals(new RecommendationRespondedEvent(7L, 91L, 501L, "APPLY", 33L), event.getValue());
    }

    @Test
    void passSavesReasonWithoutApplication() {
        when(mapper.updateItemResponse(91L, "PASS", "OTHER", "경기도 공고도 보고 싶어요")).thenReturn(1);

        ItemResponseResult result = service.respond(91L,
                new ItemResponseRequest("PASS", "OTHER", "  경기도 공고도 보고 싶어요 "));

        assertNull(result.applicationId());
        verify(mapper, never()).insertApplication(any());
    }

    @Test
    void passWithoutReasonIs400() {
        var e = assertThrows(BusinessException.class,
                () -> service.respond(91L, new ItemResponseRequest("PASS", " ", null)));
        assertEquals(ErrorCode.PASS_REASON_REQUIRED, e.getErrorCode());
    }

    @Test
    void unknownResponseOrReasonIs400() {
        assertEquals(ErrorCode.INVALID_REQUEST, assertThrows(BusinessException.class,
                () -> service.respond(91L, new ItemResponseRequest("HELP", null, null))).getErrorCode());
        assertEquals(ErrorCode.INVALID_REQUEST, assertThrows(BusinessException.class,
                () -> service.respond(91L, new ItemResponseRequest("PASS", "BORED", null))).getErrorCode());
    }

    @Test
    void alreadyRespondedIs409AndCreatesNothing() {
        when(mapper.updateItemResponse(91L, "APPLY", null, null)).thenReturn(0);

        var e = assertThrows(BusinessException.class,
                () -> service.respond(91L, new ItemResponseRequest("APPLY", null, null)));

        assertEquals(ErrorCode.ALREADY_RESPONDED, e.getErrorCode());
        verify(mapper, never()).insertApplication(any());
        verify(mapper, never()).touchLastResponse(anyLong());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void unknownItemIs404() {
        var e = assertThrows(BusinessException.class,
                () -> service.respond(999L, new ItemResponseRequest("APPLY", null, null)));
        assertEquals(ErrorCode.RECOMMENDATION_ITEM_NOT_FOUND, e.getErrorCode());
    }
}
