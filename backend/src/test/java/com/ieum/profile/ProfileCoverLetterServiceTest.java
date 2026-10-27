// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ieum.ai.AiClient;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileCoverLetterServiceTest {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

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
    void storesExactCoverLetterAndReextractsFromNewOriginalBeforeReturningUpdatedProfile() {
        String content = "모의 자기소개서\n\"문제 해결\" 경험과 \\기술을 정리했어요.";
        JsonNode previousCompetency = JSON.createObjectNode().put("oldProfile", true);
        JsonNode competency = JSON.createObjectNode().set("roles", JSON.createArrayNode().add("모의 역할"));
        StudentProfileResponse raw = profile(content, previousCompetency);
        StudentProfileResponse refreshed = profile(content, competency);
        when(mapper.lockStudent(7L)).thenReturn(7L);
        when(profiles.getProfile(7L)).thenReturn(raw, refreshed);
        when(mapper.findProjects(7L)).thenReturn(List.of());
        when(ai.extractProfile(any(JsonNode.class))).thenReturn(JSON.createObjectNode().set("profile", competency));

        StudentProfileResponse result = service.updateCoverLetter(7L, content);

        assertSame(refreshed, result);
        ArgumentCaptor<JsonNode> sources = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        InOrder order = inOrder(mapper, profiles, ai);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).upsertCoverLetter(7L, content);
        order.verify(profiles).getProfile(7L);
        order.verify(mapper).findProjects(7L);
        order.verify(ai).extractProfile(sources.capture());
        order.verify(mapper).upsertCompetencyProfile(eq(7L), eq(competency), hash.capture());
        order.verify(profiles).getProfile(7L);
        assertEquals(content, sources.getValue().get("coverLetter").get("content").asText());
        assertEquals(7L, sources.getValue().get("studentId").asLong());
        assertFalse(sources.getValue().has("competencyProfile"), "이전 AI 결과를 추출 근거로 재사용하면 안 된다");
        assertFalse(sources.getValue().has("profileUpdatedAt"));
        assertTrue(hash.getValue().matches("[0-9a-f]{64}"));
    }

    @Test
    void missingStudentCannotWriteCoverLetterOrCallAi() {
        when(mapper.lockStudent(9999L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateCoverLetter(9999L, "모의 자기소개서"));

        assertEquals(ErrorCode.STUDENT_NOT_FOUND, error.getErrorCode());
        verify(mapper).lockStudent(9999L);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(profiles, ai);
    }

    @Test
    void aiFailurePropagatesWithoutSavingCompetencyOrReturningSuccess() {
        String content = "모의 변경 자기소개서";
        when(mapper.lockStudent(7L)).thenReturn(7L);
        when(profiles.getProfile(7L)).thenReturn(profile(content, null));
        when(mapper.findProjects(7L)).thenReturn(List.of());
        BusinessException failure = new BusinessException(ErrorCode.AI_SERVER_ERROR);
        when(ai.extractProfile(any(JsonNode.class))).thenThrow(failure);

        BusinessException result = assertThrows(BusinessException.class,
                () -> service.updateCoverLetter(7L, content));

        assertSame(failure, result);
        verify(mapper).upsertCoverLetter(7L, content);
        verify(mapper, never()).upsertCompetencyProfile(any(), any(), anyString());
        verify(profiles).getProfile(7L);
        verifyNoMoreInteractions(profiles);
    }

    private static StudentProfileResponse profile(String content, JsonNode competency) {
        OffsetDateTime updatedAt = OffsetDateTime.of(2026, 10, 26, 15, 0, 0, 0, ZoneOffset.ofHours(9));
        return new StudentProfileResponse(
                7L, updatedAt,
                new StudentProfileResponse.Basic(
                        "모의 수료생", null, null, null, null, null, LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(
                        2L, "모의 과정", "1기", "모의 캠퍼스", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 8, 29)),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                new StudentProfileResponse.CoverLetter(content, updatedAt), competency);
    }
}
