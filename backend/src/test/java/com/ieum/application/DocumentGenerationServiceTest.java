// 주인: BE-A
package com.ieum.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
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
import com.ieum.profile.ProfileService;
import com.ieum.profile.StudentProfileResponse;
import com.ieum.recommendation.RecommendationRespondedEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class DocumentGenerationServiceTest {

    private final ObjectMapper json = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private ApplicationMapper mapper;
    private ProfileService profileService;
    private AiClient aiClient;
    private DocumentGenerationService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ApplicationMapper.class);
        profileService = mock(ProfileService.class);
        aiClient = mock(AiClient.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new DocumentGenerationService(mapper, profileService, aiClient, json, new TransactionTemplate(txManager));

        when(mapper.selectDraftTarget(7L)).thenReturn(target("GENERATING"));
        when(profileService.getProfile(1L)).thenReturn(profile(null));
        when(mapper.selectDraftProjects(1L)).thenReturn(List.of(project()));
    }

    @Test
    void savesDocumentAndMovesToDraft() throws Exception {
        when(aiClient.draftDocument(any())).thenReturn(json.readTree("{\"document\":{\"sections\":[]}}"));
        when(mapper.updateStatus(7L, "GENERATING", "DRAFT")).thenReturn(1);

        service.generate(7L);

        verify(mapper).upsertDocument(7L, "{\"sections\":[]}");
        verify(mapper, never()).updateStatus(7L, "GENERATING", "FAILED");
    }

    @Test
    void aiErrorMovesToFailed() {
        when(aiClient.draftDocument(any())).thenThrow(new BusinessException(ErrorCode.AI_SERVER_ERROR));

        service.generate(7L);

        verify(mapper).updateStatus(7L, "GENERATING", "FAILED");
        verify(mapper, never()).upsertDocument(any(), anyString());
    }

    @Test
    void responseWithoutDocumentMovesToFailed() throws Exception {
        when(aiClient.draftDocument(any())).thenReturn(json.readTree("{\"document\":null}"));

        service.generate(7L);

        verify(mapper).updateStatus(7L, "GENERATING", "FAILED");
        verify(mapper, never()).upsertDocument(any(), anyString());
    }

    @Test
    void doesNotSaveWhenStatusChangedMeanwhile() throws Exception {
        when(aiClient.draftDocument(any())).thenReturn(json.readTree("{\"document\":{\"sections\":[]}}"));
        when(mapper.updateStatus(7L, "GENERATING", "DRAFT")).thenReturn(0);

        service.generate(7L);

        verify(mapper, never()).upsertDocument(any(), anyString());
    }

    @Test
    void skipsWhenNotGenerating() {
        when(mapper.selectDraftTarget(7L)).thenReturn(target("DRAFT"));

        service.generate(7L);

        verifyNoInteractions(aiClient);
        verify(mapper, never()).updateStatus(any(), any(), any());
    }

    @Test
    void requestFollowsDraftSchema() throws Exception {
        ApplicationRows.CompanyInfo company = new ApplicationRows.CompanyInfo();
        company.setId(3L);
        company.setCompanyName("(주)온다랩");
        company.setStatus("PARTIAL");
        company.setInfo("{\"talentKeywords\":[\"책임감\"],\"coreValues\":[],\"recentIssue\":null,\"sources\":[]}");
        company.setCollectedAt(LocalDateTime.of(2026, 10, 24, 8, 0));
        when(mapper.selectCompanyInfo("(주)온다랩")).thenReturn(company);
        when(aiClient.draftDocument(any())).thenReturn(json.readTree("{\"document\":{\"sections\":[]}}"));
        when(mapper.updateStatus(7L, "GENERATING", "DRAFT")).thenReturn(1);

        service.generate(7L);

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(aiClient).draftDocument(captor.capture());
        JsonNode request = captor.getValue();
        assertEquals(7, request.get("applicationId").asLong());
        JsonNode student = request.get("student");
        assertEquals(1, student.get("studentId").asLong());
        assertTrue(!student.has("competencyProfile") && !student.has("profileUpdatedAt"));
        assertEquals("2026-08-29", student.get("basic").get("completedOn").asText());
        JsonNode project = student.get("projects").get(0);
        assertEquals("README 원문", project.get("readme").asText());
        assertEquals("백엔드 API 담당", project.get("roleNote").asText());
        assertEquals("Spring Boot", project.get("techStack").get(0).asText());
        assertTrue(request.get("profile").isNull());
        assertEquals(501, request.get("job").get("jobPostingId").asLong());
        assertTrue(request.get("job").has("description"));
        JsonNode companyInfo = request.get("companyInfo");
        assertEquals(3, companyInfo.get("companyInfoId").asLong());
        assertEquals("책임감", companyInfo.get("talentKeywords").get(0).asText());
        assertEquals("2026-10-24T08:00+09:00", companyInfo.get("collectedAt").asText());
    }

    @Test
    void listenerStartsOnlyForApply() {
        DocumentGenerationService generation = mock(DocumentGenerationService.class);
        DocumentGenerationListener listener = new DocumentGenerationListener(generation);
        try {
            listener.onResponded(new RecommendationRespondedEvent(1L, 92L, 502L, "PASS", null));
            listener.onResponded(new RecommendationRespondedEvent(1L, 91L, 501L, "APPLY", 7L));

            verify(generation, timeout(2000)).generate(eq(7L));
            verify(generation, never()).generate(eq(null));
        } finally {
            listener.shutdown();
        }
    }

    private static ApplicationRows.DraftTarget target(String status) {
        ApplicationRows.DraftTarget target = new ApplicationRows.DraftTarget();
        target.setApplicationId(7L);
        target.setStudentId(1L);
        target.setStatus(status);
        target.setJobPostingId(501L);
        target.setCompanyName("(주)온다랩");
        target.setTitle("백엔드 인턴");
        target.setDescription("[내용] API 개발");
        return target;
    }

    private static ApplicationRows.DraftProject project() {
        ApplicationRows.DraftProject project = new ApplicationRows.DraftProject();
        project.setItemId(11L);
        project.setTitle("모아요");
        project.setTechStack("[\"Spring Boot\",\"MySQL\"]");
        project.setIncludeInApply(true);
        project.setRoleNote("백엔드 API 담당");
        project.setReadme("README 원문");
        project.setSource("REGISTERED");
        return project;
    }

    private static StudentProfileResponse profile(JsonNode competencyProfile) {
        return new StudentProfileResponse(1L, null,
                new StudentProfileResponse.Basic("모의 수료생", null, null, "백엔드 개발", "서울", null,
                        LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(1L, "모의 과정", "4기", "모의캠퍼스",
                        LocalDate.of(2026, 3, 2), LocalDate.of(2026, 8, 29)),
                List.of(), List.of(), List.of(), List.of("Java"), List.of(), null, competencyProfile);
    }
}
