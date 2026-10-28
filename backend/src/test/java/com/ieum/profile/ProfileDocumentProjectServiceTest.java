// Owner: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ProfileDocumentProjectServiceTest {

    private static final ObjectMapper JSON = JsonMapper.builder().addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    @Mock
    private ProfileMapper mapper;
    @Mock
    private AiClient ai;

    @Mock
    private ApplicationEventPublisher events;

    private ProfileService service;

    @BeforeEach
    void setUp() {
        service = spy(new ProfileService(mapper, events));
    }

    @Test
    void documentAddsMetadataAndReturnsGeneratedIdWithoutStoringDocumentSentencesAsOriginals() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        doAnswer(invocation -> {
            ProfileData.Project saved = invocation.getArgument(1);
            saved.setItemId(77L);
            return 1;
        }).when(mapper).insertProject(eq(7L), any(ProfileData.Project.class));
        var item = documentItem();

        assertEquals(77L, service.addItem(7L, "PROJECTS", item));

        ArgumentCaptor<ProfileData.Project> saved = ArgumentCaptor.forClass(ProfileData.Project.class);
        verify(mapper).insertProject(eq(7L), saved.capture());
        assertAll(
                () -> assertEquals("Updated Project", saved.getValue().getTitle()),
                () -> assertEquals("8 weeks", saved.getValue().getPeriodText()),
                () -> assertEquals(5, saved.getValue().getTeamSize()),
                () -> assertEquals(JSON.createArrayNode().add("Kotlin"), saved.getValue().getTechStack()),
                () -> assertNull(saved.getValue().getReadme()),
                () -> assertNull(saved.getValue().getRoleNote()),
                () -> assertNull(saved.getValue().getPortfolioFileName()),
                () -> assertTrue(saved.getValue().isIncludeInApply()));
    }

    @Test
    void documentUpdatesMetadataAndPreservesOriginalsWhileQueuingRefresh() {
        prepareExisting();

        service.updateItem(7L, "PROJECTS", 77L, documentItem());

        ArgumentCaptor<ProfileData.Project> saved = ArgumentCaptor.forClass(ProfileData.Project.class);
        InOrder order = inOrder(mapper, events);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).findProjectItem(7L, 77L);
        order.verify(mapper).updateProjectMetadata(eq(7L), saved.capture());
        order.verify(events).publishEvent(new ProfileChangedEvent(7L));
        assertAll(
                () -> assertEquals("Updated Project", saved.getValue().getTitle()),
                () -> assertEquals("8 weeks", saved.getValue().getPeriodText()),
                () -> assertEquals(5, saved.getValue().getTeamSize()),
                () -> assertEquals(JSON.createArrayNode().add("Kotlin"), saved.getValue().getTechStack()),
                () -> assertEquals("Original README", saved.getValue().getReadme()),
                () -> assertEquals("Original role record", saved.getValue().getRoleNote()),
                () -> assertEquals("mock-portfolio.pdf", saved.getValue().getPortfolioFileName()),
                () -> assertTrue(saved.getValue().isIncludeInApply()));
        verify(mapper, never()).updateProjectOriginal(any(), any());
        verify(mapper, never()).updateProjectInclude(any(), any(), anyBoolean());
    }

    @Test
    void documentPatchKeepsOmittedTechStackAndOtherMetadata() {
        prepareExisting();

        service.updateItem(7L, "PROJECTS", 77L, JSON.createObjectNode().put("title", "Updated Project"));

        ArgumentCaptor<ProfileData.Project> saved = ArgumentCaptor.forClass(ProfileData.Project.class);
        verify(mapper).updateProjectMetadata(eq(7L), saved.capture());
        assertAll(
                () -> assertEquals("Updated Project", saved.getValue().getTitle()),
                () -> assertEquals("6 weeks", saved.getValue().getPeriodText()),
                () -> assertEquals(4, saved.getValue().getTeamSize()),
                () -> assertEquals(JSON.createArrayNode().add("Java"), saved.getValue().getTechStack()),
                () -> assertEquals("Original README", saved.getValue().getReadme()),
                () -> assertEquals("Original role record", saved.getValue().getRoleNote()));
    }

    @Test
    void documentPatchWithOnlyProtectedFieldsChecksOwnershipWithoutWritingOrCallingAi() {
        prepareExisting();
        var item = JSON.createObjectNode().put("role", "Backend").put("readme", "Generated README")
                .put("roleNote", "Generated role").put("fileName", "replaced.pdf").put("includeInApply", false);
        item.set("bullets", JSON.createArrayNode().add("Generated document sentence"));

        service.updateItem(7L, "PROJECTS", 77L, item);

        verify(mapper).lockStudent(7L);
        verify(mapper).findProjectItem(7L, 77L);
        verify(mapper, never()).updateProjectMetadata(any(), any());
        verify(mapper, never()).updateProjectOriginal(any(), any());
        verify(mapper, never()).updateProjectInclude(any(), any(), anyBoolean());
        verify(mapper, never()).upsertCompetencyProfile(any(), any(), anyString());
        verify(service, never()).getProfile(any());
        verify(service, never()).getCurrentProfile(any());
        verifyNoInteractions(ai);
    }

    @Test
    void documentPortfolioPatchChangesIncludeWithoutReplacingFilenameOrOriginalText() {
        prepareExisting();

        service.updateItem(7L, "PORTFOLIO", 77L, JSON.createObjectNode()
                .put("include", false).put("fileName", "generated.pdf")
                .put("readme", "Generated README").put("roleNote", "Generated role"));

        verify(mapper).updateProjectInclude(7L, 77L, false);
        verify(mapper, never()).updateProjectOriginal(any(), any());
        verify(mapper, never()).updateProjectMetadata(any(), any());
        verify(events).publishEvent(new ProfileChangedEvent(7L));
    }

    @Test
    void documentProjectDeleteChecksOwnershipAndQueuesRefreshAfterRemovingTheRow() {
        prepareExisting();

        service.deleteItem(7L, "PROJECTS", 77L);

        InOrder order = inOrder(mapper, service, events);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).findProjectItem(7L, 77L);
        order.verify(mapper).deleteProject(7L, 77L);
        order.verify(events).publishEvent(new ProfileChangedEvent(7L));
        verify(mapper, never()).updatePortfolio(any(), any(), any(), anyBoolean());
    }

    @Test
    void documentPortfolioDeleteOnlyExcludesItAndPreservesAttachmentAndOriginals() {
        prepareExisting();

        service.deleteItem(7L, "PORTFOLIO", 77L);

        verify(mapper).updateProjectInclude(7L, 77L, false);
        verify(mapper, never()).deleteProject(any(), any());
        verify(mapper, never()).updatePortfolio(any(), any(), any(), anyBoolean());
        verify(events).publishEvent(new ProfileChangedEvent(7L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROJECTS", "PORTFOLIO"})
    void documentDeleteRejectsMissingOrForeignProjectBeforeWriting(String section) {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        when(mapper.findProjectItem(7L, 9999L)).thenReturn(null);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.deleteItem(7L, section, 9999L));

        assertEquals(ErrorCode.INVALID_REQUEST, failure.getErrorCode());
        verify(mapper, never()).deleteProject(any(), any());
        verify(mapper, never()).updateProjectInclude(any(), any(), anyBoolean());
        verify(mapper, never()).updatePortfolio(any(), any(), any(), anyBoolean());
        verifyNoInteractions(ai);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROJECTS", "PORTFOLIO"})
    void documentDeletionPublishesRefreshWithoutCallingAi(String section) {
        prepareExisting();

        service.deleteItem(7L, section, 77L);

        if ("PROJECTS".equals(section)) verify(mapper).deleteProject(7L, 77L);
        else verify(mapper).updateProjectInclude(7L, 77L, false);
        verify(mapper, never()).upsertCompetencyProfile(any(), any(), anyString());
        verify(events).publishEvent(new ProfileChangedEvent(7L));
        verifyNoInteractions(ai);
    }

    @Test
    void documentMissingOrForeignProjectThrowsBusinessExceptionBeforeWriteOrAi() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        when(mapper.findProjectItem(7L, 9999L)).thenReturn(null);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.updateItem(
                7L, "PROJECTS", 9999L, JSON.createObjectNode().put("title", "Updated Project")));

        assertEquals(ErrorCode.INVALID_REQUEST, failure.getErrorCode());
        verify(mapper, never()).updateProjectMetadata(any(), any());
        verify(mapper, never()).updateProjectOriginal(any(), any());
        verifyNoInteractions(ai);
    }

    @Test
    void documentMissingStudentUsesAgreedNotFoundBusinessException() {
        when(mapper.lockStudent(9999L)).thenReturn(null);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.addItem(
                9999L, "PROJECTS", JSON.createObjectNode().put("title", "Mock Project")));

        assertEquals(ErrorCode.STUDENT_NOT_FOUND, failure.getErrorCode());
        verify(mapper, never()).insertProject(any(), any());
        verifyNoInteractions(ai);
    }

    @Test
    void documentRejectsInvalidMetadataWithoutChangingOriginalProject() {
        when(mapper.lockStudent(7L)).thenReturn(7L);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.addItem(
                7L, "PROJECTS", JSON.createObjectNode().put("title", "Mock Project").put("teamSize", 0)));

        assertEquals(ErrorCode.INVALID_REQUEST, failure.getErrorCode());
        verify(mapper, never()).insertProject(any(), any());
        verifyNoInteractions(ai);
    }

    @Test
    void currentProfileUsesCurrentReadsForEverySectionInsteadOfEarlierTransactionSnapshot() {
        var student = new ProfileData.Student();
        student.setStudentId(7L);
        student.setName("Current Mock Student");
        student.setCourseId(2L);
        student.setCourseName("Current Mock Course");
        student.setSkills(JSON.createArrayNode().add("Kotlin"));
        var education = new StudentProfileResponse.Education(
                11L, "USER", "Current Mock School", null, "BACHELOR", "GRADUATED", null);
        var career = new StudentProfileResponse.Career(
                22L, "USER", "Current Mock Company", null, null, null, null);
        var certificate = new StudentProfileResponse.Certificate(
                33L, "USER", "Current Mock Certificate", null, null);
        var project = project();
        project.setTitle("Current Mock Project");
        when(mapper.findCurrentStudent(7L)).thenReturn(student);
        when(mapper.findCurrentEducation(7L)).thenReturn(List.of(education));
        when(mapper.findCurrentCareers(7L)).thenReturn(List.of(career));
        when(mapper.findCurrentCertificates(7L)).thenReturn(List.of(certificate));
        when(mapper.findCurrentProjects(7L)).thenReturn(List.of(project));

        StudentProfileResponse current = service.getCurrentProfile(7L);

        assertAll(
                () -> assertEquals("Current Mock Student", current.basic().name()),
                () -> assertEquals("Current Mock Course", current.course().name()),
                () -> assertEquals(List.of("Kotlin"), current.skills()),
                () -> assertEquals(List.of(education), current.education()),
                () -> assertEquals(List.of(career), current.careers()),
                () -> assertEquals(List.of(certificate), current.certificates()),
                () -> assertEquals("Current Mock Project", current.projects().get(0).title()));
        verify(mapper).findCurrentStudent(7L);
        verify(mapper).findCurrentEducation(7L);
        verify(mapper).findCurrentCareers(7L);
        verify(mapper).findCurrentCertificates(7L);
        verify(mapper).findCurrentProjects(7L);
        verify(mapper, never()).findStudent(any());
        verify(mapper, never()).findEducation(any());
        verify(mapper, never()).findCareers(any());
        verify(mapper, never()).findCertificates(any());
        verify(mapper, never()).findProjects(any());
        verifyNoInteractions(ai);
    }

    private void prepareExisting() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        when(mapper.findProjectItem(7L, 77L)).thenReturn(project());
    }

    private static JsonNode documentItem() {
        var item = JSON.createObjectNode().put("title", "Updated Project").put("periodText", "8 weeks")
                .put("teamSize", 5).put("role", "Backend").put("readme", "Generated README")
                .put("roleNote", "Generated role").put("includeInApply", false)
                .put("fileName", "generated.pdf").put("portfolioFileName", "generated.pdf");
        item.set("techStack", JSON.createArrayNode().add("Kotlin"));
        item.set("bullets", JSON.createArrayNode().add("Generated document sentence"));
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

    private static StudentProfileResponse profile() {
        var project = new StudentProfileResponse.Project(
                77L, "Mock Project", "6 weeks", 4, List.of("Java"), "mock-portfolio.pdf", true);
        return new StudentProfileResponse(
                7L, null,
                new StudentProfileResponse.Basic("Mock Student", null, null, null, null, null, LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(
                        2L, "Mock Course", "1", "Mock Campus", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 8, 29)),
                List.of(), List.of(), List.of(), List.of(), List.of(project), null, null);
    }
}
