// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileEducationServiceTest {

    private static final ObjectMapper JSON = JsonMapper.builder().addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    @Mock
    private ProfileMapper mapper;
    @Mock
    private ProfileService profiles;
    @Mock
    private AiClient ai;

    private ProfileItemService service;

    @BeforeEach
    void setUp() {
        service = new ProfileItemService(mapper, profiles, ai, JSON);
    }

    @Test
    void addsEducationAndReextractsFromStoredEducationBeforeReturningProfile() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        doAnswer(invocation -> {
            ProfileData.Education education = invocation.getArgument(0);
            education.setItemId(55L);
            return 1;
        }).when(mapper).insertEducation(any(ProfileData.Education.class));
        StudentProfileResponse updated = profile(true);
        JsonNode competency = prepareExtraction(updated);

        StudentProfileResponse result = service.addItem(7L, new ProfileItemRequest("EDUCATION", validItem()));

        assertSame(updated, result);
        ArgumentCaptor<ProfileData.Education> saved = ArgumentCaptor.forClass(ProfileData.Education.class);
        ArgumentCaptor<JsonNode> sources = ArgumentCaptor.forClass(JsonNode.class);
        InOrder order = inOrder(mapper, profiles, ai);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).insertEducation(saved.capture());
        order.verify(profiles).getProfile(7L);
        order.verify(mapper).findProjects(7L);
        order.verify(ai).extractProfile(sources.capture());
        order.verify(mapper).upsertCompetencyProfile(eq(7L), eq(competency), anyString());
        order.verify(profiles).getProfile(7L);
        assertAll(
                () -> assertEquals(7L, saved.getValue().getStudentId()),
                () -> assertEquals(55L, saved.getValue().getItemId()),
                () -> assertEquals("모의대학교", saved.getValue().getSchoolName()),
                () -> assertEquals("컴퓨터공학", saved.getValue().getMajor()),
                () -> assertEquals("BACHELOR", saved.getValue().getDegree()),
                () -> assertEquals("GRADUATED", saved.getValue().getStatus()),
                () -> assertEquals(LocalDate.of(2026, 2, 28), saved.getValue().getGraduatedOn()),
                () -> assertEquals("모의대학교", sources.getValue().get("education").get(0).get("schoolName").asText()));
        verify(mapper, never()).insertCertificate(any(ProfileData.Certificate.class));
    }

    @Test
    void partialEducationPatchPreservesOmittedFieldsAndClearsExplicitNull() {
        prepareExisting();
        when(mapper.updateEducation(any(ProfileData.Education.class))).thenReturn(1);
        prepareExtraction(profile(true));
        JsonNode item = JSON.createObjectNode().put("schoolName", "모의 수정 대학교").putNull("major")
                .putNull("graduatedOn");

        service.updateItem(7L, 55L, new ProfileItemPatchRequest("EDUCATION", item));

        ArgumentCaptor<ProfileData.Education> saved = ArgumentCaptor.forClass(ProfileData.Education.class);
        verify(mapper).updateEducation(saved.capture());
        assertAll(
                () -> assertEquals(7L, saved.getValue().getStudentId()),
                () -> assertEquals(55L, saved.getValue().getItemId()),
                () -> assertEquals("모의 수정 대학교", saved.getValue().getSchoolName()),
                () -> assertNull(saved.getValue().getMajor()),
                () -> assertEquals("BACHELOR", saved.getValue().getDegree()),
                () -> assertEquals("GRADUATED", saved.getValue().getStatus()),
                () -> assertNull(saved.getValue().getGraduatedOn()));
        verify(mapper, never()).updateCertificate(any(ProfileData.Certificate.class));
    }

    @Test
    void deletesEducationBeforeExtractingProfileWithoutDeletedItem() {
        prepareExisting();
        when(mapper.deleteEducation(7L, 55L)).thenReturn(1);
        StudentProfileResponse updated = profile(false);
        JsonNode competency = prepareExtraction(updated);

        assertSame(updated, service.deleteItem(7L, 55L, "EDUCATION"));

        ArgumentCaptor<JsonNode> sources = ArgumentCaptor.forClass(JsonNode.class);
        InOrder order = inOrder(mapper, profiles, ai);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).findEducationItem(7L, 55L);
        order.verify(mapper).deleteEducation(7L, 55L);
        order.verify(profiles).getProfile(7L);
        order.verify(mapper).findProjects(7L);
        order.verify(ai).extractProfile(sources.capture());
        order.verify(mapper).upsertCompetencyProfile(eq(7L), eq(competency), anyString());
        order.verify(profiles).getProfile(7L);
        assertEquals(0, sources.getValue().get("education").size());
        verify(mapper, never()).deleteCertificate(any(), any());
    }

    @Test
    void missingOrForeignEducationCannotBeUpdatedOrDeleted() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        when(mapper.findEducationItem(7L, 9999L)).thenReturn(null);

        assertThrows(ProfileItemNotFoundException.class, () -> service.updateItem(
                7L, 9999L, new ProfileItemPatchRequest("EDUCATION", JSON.createObjectNode().put("major", "모의 전공"))));
        assertThrows(ProfileItemNotFoundException.class, () -> service.deleteItem(7L, 9999L, "EDUCATION"));

        verify(mapper, never()).updateEducation(any(ProfileData.Education.class));
        verify(mapper, never()).deleteEducation(any(), any());
        verifyNoInteractions(profiles, ai);
    }

    @Test
    void missingStudentCannotInsertEducationOrCallAi() {
        when(mapper.lockStudent(9999L)).thenReturn(null);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.addItem(9999L, new ProfileItemRequest("EDUCATION", validItem())));

        assertEquals(ErrorCode.STUDENT_NOT_FOUND, failure.getErrorCode());
        verify(mapper, never()).insertEducation(any(ProfileData.Education.class));
        verifyNoInteractions(profiles, ai);
    }

    @Test
    void aiFailureAfterEducationUpdateDoesNotSaveCompetencyOrReturnSuccess() {
        prepareExisting();
        when(mapper.updateEducation(any(ProfileData.Education.class))).thenReturn(1);
        when(profiles.getProfile(7L)).thenReturn(profile(true));
        when(mapper.findProjects(7L)).thenReturn(List.of());
        BusinessException expected = new BusinessException(ErrorCode.AI_SERVER_ERROR);
        when(ai.extractProfile(any(JsonNode.class))).thenThrow(expected);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.updateItem(
                7L, 55L, new ProfileItemPatchRequest("EDUCATION", JSON.createObjectNode().put("major", "모의 전공"))));

        assertSame(expected, failure);
        verify(mapper).updateEducation(any(ProfileData.Education.class));
        verify(mapper, never()).upsertCompetencyProfile(any(), any(), anyString());
    }

    private void prepareExisting() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        when(mapper.findEducationItem(7L, 55L)).thenReturn(new StudentProfileResponse.Education(
                55L, "REGISTERED", "모의대학교", "컴퓨터공학", "BACHELOR", "GRADUATED", LocalDate.of(2026, 2, 28)));
    }

    private JsonNode prepareExtraction(StudentProfileResponse updated) {
        when(profiles.getProfile(7L)).thenReturn(updated);
        when(mapper.findProjects(7L)).thenReturn(List.of());
        JsonNode competency = JSON.createObjectNode();
        when(ai.extractProfile(any(JsonNode.class))).thenReturn(JSON.createObjectNode().set("profile", competency));
        return competency;
    }

    private static JsonNode validItem() {
        return JSON.createObjectNode().put("schoolName", "모의대학교").put("major", "컴퓨터공학")
                .put("degree", "BACHELOR").put("status", "GRADUATED").put("graduatedOn", "2026-02-28");
    }

    private static StudentProfileResponse profile(boolean includeEducation) {
        var education = new StudentProfileResponse.Education(
                55L, "USER", "모의대학교", "컴퓨터공학", "BACHELOR", "GRADUATED", LocalDate.of(2026, 2, 28));
        return new StudentProfileResponse(
                7L, null,
                new StudentProfileResponse.Basic("모의 수료생", null, null, null, null, null, LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(
                        2L, "모의 과정", "1기", "모의 캠퍼스", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 8, 29)),
                includeEducation ? List.of(education) : List.of(), List.of(), List.of(), List.of(), List.of(), null, null);
    }
}
