// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.ai.AiClient;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class ProfileExtractionWorkerTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock private ProfileMapper mapper;
    @Mock private ProfileService profiles;
    @Mock private AiClient ai;

    private ProfileExtractionWorker worker;

    @BeforeEach
    void setUp() {
        worker = new ProfileExtractionWorker(mapper, profiles, ai, JSON, new ProfileTestTransactionManager());
    }

    @Test
    void callsAiOutsideTransactionAndStoresEmptyRolesAndAchievementsInNewTransaction() {
        JsonNode profile = JSON.createObjectNode()
                .set("roles", JSON.createArrayNode());
        ((com.fasterxml.jackson.databind.node.ObjectNode) profile).set("achievements", JSON.createArrayNode());
        when(profiles.getProfile(7L)).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return registered("Java");
        });
        when(ai.extractProfile(any())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return JSON.createObjectNode().set("profile", profile);
        });
        when(mapper.lockStudent(7L)).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return 7L;
        });
        when(profiles.getCurrentProfile(7L)).thenReturn(registered("Java"));

        worker.refresh(7L);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(mapper).upsertCompetencyProfile(eq(7L), eq(profile), hash.capture());
        assertTrue(hash.getValue().matches("[0-9a-f]{64}"));
    }

    @Test
    void doesNotOverwriteCompetencyWhenSourceChangesWhileAiRuns() {
        when(profiles.getProfile(7L)).thenReturn(registered("Java"));
        when(ai.extractProfile(any())).thenReturn(JSON.createObjectNode().set("profile", JSON.createObjectNode()));
        when(mapper.lockStudent(7L)).thenReturn(7L);
        when(profiles.getCurrentProfile(7L)).thenReturn(registered("Python"));

        worker.refresh(7L);

        verify(mapper, never()).upsertCompetencyProfile(any(), any(), anyString());
    }

    @Test
    void aiFailureLeavesExistingCompetencyUntouched() {
        when(profiles.getProfile(7L)).thenReturn(registered("Java"));
        when(ai.extractProfile(any())).thenThrow(new BusinessException(ErrorCode.AI_SERVER_ERROR));

        assertThrows(BusinessException.class, () -> worker.refresh(7L));

        verify(mapper, never()).lockStudent(any());
        verify(mapper, never()).upsertCompetencyProfile(any(), any(), anyString());
    }

    @ParameterizedTest
    @MethodSource("malformedResponses")
    void malformedAiResultDoesNotReplacePreviousCompetency(JsonNode response) {
        when(profiles.getProfile(7L)).thenReturn(registered("Java"));
        when(ai.extractProfile(any())).thenReturn(response);

        BusinessException failure = assertThrows(BusinessException.class, () -> worker.refresh(7L));

        assertEquals(ErrorCode.AI_SERVER_ERROR, failure.getErrorCode());
        verify(mapper, never()).lockStudent(any());
        verify(mapper, never()).upsertCompetencyProfile(any(), any(), anyString());
    }

    private static Stream<JsonNode> malformedResponses() {
        return Stream.of(null, JSON.nullNode(), JSON.createObjectNode(),
                JSON.createObjectNode().set("profile", JSON.nullNode()),
                JSON.createObjectNode().set("profile", JSON.createArrayNode()),
                JSON.createObjectNode().put("profile", "invalid"));
    }

    private static StudentProfileResponse registered(String skill) {
        return new StudentProfileResponse(7L, null, null, null,
                List.of(), List.of(), List.of(), List.of(skill), List.of(), null, null);
    }
}
