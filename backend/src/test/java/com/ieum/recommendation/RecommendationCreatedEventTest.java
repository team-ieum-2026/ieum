package com.ieum.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.ai.AiClient;
import com.ieum.profile.ProfileService;
import com.ieum.profile.StudentProfileResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class RecommendationCreatedEventTest {

    private final ObjectMapper json = new ObjectMapper();
    private RecommendationMapper mapper;
    private ProfileService profileService;
    private AiClient aiClient;
    private ApplicationEventPublisher events;
    private RecommendationService service;

    @BeforeEach
    void setUp() throws Exception {
        mapper = mock(RecommendationMapper.class);
        profileService = mock(ProfileService.class);
        aiClient = mock(AiClient.class);
        events = mock(ApplicationEventPublisher.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new RecommendationService(mapper, profileService, aiClient, json,
                new TransactionTemplate(txManager), events);

        when(profileService.getProfile(7L)).thenReturn(profile(json.readTree("{\"roles\":[\"백엔드\"]}")));
        when(mapper.selectRespondedJobIds(7L)).thenReturn(List.of());
        doAnswer(inv -> {
            inv.<RecommendationRows.NewRecommendation>getArgument(0).setId(40L);
            return 1;
        }).when(mapper).insertRecommendation(any());
        var saved = new RecommendationRows.Header();
        saved.setId(40L);
        saved.setCreatedAt(LocalDateTime.of(2026, 10, 28, 9, 0));
        when(mapper.selectLatest(7L)).thenReturn(saved);
    }

    @Test
    void savingRecommendationPublishesEventWithItemCount() throws Exception {
        when(aiClient.recommend(any())).thenReturn(json.readTree(
                "{\"items\":[{\"jobPostingId\":501,\"rank\":1,\"reasons\":[]}]}"));
        when(mapper.selectActiveJobIds(List.of(501L))).thenReturn(List.of(501L));

        service.refresh(7L);

        var event = ArgumentCaptor.forClass(RecommendationCreatedEvent.class);
        verify(events).publishEvent(event.capture());
        assertEquals(new RecommendationCreatedEvent(7L, 40L, "REFRESH", 1), event.getValue());
    }

    @Test
    void zeroMatchIsSavedAndPublishedWithZeroItems() throws Exception {
        when(aiClient.recommend(any())).thenReturn(json.readTree("{\"items\":[]}"));

        service.refresh(7L);

        verify(mapper, never()).insertItems(any());
        var event = ArgumentCaptor.forClass(RecommendationCreatedEvent.class);
        verify(events).publishEvent(event.capture());
        assertEquals(new RecommendationCreatedEvent(7L, 40L, "REFRESH", 0), event.getValue());
    }

    @Test
    void noProfileSavesNothingAndPublishesNothing() {
        when(profileService.getProfile(7L)).thenReturn(profile(null));

        service.refresh(7L);

        verify(mapper, never()).insertRecommendation(any());
        verify(events, never()).publishEvent(any());
    }

    private static StudentProfileResponse profile(JsonNode competencyProfile) {
        return new StudentProfileResponse(7L, null,
                new StudentProfileResponse.Basic("모의 수료생", null, null, "백엔드 개발", "서울", null,
                        LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(1L, "모의 과정", "4기", "모의캠퍼스",
                        LocalDate.of(2026, 3, 2), LocalDate.of(2026, 8, 29)),
                List.of(), List.of(), List.of(), List.of("Java"), List.of(), null, competencyProfile);
    }
}
