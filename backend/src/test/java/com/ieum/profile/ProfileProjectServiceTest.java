// Owner: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
class ProfileProjectServiceTest {

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
    void m8CreatesOriginalProjectAndExtractsOriginalTextBeforeReturningFullProfile() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        doAnswer(invocation -> {
            ProfileData.Project saved = invocation.getArgument(1);
            saved.setItemId(77L);
            return 1;
        }).when(mapper).insertProject(eq(7L), any(ProfileData.Project.class));
        StudentProfileResponse updated = profile(true);
        JsonNode competency = prepareExtraction(updated, List.of(project()));

        assertSame(updated, service.addItem(7L, new ProfileItemRequest("PROJECTS", validItem())));

        ArgumentCaptor<ProfileData.Project> saved = ArgumentCaptor.forClass(ProfileData.Project.class);
        ArgumentCaptor<JsonNode> sources = ArgumentCaptor.forClass(JsonNode.class);
        InOrder order = inOrder(mapper, profiles, ai);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).insertProject(eq(7L), saved.capture());
        order.verify(profiles).getProfile(7L);
        order.verify(mapper).findProjects(7L);
        order.verify(ai).extractProfile(sources.capture());
        order.verify(mapper).upsertCompetencyProfile(eq(7L), eq(competency), anyString());
        order.verify(profiles).getProfile(7L);
        assertAll(
                () -> assertEquals(77L, saved.getValue().getItemId()),
                () -> assertEquals("Mock Project", saved.getValue().getTitle()),
                () -> assertEquals("6 weeks", saved.getValue().getPeriodText()),
                () -> assertEquals(4, saved.getValue().getTeamSize()),
                () -> assertEquals(JSON.createArrayNode().add("Java"), saved.getValue().getTechStack()),
                () -> assertEquals("Original role record", saved.getValue().getRoleNote()),
                () -> assertEquals("Original README", saved.getValue().getReadme()),
                () -> assertEquals("Original role record", sources.getValue().get("projects").get(0).get("roleNote").asText()),
                () -> assertEquals("Original README", sources.getValue().get("projects").get(0).get("readme").asText()));
    }

    @Test
    void m8CanChangeReadmeAndRoleRecordExplicitlyWhilePreservingOmittedMetadata() {
        prepareExisting();
        prepareExtraction(profile(true), List.of(project()));
        JsonNode item = JSON.createObjectNode().put("readme", "Revised original README").put("roleNote", "Revised role record");

        service.updateItem(7L, 77L, new ProfileItemPatchRequest("PROJECTS", item));

        ArgumentCaptor<ProfileData.Project> saved = ArgumentCaptor.forClass(ProfileData.Project.class);
        verify(mapper).updateProjectOriginal(eq(7L), saved.capture());
        assertAll(
                () -> assertEquals("Revised original README", saved.getValue().getReadme()),
                () -> assertEquals("Revised role record", saved.getValue().getRoleNote()),
                () -> assertEquals("Mock Project", saved.getValue().getTitle()),
                () -> assertEquals("6 weeks", saved.getValue().getPeriodText()),
                () -> assertEquals(4, saved.getValue().getTeamSize()),
                () -> assertEquals(JSON.createArrayNode().add("Java"), saved.getValue().getTechStack()),
                () -> assertEquals("mock-portfolio.pdf", saved.getValue().getPortfolioFileName()));
        verify(mapper, never()).updateProjectMetadata(any(), any());
        verify(mapper, never()).updateProjectInclude(any(), any(), anyBoolean());
    }

    @Test
    void m8MetadataPatchPreservesOriginalsAndExplicitNullOnlyClearsRequestedOptionalField() {
        prepareExisting();
        prepareExtraction(profile(true), List.of(project()));
        JsonNode item = JSON.createObjectNode().put("title", "Updated Project").putNull("teamSize");

        service.updateItem(7L, 77L, new ProfileItemPatchRequest("PROJECTS", item));

        ArgumentCaptor<ProfileData.Project> saved = ArgumentCaptor.forClass(ProfileData.Project.class);
        verify(mapper).updateProjectOriginal(eq(7L), saved.capture());
        assertAll(
                () -> assertEquals("Updated Project", saved.getValue().getTitle()),
                () -> assertNull(saved.getValue().getTeamSize()),
                () -> assertEquals("Original role record", saved.getValue().getRoleNote()),
                () -> assertEquals("Original README", saved.getValue().getReadme()),
                () -> assertEquals("6 weeks", saved.getValue().getPeriodText()));
    }

    @Test
    void m8PortfolioPatchOnlyUpdatesIncludeThenReextracts() {
        prepareExisting();
        prepareExtraction(profile(true), List.of(project()));

        service.updateItem(7L, 77L, new ProfileItemPatchRequest(
                "PORTFOLIO", JSON.createObjectNode().put("include", false)));

        verify(mapper).updateProjectInclude(7L, 77L, false);
        verify(mapper, never()).updateProjectOriginal(any(), any());
        verify(mapper, never()).updateProjectMetadata(any(), any());
        verify(ai).extractProfile(any());
    }

    @Test
    void m8CanDeleteOriginalProjectAndExtractWithoutDeletedProject() {
        prepareExisting();
        StudentProfileResponse updated = profile(false);
        prepareExtraction(updated, List.of());

        assertSame(updated, service.deleteItem(7L, 77L, "PROJECTS"));

        InOrder order = inOrder(mapper, ai);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).findProjectItem(7L, 77L);
        order.verify(mapper).deleteProject(7L, 77L);
        order.verify(mapper).findProjects(7L);
        ArgumentCaptor<JsonNode> sources = ArgumentCaptor.forClass(JsonNode.class);
        order.verify(ai).extractProfile(sources.capture());
        assertEquals(0, sources.getValue().get("projects").size());
    }

    @Test
    void missingOrForeignM8ProjectCannotBeChangedOrDeleted() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        when(mapper.findProjectItem(7L, 9999L)).thenReturn(null);

        assertThrows(ProfileItemNotFoundException.class, () -> service.updateItem(
                7L, 9999L, new ProfileItemPatchRequest("PROJECTS", JSON.createObjectNode().put("title", "Updated Project"))));
        assertThrows(ProfileItemNotFoundException.class, () -> service.deleteItem(7L, 9999L, "PROJECTS"));

        verify(mapper, never()).updateProjectOriginal(any(), any());
        verify(mapper, never()).deleteProject(any(), any());
        verifyNoInteractions(profiles, ai);
    }

    @Test
    void missingStudentCannotInsertOriginalProjectOrCallAi() {
        when(mapper.lockStudent(9999L)).thenReturn(null);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.addItem(
                9999L, new ProfileItemRequest("PROJECTS", validItem())));

        assertEquals(ErrorCode.STUDENT_NOT_FOUND, failure.getErrorCode());
        verify(mapper, never()).insertProject(any(), any());
        verifyNoInteractions(profiles, ai);
    }

    private void prepareExisting() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        when(mapper.findProjectItem(7L, 77L)).thenReturn(project());
    }

    private JsonNode prepareExtraction(StudentProfileResponse updated, List<ProfileData.Project> projects) {
        when(profiles.getProfile(7L)).thenReturn(updated);
        when(mapper.findProjects(7L)).thenReturn(projects);
        JsonNode competency = JSON.createObjectNode();
        when(ai.extractProfile(any(JsonNode.class))).thenReturn(JSON.createObjectNode().set("profile", competency));
        return competency;
    }

    private static JsonNode validItem() {
        var item = JSON.createObjectNode().put("title", "Mock Project").put("periodText", "6 weeks")
                .put("teamSize", 4).put("roleNote", "Original role record").put("readme", "Original README");
        item.set("techStack", JSON.createArrayNode().add("Java"));
        return item;
    }

    private static ProfileData.Project project() {
        var project = new ProfileData.Project();
        project.setItemId(77L);
        project.setTitle("Mock Project");
        project.setPeriodText("6 weeks");
        project.setTeamSize(4);
        project.setTechStack(JSON.createArrayNode().add("Java"));
        project.setRoleNote("Original role record");
        project.setReadme("Original README");
        project.setPortfolioFileName("mock-portfolio.pdf");
        project.setIncludeInApply(true);
        project.setSource("REGISTERED");
        return project;
    }

    private static StudentProfileResponse profile(boolean includeProject) {
        var project = new StudentProfileResponse.Project(
                77L, "Mock Project", "6 weeks", 4, List.of("Java"), "mock-portfolio.pdf", true);
        return new StudentProfileResponse(
                7L, null,
                new StudentProfileResponse.Basic("Mock Student", null, null, null, null, null, LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(
                        2L, "Mock Course", "1", "Mock Campus", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 8, 29)),
                List.of(), List.of(), List.of(), List.of(), includeProject ? List.of(project) : List.of(), null, null);
    }
}
