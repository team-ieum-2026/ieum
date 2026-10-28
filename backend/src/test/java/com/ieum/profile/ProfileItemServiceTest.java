// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ieum.ai.AiClient;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ProfileItemServiceTest {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Mock
    private ProfileMapper mapper;

    @Mock
    private ProfileService profileService;

    @Mock
    private AiClient aiClient;

    @Mock
    private ApplicationEventPublisher events;

    private ProfileItemService service;

    @BeforeEach
    void setUp() {
        service = new ProfileItemService(mapper, profileService, events);
    }

    @Test
    void savesCertificateAndQueuesRefreshBeforeReturningSavedProfile() {
        ProfileItemRequest request = request();
        StudentProfileResponse raw = profile(null, JSON.createObjectNode());
        when(mapper.lockStudent(7L)).thenReturn(7L);
        doAnswer(invocation -> {
            ProfileData.Certificate certificate = invocation.getArgument(0);
            certificate.setItemId(31L);
            return 1;
        }).when(mapper).insertCertificate(any(ProfileData.Certificate.class));
        when(profileService.getProfile(7L)).thenReturn(raw);

        StudentProfileResponse result = service.addItem(7L, request);

        assertSame(raw, result);
        ArgumentCaptor<ProfileData.Certificate> certificateCaptor = ArgumentCaptor.forClass(ProfileData.Certificate.class);
        InOrder order = inOrder(mapper, profileService, events);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).insertCertificate(certificateCaptor.capture());
        order.verify(events).publishEvent(new ProfileChangedEvent(7L));
        order.verify(profileService).getProfile(7L);

        ProfileData.Certificate inserted = certificateCaptor.getValue();
        assertAll(
                () -> assertEquals(7L, inserted.getStudentId()),
                () -> assertEquals(31L, inserted.getItemId()),
                () -> assertEquals(request.item().get("name").asText(), inserted.getName()),
                () -> assertEquals(request.item().get("issuer").asText(), inserted.getIssuer()),
                () -> assertEquals(LocalDate.parse(request.item().get("acquiredOn").asText()), inserted.getAcquiredOn()));
    }

    @Test
    void missingStudentDoesNotInsertOrCallAi() {
        when(mapper.lockStudent(9999L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.addItem(9999L, request()));

        assertEquals(ErrorCode.STUDENT_NOT_FOUND, error.getErrorCode());
        verify(mapper).lockStudent(9999L);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(profileService, aiClient, events);
    }

    @Test
    void addingAnItemPublishesRefreshWithoutCallingAi() {
        prepareSavedProfile();

        service.addItem(7L, request());

        verify(mapper, never()).upsertCompetencyProfile(any(), any(), anyString());
        verify(profileService).getProfile(7L);
        verifyNoMoreInteractions(profileService);
        verify(events).publishEvent(new ProfileChangedEvent(7L));
        verifyNoInteractions(aiClient);
    }

    private void prepareSavedProfile() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        when(profileService.getProfile(7L)).thenReturn(profile(null, null));
    }

    private static ProfileItemRequest request() {
        return new ProfileItemRequest(
                "CERTIFICATES", JSON.valueToTree(new ProfileItemRequest.CertificateItem(
                        "모의 자격증", "모의 발급기관", LocalDate.of(2026, 9, 1))));
    }

    private static StudentProfileResponse profile(OffsetDateTime extractedAt, JsonNode competency) {
        return new StudentProfileResponse(
                7L, extractedAt,
                new StudentProfileResponse.Basic(
                        "모의 수료생", null, null, "백엔드 개발", "서울", null, LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(
                        2L, "모의 과정", "1기", "모의 캠퍼스", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 8, 29)),
                List.of(), List.of(),
                List.of(new StudentProfileResponse.Certificate(
                        31L, "USER", "모의 자격증", "모의 발급기관", LocalDate.of(2026, 9, 1))),
                List.of("Java"),
                List.of(new StudentProfileResponse.Project(
                        11L, "모의 프로젝트", null, null, List.of("Java"), null, false)),
                null, competency);
    }
}
