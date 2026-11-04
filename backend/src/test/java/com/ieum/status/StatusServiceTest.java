// 주인: BE-B
package com.ieum.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatusServiceTest {

    // 같은 순간이라도 입력 Clock의 UTC 시각이 아닌 서울 시각으로 7일을 계산해야 한다.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T01:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 10, 0);

    @Mock
    private StatusMapper mapper;

    private StatusService service;

    @BeforeEach
    void setUp() {
        service = new StatusService(mapper, CLOCK);
        // Mockito의 Long 기본값 0L 대신 실제 "추천 이력 없음" 조회 결과를 사용한다.
        lenient().when(mapper.findLatestRecommendationId(any())).thenReturn(null);
    }

    @Test
    void recalculationLocksStudentLoadsFactsAndSavesStatusWithoutChangingResponseTime() {
        when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusDays(1)));
        prepareFacts(List.of(), List.of(), List.of());

        service.recalculate(7L);

        ArgumentCaptor<StatusDecision> decision = ArgumentCaptor.forClass(StatusDecision.class);
        InOrder order = inOrder(mapper);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).upsertStatus(eq(7L), decision.capture());
        verify(mapper).findResponses(7L);
        verify(mapper).findLatestIntent(7L);
        verify(mapper).findOpenHelpRequestIds(7L);
        verify(mapper).findActiveApplicationIds(7L);
        verify(mapper).findLatestRecommendationId(7L);
        verify(mapper, never()).findRecommendationItemIds(any());
        verify(mapper, never()).updateLastResponseAt(any(), any());
        assertDecision(decision.getValue(), "SEEKING", "WAITING");
        assertNotNull(decision.getValue().reason());
        assertFalse(decision.getValue().reason().isBlank());
    }

    @Test
    void latestSavedEmptyRecommendationKeepsSeekingWithoutChangingResponseTime() {
        when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusHours(1)));
        prepareFacts(List.of(), List.of(), List.of(22L));
        when(mapper.findLatestRecommendationId(7L)).thenReturn(42L);
        when(mapper.findRecommendationItemIds(42L)).thenReturn(List.of());

        service.recalculate(7L);

        StatusDecision decision = savedDecision();
        assertDecision(decision, "SEEKING", "APPLYING");
        verify(mapper, never()).updateLastResponseAt(any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void committedRecalculationUsesOnlyNonlockingFactReads(boolean emptyRecommendation) {
        when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusHours(1)));
        when(mapper.findLatestRecommendationIdUnlocked(7L)).thenReturn(42L);
        when(mapper.findRecommendationItemIdsUnlocked(42L))
                .thenReturn(emptyRecommendation ? List.of() : List.of(101L));

        service.recalculateCommitted(7L);

        StatusDecision decision = savedDecision();
        assertDecision(decision, "SEEKING", "WAITING");
        verify(mapper).findResponsesUnlocked(7L);
        verify(mapper).findLatestIntentUnlocked(7L);
        verify(mapper).findOpenHelpRequestIdsUnlocked(7L);
        verify(mapper).findActiveApplicationIdsUnlocked(7L);
        verify(mapper, never()).findResponses(any());
        verify(mapper, never()).findLatestIntent(any());
        verify(mapper, never()).findOpenHelpRequestIds(any());
        verify(mapper, never()).findActiveApplicationIds(any());
        verify(mapper, never()).findLatestRecommendationId(any());
        verify(mapper, never()).findRecommendationItemIds(any());
        verify(mapper, never()).updateLastResponseAt(any(), any());
    }

    @Test
    void latestRecommendationWithStoredItemsClearsEmptyRecommendationSignal() {
        when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusHours(1)));
        prepareFacts(List.of(), List.of(), List.of());
        when(mapper.findLatestRecommendationId(7L)).thenReturn(43L);
        when(mapper.findRecommendationItemIds(43L)).thenReturn(List.of(101L));

        service.recalculate(7L);

        assertDecision(savedDecision(), "SEEKING", "WAITING");
    }

    @Test
    void exactSevenDayBoundaryUsesSeoulEvenWhenInjectedClockHasUtcZone() {
        when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusDays(7)));
        prepareFacts(List.of(), List.of(), List.of());

        service.recalculate(7L);

        assertDecision(savedDecision(), "CHECK_REQUIRED", null);
        verify(mapper, never()).updateLastResponseAt(any(), any());
    }

    @Test
    void olderEmployedPassIsRecognizedEvenWhenLatestPassAndOtherFactsDiffer() {
        when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusDays(20)));
        prepareFacts(List.of(
                response(99L, "PASS", "OTHER", NOW.minusHours(1)),
                response(98L, "PASS", "ALREADY_EMPLOYED", NOW.minusDays(10))), List.of(11L), List.of(22L));

        service.recalculate(7L);

        assertDecision(savedDecision(), "EMPLOYED", null);
    }

    @ParameterizedTest
    @CsvSource({"true, ALREADY_EMPLOYED", "false, ALREADY_EMPLOYED",
            "true, TAKING_BREAK", "false, TAKING_BREAK"})
    void newSavedApplyReopensSeekingBeforeDocumentSubmissionForBothRecalculationPaths(
            boolean committed, String previousIntentReason) {
        var responses = List.of(
                response(90L, "APPLY", null, NOW.minusMinutes(1)),
                response(99L, "PASS", previousIntentReason, NOW.minusDays(1)));
        when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusMinutes(1)));
        // 지원서가 GENERATING/DRAFT/FAILED여도 저장된 APPLY 자체가 구직 재개의 근거다.
        // 더 큰 항목 번호의 취업 응답도 응답 시각이 과거면 우선하지 않는다.
        if (committed) {
            when(mapper.findResponsesUnlocked(7L)).thenReturn(responses);
            when(mapper.findOpenHelpRequestIdsUnlocked(7L)).thenReturn(List.of());
            when(mapper.findActiveApplicationIdsUnlocked(7L)).thenReturn(List.of());
            when(mapper.findLatestRecommendationIdUnlocked(7L)).thenReturn(null);
            service.recalculateCommitted(7L);
        } else {
            prepareFacts(responses, List.of(), List.of());
            service.recalculate(7L);
        }

        assertDecision(savedDecision(), "SEEKING", "WAITING");
        verify(mapper, never()).updateLastResponseAt(any(), any());
        assertEquals(previousIntentReason, responses.get(1).getPassReason());
    }

    @ParameterizedTest
    @ValueSource(strings = {"NONE", "PASS", "HELP"})
    void restingPassPersistsAcrossLaterOrdinaryResponsesOpenHelpAndStaleness(String laterResponse) {
        when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusDays(10)));
        prepareFacts(List.of(
                response(102L, laterResponse, "OTHER", NOW.minusDays(10)),
                response(101L, "PASS", "TAKING_BREAK", NOW.minusDays(20))), List.of(11L), List.of(22L));

        service.recalculate(7L);

        assertDecision(savedDecision(), "RESTING", null);
        verify(mapper, never()).updateLastResponseAt(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"EMPLOYED, 0", "RESTING, 0", "RESTING, 30"})
    void explicitIntentWinsSameSecondApplyAndCoexistsWithHelpAndEmptyRecommendation(
            String selectedStatus, long secondsAfterApply) {
        LocalDateTime appliedAt = NOW.minusHours(1);
        when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusDays(20)));
        prepareFacts(List.of(response(101L, "APPLY", null, appliedAt)), List.of(11L), List.of(22L));
        when(mapper.findLatestIntent(7L)).thenReturn(intent(selectedStatus, appliedAt.plusSeconds(secondsAfterApply)));
        when(mapper.findLatestRecommendationId(7L)).thenReturn(42L);
        when(mapper.findRecommendationItemIds(42L)).thenReturn(List.of());

        service.recalculate(7L);

        assertDecision(savedDecision(), selectedStatus, null);
        verify(mapper, never()).updateLastResponseAt(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"EMPLOYED, true", "RESTING, true", "EMPLOYED, false", "RESTING, false"})
    void newerApplyOverridesAnExplicitEmploymentOrRestingChoiceAndSurvivesEmptyRecommendation(
            String oldStatus, boolean committed) {
        var responses = List.of(response(101L, "APPLY", null, NOW.minusMinutes(1)));
        when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusMinutes(1)));
        if (committed) {
            when(mapper.findResponsesUnlocked(7L)).thenReturn(responses);
            when(mapper.findLatestIntentUnlocked(7L)).thenReturn(intent(oldStatus, NOW.minusHours(1)));
            when(mapper.findOpenHelpRequestIdsUnlocked(7L)).thenReturn(List.of(11L));
            when(mapper.findActiveApplicationIdsUnlocked(7L)).thenReturn(List.of());
            when(mapper.findLatestRecommendationIdUnlocked(7L)).thenReturn(42L);
            when(mapper.findRecommendationItemIdsUnlocked(42L)).thenReturn(List.of());
            service.recalculateCommitted(7L);
        } else {
            prepareFacts(responses, List.of(11L), List.of());
            when(mapper.findLatestIntent(7L)).thenReturn(intent(oldStatus, NOW.minusHours(1)));
            when(mapper.findLatestRecommendationId(7L)).thenReturn(42L);
            when(mapper.findRecommendationItemIds(42L)).thenReturn(List.of());
            service.recalculate(7L);
        }

        assertDecision(savedDecision(), "SEEKING", "WAITING");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void explicitSeekingWinsSameSecondEmploymentAndEmptyRecommendationButKeepsSevenDayRule(boolean stale) {
        LocalDateTime selectedAt = stale ? NOW.minusDays(7) : NOW.minusMinutes(1);
        when(mapper.lockStudent(7L)).thenReturn(student(selectedAt));
        prepareFacts(List.of(response(101L, "PASS", "ALREADY_EMPLOYED", selectedAt)), List.of(11L), List.of());
        when(mapper.findLatestIntent(7L)).thenReturn(intent("SEEKING", selectedAt));
        when(mapper.findLatestRecommendationId(7L)).thenReturn(42L);
        when(mapper.findRecommendationItemIds(42L)).thenReturn(List.of());

        service.recalculate(7L);

        assertDecision(savedDecision(), stale ? "CHECK_REQUIRED" : "SEEKING", stale ? null : "WAITING");
    }

    @ParameterizedTest
    @ValueSource(strings = {"NONE", "PASS", "HELP"})
    void unrelatedLaterResponsesDoNotUndoSeekingResumedBySavedApply(String latestResponse) {
        when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusMinutes(1)));
        prepareFacts(List.of(
                response(102L, latestResponse, "JOB_MISMATCH", NOW.minusMinutes(1)),
                response(101L, "APPLY", null, NOW.minusHours(1)),
                response(100L, "PASS", "ALREADY_EMPLOYED", NOW.minusDays(1))), List.of(), List.of());

        service.recalculate(7L);

        assertDecision(savedDecision(), "SEEKING", "WAITING");
    }

    @Test
    void employmentChosenAfterAnOlderApplyRemainsEmployedDespiteAnActiveDocument() {
        when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusMinutes(1)));
        prepareFacts(List.of(
                response(90L, "PASS", "ALREADY_EMPLOYED", NOW.minusMinutes(1)),
                response(99L, "APPLY", null, NOW.minusDays(1))), List.of(), List.of(22L));

        service.recalculate(7L);

        assertDecision(savedDecision(), "EMPLOYED", null);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void sameSecondIntentUsesExistingMapperItemIdTieBreak(boolean newerItemIsApply) {
        when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusMinutes(1)));
        var apply = response(newerItemIsApply ? 101L : 100L, "APPLY", null, NOW.minusMinutes(1));
        var employed = response(newerItemIsApply ? 100L : 101L,
                "PASS", "ALREADY_EMPLOYED", NOW.minusMinutes(1));
        prepareFacts(newerItemIsApply ? List.of(apply, employed) : List.of(employed, apply),
                List.of(), List.of());

        service.recalculate(7L);

        assertDecision(savedDecision(), newerItemIsApply ? "SEEKING" : "EMPLOYED",
                newerItemIsApply ? "WAITING" : null);
    }

    @Test
    void applyWithoutASavedResponseTimeCannotClearAnEmploymentChoice() {
        when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusMinutes(1)));
        prepareFacts(List.of(
                response(100L, "PASS", "ALREADY_EMPLOYED", NOW.minusMinutes(1)),
                response(101L, "APPLY", null, null)), List.of(), List.of());

        service.recalculate(7L);

        assertDecision(savedDecision(), "EMPLOYED", null);
    }

    @Test
    void repeatedRecalculationKeepsReopenedSeekingAndDoesNotRewriteTheResponseHistory() {
        when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusMinutes(1)));
        prepareFacts(List.of(
                response(101L, "APPLY", null, NOW.minusMinutes(1)),
                response(100L, "PASS", "ALREADY_EMPLOYED", NOW.minusDays(1))), List.of(), List.of());

        service.recalculate(7L);
        service.recalculate(7L);

        var decisions = ArgumentCaptor.forClass(StatusDecision.class);
        verify(mapper, times(2)).upsertStatus(eq(7L), decisions.capture());
        decisions.getAllValues().forEach(decision -> assertDecision(decision, "SEEKING", "WAITING"));
        verify(mapper, never()).updateLastResponseAt(any(), any());
    }

    @ParameterizedTest
    @CsvSource({
            "NONE, ALREADY_EMPLOYED", "APPLY, ALREADY_EMPLOYED", "HELP, ALREADY_EMPLOYED",
            "NONE, OTHER", "APPLY, OTHER", "HELP, OTHER"
    })
    void nonPassResponsesCannotBecomeEmploymentOrOtherPassFacts(String response, String passReason) {
        when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusHours(1)));
        prepareFacts(List.of(response(99L, response, passReason, NOW.minusMinutes(30))), List.of(), List.of());

        service.recalculate(7L);

        assertDecision(savedDecision(), "SEEKING", "WAITING");
    }

    @Test
    void latestActualPassIsSelectedAfterIgnoringNewerNonPassResponses() {
        when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusHours(1)));
        prepareFacts(List.of(
                response(99L, "APPLY", "ALREADY_EMPLOYED", NOW.minusMinutes(5)),
                response(98L, "HELP", "ALREADY_EMPLOYED", NOW.minusMinutes(10)),
                response(97L, "NONE", "ALREADY_EMPLOYED", NOW.minusMinutes(20)),
                response(96L, "PASS", "OTHER", NOW.minusHours(1)),
                response(95L, "PASS", "JOB_MISMATCH", NOW.minusDays(1))), List.of(), List.of());

        service.recalculate(7L);

        assertDecision(savedDecision(), "SEEKING", "OTHER");
    }

    @Test
    void olderOtherPassDoesNotOverrideLatestDifferentPass() {
        when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusHours(1)));
        prepareFacts(List.of(
                response(99L, "PASS", "JOB_MISMATCH", NOW.minusHours(1)),
                response(98L, "PASS", "OTHER", NOW.minusDays(1))), List.of(), List.of());

        service.recalculate(7L);

        assertDecision(savedDecision(), "SEEKING", "WAITING");
    }

    @Test
    void activeApplicationFactTakesPriorityOverLatestOtherPass() {
        when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusHours(1)));
        prepareFacts(List.of(response(99L, "PASS", "OTHER", NOW.minusHours(1))), List.of(), List.of(22L));

        service.recalculate(7L);

        assertDecision(savedDecision(), "SEEKING", "APPLYING");
    }

    @Test
    void openHelpRequestDoesNotHideSevenDaysWithoutAnyResponseSinceCompletion() {
        when(mapper.lockStudent(7L)).thenReturn(student(null));
        prepareFacts(List.of(), List.of(11L), List.of(22L));

        service.recalculate(7L);

        assertDecision(savedDecision(), "CHECK_REQUIRED", null);
        verify(mapper, never()).updateLastResponseAt(any(), any());
    }

    @Test
    void employmentOverridesSevenDaysWithoutAnyResponseSinceCompletion() {
        when(mapper.lockStudent(7L)).thenReturn(student(null));
        prepareFacts(List.of(response(99L, "PASS", "ALREADY_EMPLOYED", NOW.minusDays(1))),
                List.of(), List.of());

        service.recalculate(7L);

        assertDecision(savedDecision(), "EMPLOYED", null);
        verify(mapper, never()).updateLastResponseAt(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"6, SEEKING, WAITING", "7, CHECK_REQUIRED,", "8, CHECK_REQUIRED,"})
    void missingResponseTimeUsesCompletionDateWithoutChangingResponseTime(
            int daysSinceCompletion, String expectedStatus, String expectedSubStatus) {
        when(mapper.lockStudent(7L)).thenReturn(student(null, NOW.toLocalDate().minusDays(daysSinceCompletion)));
        prepareFacts(List.of(), List.of(), List.of());

        service.recalculate(7L);

        assertDecision(savedDecision(), expectedStatus, expectedSubStatus);
        verify(mapper, never()).updateLastResponseAt(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"-1, SEEKING, WAITING", "0, CHECK_REQUIRED,", "1, CHECK_REQUIRED,"})
    void completionDateBoundaryUsesSeoulMidnightEvenWithUtcClock(
            long secondsFromBoundary, String expectedStatus, String expectedSubStatus) {
        Clock boundaryClock = Clock.fixed(Instant.parse("2026-10-26T15:00:00Z").plusSeconds(secondsFromBoundary),
                ZoneOffset.UTC);
        service = new StatusService(mapper, boundaryClock);
        when(mapper.lockStudent(7L)).thenReturn(student(null, LocalDate.of(2026, 10, 20)));
        prepareFacts(List.of(), List.of(), List.of());

        service.recalculate(7L);

        assertDecision(savedDecision(), expectedStatus, expectedSubStatus);
        verify(mapper, never()).updateLastResponseAt(any(), any());
    }

    @Test
    void recentResponseUsesResponseTimestampDespiteLongPastCompletionDate() {
        when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusHours(1), NOW.toLocalDate().minusDays(100)));
        prepareFacts(List.of(), List.of(), List.of());

        service.recalculate(7L);

        assertDecision(savedDecision(), "SEEKING", "WAITING");
        verify(mapper, never()).updateLastResponseAt(any(), any());
    }

    @Test
    void recordingResponseRereadsStoredTimestampBeforeRecalculating() {
        when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusDays(20)), student(NOW));
        when(mapper.updateLastResponseAt(7L, NOW)).thenReturn(1);
        prepareFacts(List.of(), List.of(), List.of());

        service.recordResponse(7L, NOW);

        ArgumentCaptor<StatusDecision> decision = ArgumentCaptor.forClass(StatusDecision.class);
        InOrder order = inOrder(mapper);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).updateLastResponseAt(7L, NOW);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).upsertStatus(eq(7L), decision.capture());
        verify(mapper, times(2)).lockStudent(7L);
        verify(mapper).findResponses(7L);
        verify(mapper).findOpenHelpRequestIds(7L);
        verify(mapper).findActiveApplicationIds(7L);
        assertDecision(decision.getValue(), "SEEKING", "WAITING");
    }

    @Test
    void olderResponseEventUsesDatabaseFinalTimestampInsteadOfRegressingToEventTime() {
        LocalDateTime recent = NOW.minusHours(1);
        LocalDateTime oldEvent = NOW.minusDays(20);
        when(mapper.lockStudent(7L)).thenReturn(student(recent), student(recent));
        when(mapper.updateLastResponseAt(7L, oldEvent)).thenReturn(0);
        prepareFacts(List.of(), List.of(), List.of());

        service.recordResponse(7L, oldEvent);

        verify(mapper).updateLastResponseAt(7L, oldEvent);
        verify(mapper, times(2)).lockStudent(7L);
        assertDecision(savedDecision(), "SEEKING", "WAITING");
    }

    @Test
    void nullEventTimeFailsBeforeAnyDatabaseOperation() {
        BusinessException failure = assertThrows(BusinessException.class, () -> service.recordResponse(7L, null));

        assertEquals(ErrorCode.INVALID_REQUEST, failure.getErrorCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void missingStudentCannotRecordResponseOrRecalculateStatus() {
        when(mapper.lockStudent(9999L)).thenReturn(null);

        BusinessException record = assertThrows(BusinessException.class, () -> service.recordResponse(9999L, NOW));
        BusinessException recalculate = assertThrows(BusinessException.class, () -> service.recalculate(9999L));

        assertEquals(ErrorCode.STUDENT_NOT_FOUND, record.getErrorCode());
        assertEquals(ErrorCode.STUDENT_NOT_FOUND, recalculate.getErrorCode());
        verify(mapper, never()).updateLastResponseAt(any(), any());
        verify(mapper, never()).findResponses(any());
        verify(mapper, never()).findOpenHelpRequestIds(any());
        verify(mapper, never()).findActiveApplicationIds(any());
        verify(mapper, never()).upsertStatus(any(), any());
    }

    @Test
    void timestampWriteFailureIsPropagatedWithoutRecalculation() {
        when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusDays(1)));
        RuntimeException expected = new IllegalStateException("Mock timestamp write failed");
        when(mapper.updateLastResponseAt(7L, NOW)).thenThrow(expected);

        RuntimeException failure = assertThrows(RuntimeException.class, () -> service.recordResponse(7L, NOW));

        assertSame(expected, failure);
        verify(mapper).lockStudent(7L);
        verify(mapper, never()).findResponses(any());
        verify(mapper, never()).findOpenHelpRequestIds(any());
        verify(mapper, never()).findActiveApplicationIds(any());
        verify(mapper, never()).upsertStatus(any(), any());
    }

    @Test
    void factReadFailureIsPropagatedWithoutSavingStatusOrUpdatingResponseTime() {
        when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusDays(1)));
        RuntimeException expected = new IllegalStateException("Mock response query failed");
        when(mapper.findResponses(7L)).thenThrow(expected);

        RuntimeException failure = assertThrows(RuntimeException.class, () -> service.recalculate(7L));

        assertSame(expected, failure);
        verify(mapper, never()).upsertStatus(any(), any());
        verify(mapper, never()).updateLastResponseAt(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECALCULATE", "RECORD_RESPONSE"})
    void statusWriteFailureIsPropagatedToCaller(String operation) {
        if ("RECORD_RESPONSE".equals(operation)) {
            when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusDays(1)), student(NOW));
            when(mapper.updateLastResponseAt(7L, NOW)).thenReturn(1);
        } else when(mapper.lockStudent(7L)).thenReturn(student(NOW.minusDays(1)));
        prepareFacts(List.of(), List.of(), List.of());
        RuntimeException expected = new IllegalStateException("Mock status write failed");
        when(mapper.upsertStatus(eq(7L), any(StatusDecision.class))).thenThrow(expected);

        RuntimeException failure = assertThrows(RuntimeException.class, () -> {
            if ("RECORD_RESPONSE".equals(operation)) service.recordResponse(7L, NOW);
            else service.recalculate(7L);
        });

        assertSame(expected, failure);
    }

    private void prepareFacts(List<StatusData.Response> responses, List<Long> openHelpIds,
                              List<Long> activeApplicationIds) {
        when(mapper.findResponses(7L)).thenReturn(responses);
        when(mapper.findOpenHelpRequestIds(7L)).thenReturn(openHelpIds);
        when(mapper.findActiveApplicationIds(7L)).thenReturn(activeApplicationIds);
    }

    private StatusDecision savedDecision() {
        ArgumentCaptor<StatusDecision> decision = ArgumentCaptor.forClass(StatusDecision.class);
        verify(mapper).upsertStatus(eq(7L), decision.capture());
        return decision.getValue();
    }

    private static void assertDecision(StatusDecision decision, String status, String subStatus) {
        assertEquals(status, decision.status());
        if (subStatus == null) assertNull(decision.subStatus());
        else assertEquals(subStatus, decision.subStatus());
    }

    private static StatusData.Student student(LocalDateTime lastResponseAt) {
        return student(lastResponseAt, NOW.toLocalDate().minusDays(30));
    }

    /** 잠금 조회가 반환하는 원본 응답 시각과 SQL COALESCE 결과를 준비한다. */
    private static StatusData.Student student(LocalDateTime lastResponseAt, LocalDate completedOn) {
        var student = new StatusData.Student();
        student.setStudentId(7L);
        student.setLastResponseAt(lastResponseAt);
        student.setInactivitySince(lastResponseAt != null ? lastResponseAt : completedOn.atStartOfDay());
        return student;
    }

    private static StatusData.Response response(Long itemId, String response, String passReason,
                                                LocalDateTime respondedAt) {
        var item = new StatusData.Response();
        item.setItemId(itemId);
        item.setResponse(response);
        item.setPassReason(passReason);
        item.setRespondedAt(respondedAt);
        return item;
    }

    private static StatusData.Intent intent(String status, LocalDateTime selectedAt) {
        var intent = new StatusData.Intent();
        intent.setId(201L);
        intent.setStudentId(7L);
        intent.setStatus(status);
        intent.setSelectedAt(selectedAt);
        return intent;
    }
}
