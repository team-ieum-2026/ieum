// Owner: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ProfileCareerServiceTest {

    private static final ObjectMapper JSON = JsonMapper.builder().addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    @Mock
    private ProfileMapper mapper;
    @Mock
    private ProfileService profiles;
    @Mock
    private AiClient ai;

    @Mock
    private ApplicationEventPublisher events;

    private ProfileItemService service;

    @BeforeEach
    void setUp() {
        service = new ProfileItemService(mapper, profiles, events);
    }

    @Test
    void addsAllCareerFieldsAndQueuesRefreshBeforeReturningGeneratedId() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        doAnswer(invocation -> {
            ProfileData.Career career = invocation.getArgument(0);
            career.setItemId(77L);
            return 1;
        }).when(mapper).insertCareer(any(ProfileData.Career.class));
        StudentProfileResponse updated = profile(true);
        prepareSavedProfile(updated);

        StudentProfileResponse result = service.addItem(7L, new ProfileItemRequest("CAREER", validItem()));

        assertSame(updated, result);
        ArgumentCaptor<ProfileData.Career> saved = ArgumentCaptor.forClass(ProfileData.Career.class);
        InOrder order = inOrder(mapper, profiles, events);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).insertCareer(saved.capture());
        order.verify(events).publishEvent(new ProfileChangedEvent(7L));
        order.verify(profiles).getProfile(7L);
        assertAll(
                () -> assertEquals(7L, saved.getValue().getStudentId()),
                () -> assertEquals(77L, saved.getValue().getItemId()),
                () -> assertEquals("Mock Company", saved.getValue().getCompanyName()),
                () -> assertEquals("Mock Position", saved.getValue().getPosition()),
                () -> assertEquals(LocalDate.of(2023, 1, 15), saved.getValue().getStartedOn()),
                () -> assertEquals(LocalDate.of(2024, 6, 30), saved.getValue().getEndedOn()),
                () -> assertEquals("Mock Description", saved.getValue().getDescription()),
                () -> assertEquals(77L, result.careers().get(0).itemId()));
        verify(mapper, never()).insertCertificate(any(ProfileData.Certificate.class));
        verify(mapper, never()).insertEducation(any(ProfileData.Education.class));
    }

    @Test
    void patchesRegisteredCareerPreservingOmissionsAndClearingNullToMarkOngoingEmployment() {
        prepareExisting();
        when(mapper.updateCareer(any(ProfileData.Career.class))).thenReturn(1);
        prepareSavedProfile(profile(true));
        JsonNode item = JSON.createObjectNode().putNull("position").putNull("endedOn");

        service.updateItem(7L, 77L, new ProfileItemPatchRequest("CAREER", item));

        ArgumentCaptor<ProfileData.Career> saved = ArgumentCaptor.forClass(ProfileData.Career.class);
        verify(mapper).updateCareer(saved.capture());
        assertAll(
                () -> assertEquals(7L, saved.getValue().getStudentId()),
                () -> assertEquals(77L, saved.getValue().getItemId()),
                () -> assertEquals("Mock Company", saved.getValue().getCompanyName()),
                () -> assertNull(saved.getValue().getPosition()),
                () -> assertEquals(LocalDate.of(2023, 1, 15), saved.getValue().getStartedOn()),
                () -> assertNull(saved.getValue().getEndedOn()),
                () -> assertEquals("Mock Description", saved.getValue().getDescription()));
        verify(mapper, never()).updateCertificate(any(ProfileData.Certificate.class));
        verify(mapper, never()).updateEducation(any(ProfileData.Education.class));
    }

    @ParameterizedTest
    @MethodSource("datePatchesConflictingWithStoredCareer")
    void rejectsPartialDatePatchThatConflictsWithOmittedStoredDate(JsonNode item) {
        prepareExisting();

        BusinessException failure = assertThrows(BusinessException.class, () -> service.updateItem(
                7L, 77L, new ProfileItemPatchRequest("CAREER", item)));

        assertEquals(ErrorCode.INVALID_REQUEST, failure.getErrorCode());
        verify(mapper, never()).updateCareer(any(ProfileData.Career.class));
        verifyNoInteractions(profiles, ai, events);
    }

    @Test
    void deletesRegisteredCareerAndQueuesRefreshBeforeReturningSavedProfile() {
        prepareExisting();
        when(mapper.deleteCareer(7L, 77L)).thenReturn(1);
        StudentProfileResponse updated = profile(false);
        prepareSavedProfile(updated);

        assertSame(updated, service.deleteItem(7L, 77L, "CAREER"));

        InOrder order = inOrder(mapper, profiles, events);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).findCareerItem(7L, 77L);
        order.verify(mapper).deleteCareer(7L, 77L);
        order.verify(events).publishEvent(new ProfileChangedEvent(7L));
        order.verify(profiles).getProfile(7L);
        verify(mapper, never()).deleteCertificate(any(), any());
        verify(mapper, never()).deleteEducation(any(), any());
    }

    @Test
    void missingOrForeignCareerCannotBeUpdatedOrDeleted() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        when(mapper.findCareerItem(7L, 9999L)).thenReturn(null);

        assertThrows(ProfileItemNotFoundException.class, () -> service.updateItem(
                7L, 9999L, new ProfileItemPatchRequest("CAREER", JSON.createObjectNode().putNull("endedOn"))));
        assertThrows(ProfileItemNotFoundException.class, () -> service.deleteItem(7L, 9999L, "CAREER"));

        verify(mapper, never()).updateCareer(any(ProfileData.Career.class));
        verify(mapper, never()).deleteCareer(any(), any());
        verifyNoInteractions(profiles, ai, events);
    }

    @Test
    void missingStudentCannotInsertCareerOrCallAi() {
        when(mapper.lockStudent(9999L)).thenReturn(null);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.addItem(9999L, new ProfileItemRequest("CAREER", validItem())));

        assertEquals(ErrorCode.STUDENT_NOT_FOUND, failure.getErrorCode());
        verify(mapper, never()).insertCareer(any(ProfileData.Career.class));
        verifyNoInteractions(profiles, ai, events);
    }

    @Test
    void careerUpdatePublishesRefreshWithoutCallingAi() {
        prepareExisting();
        when(mapper.updateCareer(any(ProfileData.Career.class))).thenReturn(1);
        when(profiles.getProfile(7L)).thenReturn(profile(true));

        service.updateItem(
                7L, 77L, new ProfileItemPatchRequest("CAREER", JSON.createObjectNode().put("position", "Mock Updated Position")));

        verify(mapper).updateCareer(any(ProfileData.Career.class));
        verify(mapper, never()).upsertCompetencyProfile(any(), any(), anyString());
        verify(events).publishEvent(new ProfileChangedEvent(7L));
        verifyNoInteractions(ai);
    }

    private void prepareExisting() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        when(mapper.findCareerItem(7L, 77L)).thenReturn(new StudentProfileResponse.Career(
                77L, "REGISTERED", "Mock Company", "Mock Position",
                LocalDate.of(2023, 1, 15), LocalDate.of(2024, 6, 30), "Mock Description"));
    }

    private void prepareSavedProfile(StudentProfileResponse updated) {
        when(profiles.getProfile(7L)).thenReturn(updated);
    }

    private static Stream<JsonNode> datePatchesConflictingWithStoredCareer() {
        return Stream.of(
                JSON.createObjectNode().put("startedOn", "2024-07-01"),
                JSON.createObjectNode().put("endedOn", "2023-01-14"));
    }

    private static JsonNode validItem() {
        return JSON.createObjectNode().put("companyName", "Mock Company").put("position", "Mock Position")
                .put("startedOn", "2023-01-15").put("endedOn", "2024-06-30").put("description", "Mock Description");
    }

    private static StudentProfileResponse profile(boolean includeCareer) {
        var career = new StudentProfileResponse.Career(
                77L, "USER", "Mock Company", "Mock Position",
                LocalDate.of(2023, 1, 15), LocalDate.of(2024, 6, 30), "Mock Description");
        return new StudentProfileResponse(
                7L, null,
                new StudentProfileResponse.Basic("Mock Student", null, null, null, null, null, LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(
                        2L, "Mock Course", "1", "Mock Campus", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 8, 29)),
                List.of(), includeCareer ? List.of(career) : List.of(), List.of(), List.of(), List.of(), null, null);
    }
}
