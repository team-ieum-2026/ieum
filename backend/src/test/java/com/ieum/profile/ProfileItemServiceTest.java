// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ieum.ai.AiClient;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

    private ProfileItemService service;

    @BeforeEach
    void setUp() {
        service = new ProfileItemService(mapper, profileService, aiClient, JSON);
    }

    @Test
    void extractsFromUpdatedCertificateAndProjectOriginalsBeforeReturningRefreshedProfile() {
        ProfileItemRequest request = request();
        JsonNode extracted = JSON.createObjectNode()
                .set("certificates", JSON.createArrayNode().add("모의 자격증"));
        StudentProfileResponse raw = profile(null, JSON.createObjectNode());
        StudentProfileResponse refreshed = profile(
                OffsetDateTime.of(2026, 10, 26, 14, 0, 0, 0, ZoneOffset.ofHours(9)), extracted);
        when(mapper.lockStudent(7L)).thenReturn(7L);
        doAnswer(invocation -> {
            ProfileData.Certificate certificate = invocation.getArgument(0);
            certificate.setItemId(31L);
            return 1;
        }).when(mapper).insertCertificate(any(ProfileData.Certificate.class));
        when(profileService.getProfile(7L)).thenReturn(raw, refreshed);
        when(mapper.findProjects(7L)).thenReturn(List.of(project()));
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenReturn(JSON.createObjectNode().set("profile", extracted));

        StudentProfileResponse result = service.addItem(7L, request);

        assertSame(refreshed, result);
        ArgumentCaptor<ProfileData.Certificate> certificateCaptor = ArgumentCaptor.forClass(ProfileData.Certificate.class);
        ArgumentCaptor<JsonNode> inputCaptor = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        InOrder order = inOrder(mapper, profileService, aiClient);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).insertCertificate(certificateCaptor.capture());
        order.verify(profileService).getProfile(7L);
        order.verify(mapper).findProjects(7L);
        order.verify(aiClient).extractProfile(inputCaptor.capture());
        order.verify(mapper).upsertCompetencyProfile(eq(7L), eq(extracted), hashCaptor.capture());
        order.verify(profileService).getProfile(7L);

        ProfileData.Certificate inserted = certificateCaptor.getValue();
        assertAll(
                () -> assertEquals(7L, inserted.getStudentId()),
                () -> assertEquals(31L, inserted.getItemId()),
                () -> assertEquals(request.item().get("name").asText(), inserted.getName()),
                () -> assertEquals(request.item().get("issuer").asText(), inserted.getIssuer()),
                () -> assertEquals(LocalDate.parse(request.item().get("acquiredOn").asText()), inserted.getAcquiredOn()),
                () -> assertTrue(hashCaptor.getValue().matches("[0-9a-f]{64}")));
        JsonNode input = inputCaptor.getValue();
        assertAll(
                () -> assertEquals(7L, input.get("studentId").asLong()),
                () -> assertFalse(input.has("competencyProfile")),
                () -> assertFalse(input.has("profileUpdatedAt")),
                () -> assertEquals("모의 자격증", input.get("certificates").get(0).get("name").asText()),
                () -> assertEquals("USER", input.get("certificates").get(0).get("source").asText()),
                () -> assertEquals("모의 프로젝트 원문", input.get("projects").get(0).get("readme").asText()),
                () -> assertEquals("모의 API 담당", input.get("projects").get(0).get("roleNote").asText()),
                () -> assertEquals("REGISTERED", input.get("projects").get(0).get("source").asText()));
    }

    @Test
    void missingStudentDoesNotInsertOrCallAi() {
        when(mapper.lockStudent(9999L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.addItem(9999L, request()));

        assertEquals(ErrorCode.STUDENT_NOT_FOUND, error.getErrorCode());
        verify(mapper).lockStudent(9999L);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(profileService, aiClient);
    }

    @Test
    void propagatesAiFailureWithoutSavingCompetencyOrReturningSuccess() {
        prepareExtraction();
        BusinessException failure = new BusinessException(ErrorCode.AI_SERVER_ERROR);
        when(aiClient.extractProfile(any(JsonNode.class))).thenThrow(failure);

        BusinessException error = assertThrows(BusinessException.class, () -> service.addItem(7L, request()));

        assertSame(failure, error);
        verify(mapper, never()).upsertCompetencyProfile(any(), any(), anyString());
        verify(profileService).getProfile(7L);
        verifyNoMoreInteractions(profileService);
    }

    @ParameterizedTest
    @MethodSource("malformedAiResponses")
    void rejectsMalformedAiProfileWithoutSaving(JsonNode response) {
        prepareExtraction();
        when(aiClient.extractProfile(any(JsonNode.class))).thenReturn(response);

        BusinessException error = assertThrows(BusinessException.class, () -> service.addItem(7L, request()));

        assertEquals(ErrorCode.AI_SERVER_ERROR, error.getErrorCode());
        verify(mapper, never()).upsertCompetencyProfile(any(), any(), anyString());
        verify(profileService).getProfile(7L);
        verifyNoMoreInteractions(profileService);
    }

    private void prepareExtraction() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        when(profileService.getProfile(7L)).thenReturn(profile(null, null));
        when(mapper.findProjects(7L)).thenReturn(List.of(project()));
    }

    private static Stream<JsonNode> malformedAiResponses() {
        return Stream.of(
                null,
                NullNode.getInstance(),
                JSON.createObjectNode(),
                JSON.createObjectNode().set("profile", NullNode.getInstance()),
                JSON.createObjectNode().set("profile", JSON.createArrayNode()),
                JSON.createObjectNode().put("profile", "invalid"));
    }

    private static ProfileItemRequest request() {
        return new ProfileItemRequest(
                "CERTIFICATES", JSON.valueToTree(new ProfileItemRequest.CertificateItem(
                        "모의 자격증", "모의 발급기관", LocalDate.of(2026, 9, 1))));
    }

    private static ProfileData.Project project() {
        ProfileData.Project project = new ProfileData.Project();
        project.setItemId(11L);
        project.setTitle("모의 프로젝트");
        project.setReadme("모의 프로젝트 원문");
        project.setRoleNote("모의 API 담당");
        project.setSource("REGISTERED");
        project.setTechStack(JSON.createArrayNode().add("Java"));
        return project;
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
