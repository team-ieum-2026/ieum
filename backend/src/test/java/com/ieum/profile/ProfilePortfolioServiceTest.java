// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ProfilePortfolioServiceTest {

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
    void patchesFilenameAndIncludeWithoutReplacingProjectOriginalsAndQueuesRefresh() {
        prepareExisting(true);
        StudentProfileResponse updated = profile("mock-replacement.pdf", false);
        prepareSavedProfile(updated);
        var item = JSON.createObjectNode().put("fileName", "mock-replacement.pdf").put("include", false)
                .put("title", "Ignored Title").put("readme", "Ignored README").put("roleNote", "Ignored Role");

        assertSame(updated, service.updateItem(7L, 77L, new ProfileItemPatchRequest("PORTFOLIO", item)));

        InOrder order = inOrder(mapper, profiles, events);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).findProjectItem(7L, 77L);
        order.verify(mapper).updatePortfolio(7L, 77L, "mock-replacement.pdf", false);
        order.verify(events).publishEvent(new ProfileChangedEvent(7L));
        order.verify(profiles).getProfile(7L);
        verify(mapper, never()).updateProjectOriginal(any(), any());
        verify(mapper, never()).updateProjectMetadata(any(), any());
        verify(mapper, never()).deleteProject(any(), any());
    }

    @Test
    void filenameOnlyPatchKeepsStoredIncludeFlag() {
        prepareExisting(false);
        prepareSavedProfile(profile("mock-replacement.pdf", false));

        service.updateItem(7L, 77L, new ProfileItemPatchRequest("PORTFOLIO",
                JSON.createObjectNode().put("fileName", "mock-replacement.pdf")));

        verify(mapper).updatePortfolio(7L, 77L, "mock-replacement.pdf", false);
        verify(mapper, never()).updateProjectInclude(any(), any(), anyBoolean());
    }

    @Test
    void explicitNullFilenameClearsOnlyAttachmentAndPreservesOmittedInclude() {
        prepareExisting(true);
        prepareSavedProfile(profile(null, true));

        service.updateItem(7L, 77L, new ProfileItemPatchRequest("PORTFOLIO",
                JSON.createObjectNode().putNull("fileName")));

        verify(mapper).updatePortfolio(7L, 77L, null, true);
        verify(mapper, never()).deleteProject(any(), any());
        verify(mapper, never()).updateProjectOriginal(any(), any());
    }

    @Test
    void deletingPortfolioClearsFilenameAndIncludeWhilePreservingProject() {
        prepareExisting(true);
        StudentProfileResponse updated = profile(null, false);
        prepareSavedProfile(updated);

        assertSame(updated, service.deleteItem(7L, 77L, "PORTFOLIO"));

        InOrder order = inOrder(mapper, profiles, events);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).findProjectItem(7L, 77L);
        order.verify(mapper).updatePortfolio(7L, 77L, null, false);
        order.verify(events).publishEvent(new ProfileChangedEvent(7L));
        order.verify(profiles).getProfile(7L);
        verify(mapper, never()).deleteProject(any(), any());
        verify(mapper, never()).updateProjectOriginal(any(), any());
        verify(mapper, never()).updateProjectMetadata(any(), any());
    }

    @Test
    void missingOrForeignProjectCannotHaveAttachmentChangedOrDeleted() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        when(mapper.findProjectItem(7L, 9999L)).thenReturn(null);

        assertThrows(ProfileItemNotFoundException.class, () -> service.updateItem(7L, 9999L,
                new ProfileItemPatchRequest("PORTFOLIO", JSON.createObjectNode().put("fileName", "mock.pdf"))));
        assertThrows(ProfileItemNotFoundException.class, () -> service.deleteItem(7L, 9999L, "PORTFOLIO"));

        verify(mapper, never()).updatePortfolio(any(), any(), any(), anyBoolean());
        verify(mapper, never()).deleteProject(any(), any());
        verifyNoInteractions(profiles, ai, events);
    }

    @Test
    void missingStudentCannotChangeOrDeletePortfolio() {
        when(mapper.lockStudent(9999L)).thenReturn(null);

        BusinessException update = assertThrows(BusinessException.class, () -> service.updateItem(9999L, 77L,
                new ProfileItemPatchRequest("PORTFOLIO", JSON.createObjectNode().put("fileName", "mock.pdf"))));
        BusinessException delete = assertThrows(BusinessException.class,
                () -> service.deleteItem(9999L, 77L, "PORTFOLIO"));

        assertEquals(ErrorCode.STUDENT_NOT_FOUND, update.getErrorCode());
        assertEquals(ErrorCode.STUDENT_NOT_FOUND, delete.getErrorCode());
        verify(mapper, never()).updatePortfolio(any(), any(), any(), anyBoolean());
        verifyNoInteractions(profiles, ai, events);
    }

    @Test
    void documentPortfolioUpdatesOnlyIncludeAndKeepsRegisteredFilenameAndOriginals() {
        ProfileService documentService = spy(new ProfileService(mapper, events));
        prepareExisting(true);

        documentService.updateItem(7L, "PORTFOLIO", 77L, JSON.createObjectNode()
                .put("fileName", "ignored-document.pdf").put("include", false)
                .put("readme", "Ignored README").put("roleNote", "Ignored Role"));

        verify(mapper).updateProjectInclude(7L, 77L, false);
        verify(mapper, never()).updatePortfolio(any(), any(), any(), anyBoolean());
        verify(mapper, never()).updateProjectOriginal(any(), any());
        verify(mapper, never()).updateProjectMetadata(any(), any());
        verify(events).publishEvent(new ProfileChangedEvent(7L));
        verify(documentService, never()).getProfile(any());
    }

    @Test
    void portfolioPostCannotCreateFakeProjectToHoldFilename() {
        when(mapper.lockStudent(7L)).thenReturn(7L);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.addItem(7L,
                new ProfileItemRequest("PORTFOLIO", JSON.createObjectNode().put("fileName", "mock.pdf"))));

        assertEquals(ErrorCode.INVALID_REQUEST, failure.getErrorCode());
        verify(mapper, never()).insertProject(any(), any());
        verify(mapper, never()).updatePortfolio(any(), any(), any(), anyBoolean());
        verifyNoInteractions(profiles, ai, events);
    }

    private void prepareExisting(boolean include) {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        when(mapper.findProjectItem(7L, 77L)).thenReturn(project("mock-original.pdf", include));
    }

    private void prepareSavedProfile(StudentProfileResponse result) {
        when(profiles.getProfile(7L)).thenReturn(result);
    }

    private static ProfileData.Project project(String filename, boolean include) {
        var project = new ProfileData.Project();
        project.setItemId(77L);
        project.setTitle("Mock Project");
        project.setPeriodText("6 weeks");
        project.setTeamSize(4);
        project.setTechStack(JSON.createArrayNode().add("Java"));
        project.setRoleNote("Original role record");
        project.setReadme("Original README");
        project.setPortfolioFileName(filename);
        project.setIncludeInApply(include);
        project.setSource("REGISTERED");
        return project;
    }

    private static StudentProfileResponse profile(String filename, boolean include) {
        var project = new StudentProfileResponse.Project(
                77L, "Mock Project", "6 weeks", 4, List.of("Java"), filename, include);
        return new StudentProfileResponse(7L, null,
                new StudentProfileResponse.Basic("Mock Student", null, null, null, null, null, LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(2L, "Mock Course", "1", "Mock Campus", null, null),
                List.of(), List.of(), List.of(), List.of(), List.of(project), null, null);
    }
}
