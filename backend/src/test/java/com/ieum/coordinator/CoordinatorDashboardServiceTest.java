// 주인: BE-B
package com.ieum.coordinator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class CoordinatorDashboardServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T03:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 12, 0);
    private static final OffsetDateTime AS_OF = NOW.atOffset(ZoneOffset.ofHours(9));

    @Mock
    private CoordinatorDashboardMapper mapper;
    private CoordinatorDashboardService service;

    @BeforeEach
    void setUp() {
        service = new CoordinatorDashboardService(mapper, CLOCK);
    }

    @Test
    void noStudentsOrTasksReturnsZeroCountersAndEmptyListsWithOneSeoulAsOf() {
        prepare(List.of());

        CoordinatorDashboardResponse result = service.getDashboard();

        assertAll(
                () -> assertEquals(AS_OF, result.asOf()),
                () -> assertEquals(ZoneOffset.ofHours(9), result.asOf().getOffset()),
                () -> assertEquals(0, result.totalStudents()),
                () -> assertEquals(new CoordinatorDashboardResponse.StatusSummary(0,
                        new CoordinatorDashboardResponse.Seeking(0, 0, 0, 0), 0, 0), result.statusSummary()),
                () -> assertTrue(result.helpRequests().isEmpty()),
                () -> assertTrue(result.reviewQueue().isEmpty()),
                () -> assertTrue(result.callList().isEmpty()),
                () -> assertTrue(result.notesToCheck().isEmpty()));
        verifyAllReadsAndNoOtherCalls();
    }

    @Test
    void fiftyMixedStudentsAreCountedOnceAndSeekingSubtotalsExcludeOtherStatuses() {
        List<CoordinatorDashboardData.StudentState> students = new ArrayList<>();
        for (long id = 1; id <= 50; id++) {
            String status = id <= 10 ? "EMPLOYED" : id <= 14 ? "NEED_HELP" : id <= 21 ? "CHECK_REQUIRED"
                    : "SEEKING";
            // Deliberately stale subStatus values on non-SEEKING students must not inflate seeking counters.
            String subStatus = id <= 25 ? "APPLYING" : id <= 47 ? "WAITING" : "OTHER";
            students.add(student(id, status, subStatus, NOW.minusDays(id % 9), NOW.toLocalDate().minusDays(30)));
        }
        Collections.rotate(students, 13);
        prepare(students);

        CoordinatorDashboardResponse result = service.getDashboard();

        var summary = result.statusSummary();
        assertAll(
                () -> assertEquals(50, result.totalStudents()),
                () -> assertEquals(10, summary.employed()),
                () -> assertEquals(4, summary.needHelp()),
                () -> assertEquals(7, summary.checkRequired()),
                () -> assertEquals(29, summary.seeking().total()),
                () -> assertEquals(4, summary.seeking().applying()),
                () -> assertEquals(22, summary.seeking().waiting()),
                () -> assertEquals(3, summary.seeking().other()),
                () -> assertEquals(result.totalStudents(), summary.employed() + summary.needHelp()
                        + summary.checkRequired() + summary.seeking().total()),
                () -> assertEquals(summary.seeking().total(), summary.seeking().applying()
                        + summary.seeking().waiting() + summary.seeking().other()),
                () -> assertEquals(7, result.callList().size()));
        verifyAllReadsAndNoOtherCalls();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "UNKNOWN", "employed"})
    void missingOrUnknownStatusIsDisplayedAsCheckRequiredWithoutDroppingTheStudent(String status) {
        prepare(List.of(student(7L, status, "APPLYING", NOW.minusHours(1), NOW.toLocalDate().minusDays(30))));

        CoordinatorDashboardResponse result = service.getDashboard();

        assertEquals(1, result.totalStudents());
        assertEquals(new CoordinatorDashboardResponse.StatusSummary(0,
                new CoordinatorDashboardResponse.Seeking(0, 0, 0, 0), 0, 1), result.statusSummary());
        assertEquals(List.of(7L), result.callList().stream().map(CoordinatorDashboardResponse.CallListItem::studentId)
                .toList());
        assertEquals(0, result.callList().get(0).daysSinceResponse());
        verifyAllReadsAndNoOtherCalls();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "UNKNOWN", "WAITING"})
    void seekingWithoutARecognizedSubStatusStillContributesToWaitingAndBothTotals(String subStatus) {
        prepare(List.of(student(7L, "SEEKING", subStatus, NOW.minusDays(1), NOW.toLocalDate().minusDays(30))));

        CoordinatorDashboardResponse result = service.getDashboard();

        assertEquals(1, result.totalStudents());
        assertEquals(new CoordinatorDashboardResponse.Seeking(1, 0, 1, 0), result.statusSummary().seeking());
        assertTrue(result.callList().isEmpty());
        verifyAllReadsAndNoOtherCalls();
    }

    @Test
    void callsUseResponseOrCompletionMidnightOrderAndIncludeRecentAndFutureCheckSignalsWithZeroDays() {
        LocalDate oldCompletion = NOW.toLocalDate().minusDays(100);
        prepare(List.of(
                student(9, "CHECK_REQUIRED", null, NOW.minusDays(1), oldCompletion),
                student(8, "CHECK_REQUIRED", null, null, NOW.toLocalDate().minusDays(10)),
                student(5, "CHECK_REQUIRED", null, NOW.minusDays(7), oldCompletion),
                student(4, "CHECK_REQUIRED", null, NOW.minusDays(7), oldCompletion),
                student(2, "CHECK_REQUIRED", null, NOW.plusDays(1), oldCompletion),
                student(7, "CHECK_REQUIRED", null, NOW.minusMinutes(5), oldCompletion),
                student(6, "CHECK_REQUIRED", null, NOW.minusDays(12).plusHours(2), oldCompletion),
                student(1, "EMPLOYED", null, NOW.minusDays(60), oldCompletion),
                student(3, "NEED_HELP", null, NOW.minusDays(60), oldCompletion),
                student(10, "SEEKING", "WAITING", NOW.minusDays(60), oldCompletion)));

        CoordinatorDashboardResponse result = service.getDashboard();
        var calls = result.callList();

        assertEquals(List.of(6L, 8L, 4L, 5L, 9L, 7L, 2L),
                calls.stream().map(CoordinatorDashboardResponse.CallListItem::studentId).toList());
        assertEquals(List.of(11L, 10L, 7L, 7L, 1L, 0L, 0L),
                calls.stream().map(CoordinatorDashboardResponse.CallListItem::daysSinceResponse).toList());
        assertNull(calls.get(1).lastResponseAt(), "Completion fallback must not invent a response timestamp");
        assertEquals(AS_OF.minusDays(7), calls.get(2).lastResponseAt());
        assertEquals(AS_OF.minusDays(1), calls.get(4).lastResponseAt(),
                "An actual response must take precedence over the older completion date");
        assertEquals(AS_OF.plusDays(1), calls.get(6).lastResponseAt());
        assertEquals(7, result.statusSummary().checkRequired());
        verifyAllReadsAndNoOtherCalls();
    }

    @Test
    void helpReviewAndNoteRowsKeepMapperOrderingNullableFieldsAndAllReviewEntries() {
        when(mapper.findStudentStates()).thenReturn(List.of());
        var help = new CoordinatorDashboardData.HelpRequest();
        help.setHelpRequestId(4L);
        help.setStudentId(12L);
        help.setStudentName("도움 검증 모의");
        help.setCourseLabel("모의 과정 1기");
        help.setCreatedAt(NOW.minusHours(1));
        when(mapper.findOpenHelpRequests()).thenReturn(List.of(help));
        List<CoordinatorDashboardData.ReviewQueueItem> review = new ArrayList<>();
        for (long id = 1; id <= 12; id++) {
            var item = new CoordinatorDashboardData.ReviewQueueItem();
            item.setApplicationId(id);
            item.setStudentName("서류 검증 모의");
            item.setJobTitle("모의 공고");
            item.setSubmittedAt(NOW.minusHours(13 - id));
            review.add(item);
        }
        when(mapper.findReviewQueue()).thenReturn(review);
        var note = new CoordinatorDashboardData.NoteToCheck();
        note.setStudentId(33L);
        note.setStudentName("비고 검증 모의");
        note.setPassNote("모의 비고\n원문 유지");
        when(mapper.findLatestPassNotes()).thenReturn(List.of(note));

        CoordinatorDashboardResponse result = service.getDashboard();

        assertEquals(List.of(new CoordinatorDashboardResponse.HelpRequest(
                4L, 12L, "도움 검증 모의", "모의 과정 1기", AS_OF.minusHours(1), null, null, null)),
                result.helpRequests());
        assertEquals(12, result.reviewQueue().size(), "Sidebar count must include every submitted application");
        assertEquals(1L, result.reviewQueue().get(0).applicationId());
        assertEquals(12L, result.reviewQueue().get(11).applicationId());
        assertEquals(AS_OF.minusHours(12), result.reviewQueue().get(0).submittedAt());
        assertEquals(List.of(new CoordinatorDashboardResponse.NoteToCheck(33L, "비고 검증 모의", "모의 비고\n원문 유지")),
                result.notesToCheck());
        verifyAllReadsAndNoOtherCalls();
    }

    @Test
    void dashboardDeclaresAReadOnlyRepeatableReadTransaction() throws NoSuchMethodException {
        Transactional transaction = CoordinatorDashboardService.class.getMethod("getDashboard")
                .getAnnotation(Transactional.class);

        assertTrue(transaction.readOnly());
        assertEquals(Isolation.REPEATABLE_READ, transaction.isolation());
    }

    @ParameterizedTest
    @ValueSource(strings = {"students", "help", "review", "notes"})
    void aReadFailurePropagatesWithoutRetryingOrCallingAnyOtherMapperMethod(String stage) {
        IllegalStateException expected = new IllegalStateException("Mock dashboard read failure");
        if ("students".equals(stage)) when(mapper.findStudentStates()).thenThrow(expected);
        else {
            when(mapper.findStudentStates()).thenReturn(List.of());
            if ("help".equals(stage)) when(mapper.findOpenHelpRequests()).thenThrow(expected);
            else {
                when(mapper.findOpenHelpRequests()).thenReturn(List.of());
                if ("review".equals(stage)) when(mapper.findReviewQueue()).thenThrow(expected);
                else {
                    when(mapper.findReviewQueue()).thenReturn(List.of());
                    when(mapper.findLatestPassNotes()).thenThrow(expected);
                }
            }
        }

        assertSame(expected, assertThrows(IllegalStateException.class, service::getDashboard));

        verify(mapper).findStudentStates();
        if (!"students".equals(stage)) verify(mapper).findOpenHelpRequests();
        if ("review".equals(stage) || "notes".equals(stage)) verify(mapper).findReviewQueue();
        if ("notes".equals(stage)) verify(mapper).findLatestPassNotes();
        verifyNoMoreInteractions(mapper);
    }

    private void prepare(List<CoordinatorDashboardData.StudentState> students) {
        when(mapper.findStudentStates()).thenReturn(students);
        when(mapper.findOpenHelpRequests()).thenReturn(List.of());
        when(mapper.findReviewQueue()).thenReturn(List.of());
        when(mapper.findLatestPassNotes()).thenReturn(List.of());
    }

    private static CoordinatorDashboardData.StudentState student(
            long id, String status, String subStatus, LocalDateTime lastResponse, LocalDate completedOn) {
        var student = new CoordinatorDashboardData.StudentState();
        student.setStudentId(id);
        student.setStudentName("집계 검증 모의 " + id);
        student.setStatus(status);
        student.setSubStatus(subStatus);
        student.setLastResponseAt(lastResponse);
        student.setCompletedOn(completedOn);
        return student;
    }

    private void verifyAllReadsAndNoOtherCalls() {
        verify(mapper).findStudentStates();
        verify(mapper).findOpenHelpRequests();
        verify(mapper).findReviewQueue();
        verify(mapper).findLatestPassNotes();
        verifyNoMoreInteractions(mapper);
    }
}
