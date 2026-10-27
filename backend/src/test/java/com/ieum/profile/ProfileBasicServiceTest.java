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
class ProfileBasicServiceTest {

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
    void updatesOnlyBasicFieldsAndReextractsBeforeReturningFullProfile() {
        prepareExisting();
        StudentProfileResponse updated = profile();
        prepareM8Extraction(updated);
        var item = JSON.createObjectNode().put("name", "Updated Mock Student").put("email", "updated@example.com")
                .put("githubUrl", "github.com/mock-updated").put("desiredJob", "Mock Backend")
                .put("preferredRegion", "Mock Seoul").put("studentId", 8L).put("courseId", 99L)
                .put("militaryStatus", "EXEMPT").put("completedOn", "2020-01-01")
                .put("lastResponseAt", "2026-01-01T00:00:00").put("status", "EMPLOYED");
        item.putArray("skills").add("Ignored Skill");

        assertSame(updated, service.updateItem(7L, 7L, new ProfileItemPatchRequest("BASIC", item)));

        ArgumentCaptor<ProfileData.Student> saved = ArgumentCaptor.forClass(ProfileData.Student.class);
        ArgumentCaptor<JsonNode> sources = ArgumentCaptor.forClass(JsonNode.class);
        InOrder order = inOrder(mapper, profiles, ai);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).findCurrentStudent(7L);
        order.verify(mapper).updateBasic(saved.capture());
        order.verify(profiles).getProfile(7L);
        order.verify(mapper).findProjects(7L);
        order.verify(ai).extractProfile(sources.capture());
        order.verify(mapper).upsertCompetencyProfile(eq(7L), any(JsonNode.class), anyString());
        order.verify(profiles).getProfile(7L);
        assertAll(
                () -> assertEquals(7L, saved.getValue().getStudentId()),
                () -> assertEquals("Updated Mock Student", saved.getValue().getName()),
                () -> assertEquals("updated@example.com", saved.getValue().getEmail()),
                () -> assertEquals("github.com/mock-updated", saved.getValue().getGithubUrl()),
                () -> assertEquals("Mock Backend", saved.getValue().getDesiredJob()),
                () -> assertEquals("Mock Seoul", saved.getValue().getPreferredRegion()),
                () -> assertNull(saved.getValue().getCourseId()),
                () -> assertNull(saved.getValue().getMilitaryStatus()),
                () -> assertNull(saved.getValue().getSkills()),
                () -> assertNull(saved.getValue().getCompletedOn()),
                () -> assertEquals("Mock Backend", sources.getValue().path("basic").path("desiredJob").asText()));
    }

    @Test
    void partialPatchKeepsEveryOmittedBasicValueFromCurrentStudent() {
        prepareExisting();
        prepareM8Extraction(profile());

        service.updateItem(7L, 7L, new ProfileItemPatchRequest("BASIC",
                JSON.createObjectNode().put("desiredJob", "Mock Backend")));

        ArgumentCaptor<ProfileData.Student> saved = ArgumentCaptor.forClass(ProfileData.Student.class);
        verify(mapper).updateBasic(saved.capture());
        assertAll(
                () -> assertEquals("Original Mock Student", saved.getValue().getName()),
                () -> assertEquals("original@example.com", saved.getValue().getEmail()),
                () -> assertEquals("github.com/mock-original", saved.getValue().getGithubUrl()),
                () -> assertEquals("Mock Backend", saved.getValue().getDesiredJob()),
                () -> assertEquals("Mock Original Region", saved.getValue().getPreferredRegion()));
    }

    @Test
    void explicitNullClearsOptionalValuesAndKeepsRequiredName() {
        prepareExisting();
        prepareM8Extraction(profile());

        service.updateItem(7L, 7L, new ProfileItemPatchRequest("BASIC", JSON.createObjectNode()
                .putNull("email").putNull("githubUrl").putNull("desiredJob").putNull("preferredRegion")));

        ArgumentCaptor<ProfileData.Student> saved = ArgumentCaptor.forClass(ProfileData.Student.class);
        verify(mapper).updateBasic(saved.capture());
        assertAll(
                () -> assertEquals("Original Mock Student", saved.getValue().getName()),
                () -> assertNull(saved.getValue().getEmail()),
                () -> assertNull(saved.getValue().getGithubUrl()),
                () -> assertNull(saved.getValue().getDesiredJob()),
                () -> assertNull(saved.getValue().getPreferredRegion()));
    }

    @Test
    void wrongSingletonIdCannotReadWriteOrExtractAnotherStudent() {
        when(mapper.lockStudent(7L)).thenReturn(7L);

        assertThrows(ProfileItemNotFoundException.class, () -> service.updateItem(7L, 8L,
                new ProfileItemPatchRequest("BASIC", JSON.createObjectNode().put("desiredJob", "Mock Backend"))));

        verify(mapper, never()).findCurrentStudent(any());
        verify(mapper, never()).updateBasic(any());
        verifyNoInteractions(profiles, ai);
    }

    @Test
    void missingStudentUsesAgreedBusinessExceptionBeforeWrite() {
        when(mapper.lockStudent(9999L)).thenReturn(null);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.updateItem(9999L, 9999L,
                new ProfileItemPatchRequest("BASIC", JSON.createObjectNode().put("desiredJob", "Mock Backend"))));

        assertEquals(ErrorCode.STUDENT_NOT_FOUND, failure.getErrorCode());
        verify(mapper, never()).findCurrentStudent(any());
        verify(mapper, never()).updateBasic(any());
        verifyNoInteractions(profiles, ai);
    }

    @Test
    void invalidDirectServicePatchCannotClearRequiredName() {
        when(mapper.lockStudent(7L)).thenReturn(7L);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.updateItem(7L, 7L,
                new ProfileItemPatchRequest("BASIC", JSON.createObjectNode().putNull("name"))));

        assertEquals(ErrorCode.INVALID_REQUEST, failure.getErrorCode());
        verify(mapper, never()).updateBasic(any());
        verifyNoInteractions(profiles, ai);
    }

    @Test
    void basicCannotBeAddedOrDeletedAsAnIndependentProfileRow() {
        when(mapper.lockStudent(7L)).thenReturn(7L);

        BusinessException addFailure = assertThrows(BusinessException.class, () -> service.addItem(7L,
                new ProfileItemRequest("BASIC", JSON.createObjectNode().put("name", "Mock Student"))));
        BusinessException deleteFailure = assertThrows(BusinessException.class,
                () -> service.deleteItem(7L, 7L, "BASIC"));

        assertEquals(ErrorCode.INVALID_REQUEST, addFailure.getErrorCode());
        assertEquals(ErrorCode.INVALID_REQUEST, deleteFailure.getErrorCode());
        verify(mapper, never()).updateBasic(any());
        verifyNoInteractions(profiles, ai);
    }

    private void prepareExisting() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        when(mapper.findCurrentStudent(7L)).thenReturn(student());
    }

    private void prepareM8Extraction(StudentProfileResponse result) {
        when(profiles.getProfile(7L)).thenReturn(result);
        when(mapper.findProjects(7L)).thenReturn(List.of());
        when(ai.extractProfile(any(JsonNode.class))).thenReturn(JSON.createObjectNode()
                .set("profile", JSON.createObjectNode().put("marker", "mock-competency")));
    }

    private static ProfileData.Student student() {
        var student = new ProfileData.Student();
        student.setStudentId(7L);
        student.setName("Original Mock Student");
        student.setEmail("original@example.com");
        student.setGithubUrl("github.com/mock-original");
        student.setDesiredJob("Original Mock Job");
        student.setPreferredRegion("Mock Original Region");
        student.setCourseId(2L);
        student.setMilitaryStatus("SERVED");
        student.setCompletedOn(LocalDate.of(2026, 8, 29));
        student.setSkills(JSON.createArrayNode().add("Java"));
        return student;
    }

    private static StudentProfileResponse profile() {
        return new StudentProfileResponse(7L, null,
                new StudentProfileResponse.Basic("Updated Mock Student", "updated@example.com",
                        "github.com/mock-updated", "Mock Backend", "Mock Seoul", "SERVED", LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(2L, "Mock Course", "1", "Mock Campus", null, null),
                List.of(), List.of(), List.of(), List.of(), List.of(), null, null);
    }
}
