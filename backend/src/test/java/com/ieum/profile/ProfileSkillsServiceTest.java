// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
class ProfileSkillsServiceTest {

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
    void replacesOnlySkillsAndPreservesTagOrderDuplicatesAndSurroundingWhitespaceBeforeExtracting() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        List<String> tags = List.of("Spring Boot", " Java ", "Spring Boot", "모의 기술");
        StudentProfileResponse updated = profile(tags);
        prepareM8Extraction(updated);
        var item = tagsItem(tags).put("source", "AI").put("name", "Ignored Mock Name")
                .put("militaryStatus", "EXEMPT").put("lastResponseAt", "2000-01-01T00:00:00")
                .put("status", "EMPLOYED");
        item.putArray("summary").add("Generated document sentence");
        item.putArray("skills").add("Ignored alternate key");

        assertSame(updated, service.updateItem(7L, 7L, new ProfileItemPatchRequest("SKILLS", item)));

        ArgumentCaptor<JsonNode> saved = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<JsonNode> sources = ArgumentCaptor.forClass(JsonNode.class);
        InOrder order = inOrder(mapper, profiles, ai);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).updateSkills(eq(7L), saved.capture());
        order.verify(profiles).getProfile(7L);
        order.verify(mapper).findProjects(7L);
        order.verify(ai).extractProfile(sources.capture());
        order.verify(mapper).upsertCompetencyProfile(eq(7L), any(JsonNode.class), anyString());
        order.verify(profiles).getProfile(7L);
        assertEquals(JSON.valueToTree(tags), saved.getValue());
        assertEquals(JSON.valueToTree(tags), sources.getValue().path("skills"));
        verify(mapper, never()).updateBasic(any());
        verify(mapper, never()).updateMilitary(any(), any());
    }

    @Test
    void emptyTagsClearsListThroughPatchAndReextracts() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        prepareM8Extraction(profile(List.of()));

        StudentProfileResponse result = service.updateItem(7L, 7L,
                new ProfileItemPatchRequest("SKILLS", tagsItem(List.of())));

        verify(mapper).updateSkills(7L, JSON.createArrayNode());
        assertEquals(List.of(), result.skills());
        verify(ai).extractProfile(any());
    }

    @Test
    void wrongSingletonIdCannotWriteAnotherStudentsSkills() {
        when(mapper.lockStudent(7L)).thenReturn(7L);

        assertThrows(ProfileItemNotFoundException.class, () -> service.updateItem(7L, 8L,
                new ProfileItemPatchRequest("SKILLS", tagsItem(List.of("Java")))));

        verify(mapper, never()).updateSkills(any(), any());
        verifyNoInteractions(profiles, ai);
    }

    @Test
    void missingStudentUsesAgreedBusinessExceptionBeforeWriting() {
        when(mapper.lockStudent(9999L)).thenReturn(null);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.updateItem(9999L, 9999L,
                new ProfileItemPatchRequest("SKILLS", tagsItem(List.of("Java")))));

        assertEquals(ErrorCode.STUDENT_NOT_FOUND, failure.getErrorCode());
        verify(mapper, never()).updateSkills(any(), any());
        verifyNoInteractions(profiles, ai);
    }

    @Test
    void directM8ServiceCannotPersistNullTagsOrDocumentSummaryWithoutTags() {
        when(mapper.lockStudent(7L)).thenReturn(7L);

        BusinessException nullFailure = assertThrows(BusinessException.class, () -> service.updateItem(7L, 7L,
                new ProfileItemPatchRequest("SKILLS", JSON.createObjectNode().putNull("tags"))));
        BusinessException summaryFailure = assertThrows(BusinessException.class, () -> service.updateItem(7L, 7L,
                new ProfileItemPatchRequest("SKILLS", JSON.createObjectNode().set("summary", JSON.createArrayNode()))));

        assertEquals(ErrorCode.INVALID_REQUEST, nullFailure.getErrorCode());
        assertEquals(ErrorCode.INVALID_REQUEST, summaryFailure.getErrorCode());
        verify(mapper, never()).updateSkills(any(), any());
        verifyNoInteractions(profiles, ai);
    }

    @Test
    void skillsCannotBeAddedOrDeletedAsAnIndependentProfileRow() {
        when(mapper.lockStudent(7L)).thenReturn(7L);

        BusinessException addFailure = assertThrows(BusinessException.class, () -> service.addItem(7L,
                new ProfileItemRequest("SKILLS", tagsItem(List.of("Java")))));
        BusinessException deleteFailure = assertThrows(BusinessException.class,
                () -> service.deleteItem(7L, 7L, "SKILLS"));

        assertEquals(ErrorCode.INVALID_REQUEST, addFailure.getErrorCode());
        assertEquals(ErrorCode.INVALID_REQUEST, deleteFailure.getErrorCode());
        verify(mapper, never()).updateSkills(any(), any());
        verifyNoInteractions(profiles, ai);
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

    private static com.fasterxml.jackson.databind.node.ObjectNode tagsItem(List<String> tags) {
        return JSON.createObjectNode().set("tags", JSON.valueToTree(tags));
    }

    private static StudentProfileResponse profile(List<String> tags) {
        return new StudentProfileResponse(7L, null,
                new StudentProfileResponse.Basic("Mock Student", "mock@example.com", null,
                        "Mock Backend", "Mock Region", null, LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(2L, "Mock Course", "1", "Mock Campus", null, null),
                List.of(), List.of(), List.of(), tags, List.of(), null, null);
    }
}
