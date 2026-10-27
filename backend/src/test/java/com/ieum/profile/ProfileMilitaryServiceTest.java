// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
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
class ProfileMilitaryServiceTest {

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
    void updatesOnlyMilitaryColumnBeforeExtractingAndReturningFullProfile() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        StudentProfileResponse updated = profile("SERVED");
        prepareM8Extraction(updated);
        var item = JSON.createObjectNode().put("value", "SERVED").put("name", "Ignored Mock Name")
                .put("desiredJob", "Ignored Job").put("courseId", 99L)
                .put("completedOn", "2000-01-01").put("lastResponseAt", "2000-01-01T00:00:00")
                .put("status", "EMPLOYED");
        item.putArray("skills").add("Ignored Skill");

        assertSame(updated, service.updateItem(7L, 7L, new ProfileItemPatchRequest("MILITARY", item)));

        ArgumentCaptor<JsonNode> sources = ArgumentCaptor.forClass(JsonNode.class);
        InOrder order = inOrder(mapper, profiles, ai);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).updateMilitary(7L, "SERVED");
        order.verify(profiles).getProfile(7L);
        order.verify(mapper).findProjects(7L);
        order.verify(ai).extractProfile(sources.capture());
        order.verify(mapper).upsertCompetencyProfile(eq(7L), any(JsonNode.class), anyString());
        order.verify(profiles).getProfile(7L);
        assertEquals("SERVED", sources.getValue().path("basic").path("militaryStatus").asText());
        verify(mapper, never()).updateBasic(any());
        verify(mapper, never()).findCurrentStudent(any());
    }

    @Test
    void explicitNullClearsMilitaryThroughPatchAndReextracts() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        prepareM8Extraction(profile(null));

        StudentProfileResponse result = service.updateItem(7L, 7L,
                new ProfileItemPatchRequest("MILITARY", JSON.createObjectNode().putNull("value")));

        verify(mapper).updateMilitary(7L, null);
        assertNull(result.basic().militaryStatus());
        verify(ai).extractProfile(any());
    }

    @Test
    void wrongSingletonIdCannotWriteAnotherStudentsMilitaryStatus() {
        when(mapper.lockStudent(7L)).thenReturn(7L);

        assertThrows(ProfileItemNotFoundException.class, () -> service.updateItem(7L, 8L,
                new ProfileItemPatchRequest("MILITARY", JSON.createObjectNode().put("value", "SERVED"))));

        verify(mapper, never()).updateMilitary(any(), any());
        verifyNoInteractions(profiles, ai);
    }

    @Test
    void missingStudentUsesAgreedBusinessExceptionBeforeWriting() {
        when(mapper.lockStudent(9999L)).thenReturn(null);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.updateItem(9999L, 9999L,
                new ProfileItemPatchRequest("MILITARY", JSON.createObjectNode().put("value", "SERVED"))));

        assertEquals(ErrorCode.STUDENT_NOT_FOUND, failure.getErrorCode());
        verify(mapper, never()).updateMilitary(any(), any());
        verifyNoInteractions(profiles, ai);
    }

    @Test
    void directServiceCannotPersistUnsupportedMilitaryValue() {
        when(mapper.lockStudent(7L)).thenReturn(7L);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.updateItem(7L, 7L,
                new ProfileItemPatchRequest("MILITARY", JSON.createObjectNode().put("value", "UNKNOWN"))));

        assertEquals(ErrorCode.INVALID_REQUEST, failure.getErrorCode());
        verify(mapper, never()).updateMilitary(any(), any());
        verifyNoInteractions(profiles, ai);
    }

    @Test
    void militaryCannotBeAddedOrDeletedAsAnIndependentProfileRow() {
        when(mapper.lockStudent(7L)).thenReturn(7L);

        BusinessException addFailure = assertThrows(BusinessException.class, () -> service.addItem(7L,
                new ProfileItemRequest("MILITARY", JSON.createObjectNode().put("value", "SERVED"))));
        BusinessException deleteFailure = assertThrows(BusinessException.class,
                () -> service.deleteItem(7L, 7L, "MILITARY"));

        assertEquals(ErrorCode.INVALID_REQUEST, addFailure.getErrorCode());
        assertEquals(ErrorCode.INVALID_REQUEST, deleteFailure.getErrorCode());
        verify(mapper, never()).updateMilitary(any(), any());
        verifyNoInteractions(profiles, ai);
    }

    @Test
    void documentMilitaryUpdateUsesSharedWriterAndCurrentSnapshotForAi() {
        ProfileService documentService = spy(new ProfileService(mapper, ai, JSON));
        when(mapper.lockStudent(7L)).thenReturn(7L);
        doReturn(profile("EXEMPT")).when(documentService).getCurrentProfile(7L);
        when(mapper.findCurrentProjects(7L)).thenReturn(List.of());
        prepareAiResult();

        documentService.updateItem(7L, "MILITARY", 7L, JSON.createObjectNode().put("value", "EXEMPT"));

        InOrder order = inOrder(mapper, documentService, ai);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).updateMilitary(7L, "EXEMPT");
        order.verify(documentService).getCurrentProfile(7L);
        order.verify(mapper).findCurrentProjects(7L);
        ArgumentCaptor<JsonNode> sources = ArgumentCaptor.forClass(JsonNode.class);
        order.verify(ai).extractProfile(sources.capture());
        order.verify(mapper).upsertCompetencyProfile(eq(7L), any(JsonNode.class), anyString());
        assertEquals("EXEMPT", sources.getValue().path("basic").path("militaryStatus").asText());
        verify(documentService, never()).getProfile(any());
        verify(mapper, never()).findProjects(any());
    }

    private void prepareM8Extraction(StudentProfileResponse result) {
        when(profiles.getProfile(7L)).thenReturn(result);
        when(mapper.findProjects(7L)).thenReturn(List.of());
        prepareAiResult();
    }

    private void prepareAiResult() {
        when(ai.extractProfile(any(JsonNode.class))).thenReturn(JSON.createObjectNode()
                .set("profile", JSON.createObjectNode().put("marker", "mock-competency")));
    }

    private static StudentProfileResponse profile(String militaryStatus) {
        return new StudentProfileResponse(7L, null,
                new StudentProfileResponse.Basic("Mock Student", "mock@example.com", null,
                        "Mock Backend", "Mock Region", militaryStatus, LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(2L, "Mock Course", "1", "Mock Campus", null, null),
                List.of(), List.of(), List.of(), List.of(), List.of(), null, null);
    }
}
