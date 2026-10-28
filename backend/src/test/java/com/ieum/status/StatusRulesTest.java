// 주인: BE-B
package com.ieum.status;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class StatusRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 12, 0);
    private static final LocalDateTime RECENT = NOW.minusDays(1);

    @Test
    void employedOverridesOpenHelpStaleResponseAndActiveApplication() {
        var facts = new StatusRules.Facts(true, true, true, NOW.minusDays(30), "OTHER");

        assertDecision(StatusRules.decide(facts, NOW), "EMPLOYED", null);
    }

    @Test
    void openHelpOverridesStaleResponseAndActiveApplication() {
        var facts = new StatusRules.Facts(false, true, true, NOW.minusDays(30), "OTHER");

        assertDecision(StatusRules.decide(facts, NOW), "NEED_HELP", null);
    }

    @Test
    void savedEmptyRecommendationRequiresCheckingEvenAfterRecentResponseWithAnApplication() {
        var facts = new StatusRules.Facts(false, false, true, RECENT, "OTHER", true);

        StatusDecision decision = StatusRules.decide(facts, NOW);

        assertDecision(decision, "CHECK_REQUIRED", null);
        assertEquals("맞는 공고 없음", decision.reason());
    }

    @ParameterizedTest
    @CsvSource({"true, true, EMPLOYED", "true, false, EMPLOYED", "false, true, NEED_HELP"})
    void employmentAndExplicitHelpOverrideSavedEmptyRecommendation(
            boolean employed, boolean openHelp, String expectedStatus) {
        var facts = new StatusRules.Facts(employed, openHelp, true, NOW.minusDays(30), "OTHER", true);

        assertDecision(StatusRules.decide(facts, NOW), expectedStatus, null);
    }

    @Test
    void savedEmptyRecommendationKeepsItsSpecificReasonWhenResponseIsAlsoStale() {
        var facts = new StatusRules.Facts(false, false, false, NOW.minusDays(30), null, true);

        assertEquals("맞는 공고 없음", StatusRules.decide(facts, NOW).reason());
    }

    @ParameterizedTest
    @MethodSource("higherPriorityAfterCompletion")
    void employmentAndHelpOverrideSevenDaysSinceCompletion(
            StatusRules.Facts facts, String expectedStatus) {
        assertDecision(StatusRules.decide(facts, NOW), expectedStatus, null);
    }

    static Stream<Arguments> higherPriorityAfterCompletion() {
        LocalDateTime completedAt = NOW.toLocalDate().minusDays(30).atStartOfDay();
        return Stream.of(
                Arguments.of(new StatusRules.Facts(true, true, true, completedAt, "OTHER"), "EMPLOYED"),
                Arguments.of(new StatusRules.Facts(true, false, false, completedAt, null), "EMPLOYED"),
                Arguments.of(new StatusRules.Facts(false, true, true, completedAt, "OTHER"), "NEED_HELP"));
    }

    @ParameterizedTest
    @MethodSource("sevenDayBoundary")
    void sevenDayCutoffIncludesTheBoundaryAndPreservesNanosecondDifferences(
            LocalDateTime lastResponseAt, String expectedStatus, String expectedSubStatus) {
        var facts = new StatusRules.Facts(false, false, true, lastResponseAt, "OTHER");

        assertDecision(StatusRules.decide(facts, NOW), expectedStatus, expectedSubStatus);
    }

    static Stream<Arguments> sevenDayBoundary() {
        LocalDateTime cutoff = NOW.minusDays(7);
        return Stream.of(
                Arguments.of(cutoff.minusNanos(1), "CHECK_REQUIRED", null),
                Arguments.of(cutoff, "CHECK_REQUIRED", null),
                Arguments.of(cutoff.plusNanos(1), "SEEKING", "APPLYING"));
    }

    @Test
    void staleResponseRequiresCheckingWithoutAnApplicationOrPass() {
        var facts = new StatusRules.Facts(false, false, false, NOW.minusDays(8), null);

        assertDecision(StatusRules.decide(facts, NOW), "CHECK_REQUIRED", null);
    }

    @Test
    void activeApplicationOverridesTheLatestOtherPass() {
        var facts = new StatusRules.Facts(false, false, true, RECENT, "OTHER");

        assertDecision(StatusRules.decide(facts, NOW), "SEEKING", "APPLYING");
    }

    @Test
    void latestOtherPassSelectsOtherWhenThereIsNoActiveApplication() {
        var facts = new StatusRules.Facts(false, false, false, RECENT, "OTHER");

        assertDecision(StatusRules.decide(facts, NOW), "SEEKING", "OTHER");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"JOB_MISMATCH", "CONDITION_MISMATCH", "SKILL_GAP", "TAKING_BREAK"})
    void remainingRecentResponsesWaitForTheNextRecommendation(String latestPassReason) {
        var facts = new StatusRules.Facts(false, false, false, RECENT, latestPassReason);

        assertDecision(StatusRules.decide(facts, NOW), "SEEKING", "WAITING");
    }

    @ParameterizedTest
    @MethodSource("sevenDaysAfterCompletion")
    void completionDateAtMidnightUsesInclusiveSevenDayBoundary(
            LocalDateTime at, String expectedStatus, String expectedSubStatus) {
        LocalDateTime completedAt = LocalDate.of(2026, 10, 20).atStartOfDay();
        var facts = new StatusRules.Facts(false, false, false, completedAt, null);

        assertDecision(StatusRules.decide(facts, at), expectedStatus, expectedSubStatus);
    }

    static Stream<Arguments> sevenDaysAfterCompletion() {
        LocalDateTime boundary = LocalDate.of(2026, 10, 27).atStartOfDay();
        return Stream.of(
                Arguments.of(boundary.minusNanos(1), "SEEKING", "WAITING"),
                Arguments.of(boundary, "CHECK_REQUIRED", null),
                Arguments.of(boundary.plusNanos(1), "CHECK_REQUIRED", null));
    }

    @ParameterizedTest
    @MethodSource("staleCompletionWithOtherFacts")
    void sevenDaysWithoutResponseOverridesApplicationsAndOtherPass(StatusRules.Facts facts) {
        assertDecision(StatusRules.decide(facts, NOW), "CHECK_REQUIRED", null);
    }

    static Stream<StatusRules.Facts> staleCompletionWithOtherFacts() {
        LocalDateTime completedAt = NOW.toLocalDate().minusDays(8).atStartOfDay();
        return Stream.of(
                new StatusRules.Facts(false, false, false, completedAt, null),
                new StatusRules.Facts(false, false, true, completedAt, null),
                new StatusRules.Facts(false, false, false, completedAt, "OTHER"),
                new StatusRules.Facts(false, false, true, completedAt, "OTHER"));
    }

    private static void assertDecision(StatusDecision actual, String status, String subStatus) {
        assertAll(
                () -> assertEquals(status, actual.status()),
                () -> assertEquals(subStatus, actual.subStatus()),
                () -> assertNotNull(actual.reason()),
                () -> assertTrue(actual.reason() != null && !actual.reason().isBlank()),
                () -> assertTrue(actual.reason() != null && actual.reason().length() <= 200));
        if (!"SEEKING".equals(status)) assertNull(actual.subStatus());
    }
}
