// 주인: BE-B
package com.ieum.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CoordinatorStudentServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T03:04:05.678Z"), ZoneOffset.UTC);

    @Mock private CoordinatorStudentMapper mapper;
    private CoordinatorStudentService service;

    @BeforeEach
    void setUp() {
        service = new CoordinatorStudentService(mapper, CLOCK);
    }

    @Test
    void blankFiltersBecomeAbsentAndNoRowsAreFetchedForEmptyResults() {
        var result = service.list(" ", "\t", "\n", 1, 20);

        assertEquals(0, result.total());
        assertTrue(result.items().isEmpty());
        assertEquals(1, result.page());
        assertEquals(20, result.size());
        verify(mapper).countStudents(null, null, null);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void filtersRemainIndependentAndSearchEscapesLiteralWildcards() {
        when(mapper.countStudents("EMPLOYED", "APPLYING", "%A!!B!%C!_%")).thenReturn(1L);
        when(mapper.findStudents("EMPLOYED", "APPLYING", "%A!!B!%C!_%", 0L, 20))
                .thenReturn(List.of(student()));

        var result = service.list("EMPLOYED", "APPLYING", "A!B%C_", 1, 20);

        assertEquals(7L, result.items().get(0).studentId());
        assertEquals(ZoneOffset.ofHours(9), result.items().get(0).lastResponseAt().getOffset());
        verify(mapper).countStudents("EMPLOYED", "APPLYING", "%A!!B!%C!_%");
        verify(mapper).findStudents("EMPLOYED", "APPLYING", "%A!!B!%C!_%", 0L, 20);
    }

    @Test
    void largePageUsesLongOffsetAndDoesNotFetchPastTotal() {
        when(mapper.countStudents(null, null, null)).thenReturn(50L);

        var empty = service.list(null, null, null, Integer.MAX_VALUE, 100);

        assertEquals(50, empty.total());
        assertTrue(empty.items().isEmpty());
        verify(mapper).countStudents(null, null, null);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void largeValidOffsetDoesNotOverflowBeforeMapperCall() {
        when(mapper.countStudents(null, null, null)).thenReturn(Long.MAX_VALUE);

        service.list(null, null, null, Integer.MAX_VALUE, 100);

        verify(mapper).findStudents(null, null, null, 214748364600L, 100);
    }

    @ParameterizedTest
    @MethodSource("invalidListQueries")
    void rejectsInvalidFiltersAndPaginationBeforeAnyDbCall(String status, String subStatus, int page, int size) {
        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.list(status, subStatus, null, page, size));

        assertEquals(ErrorCode.INVALID_REQUEST, failure.getErrorCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void detailKeepsEmptyRecommendationHeadersOriginalEvidenceAndNullableLegacyLogs() {
        var stored = student();
        var competency = JSON.createObjectNode();
        competency.putArray("skills").addObject().put("name", "Java")
                .putArray("evidence").addObject().put("refType", "PROJECT").put("refId", 41L);
        stored.setCompetencyProfile(competency);
        var registered = new CoordinatorStudentData.RegisteredSummary();
        registered.setEducation(2);
        registered.setCertificates(3);
        registered.setProjects(4);
        registered.setHasCoverLetter(true);
        var recent = recommendation(31L);
        var older = recommendation(30L);
        var item = new CoordinatorStudentData.HistoryItem();
        item.setRecommendationId(30L);
        item.setItemId(91L);
        item.setResponse("APPLY");
        item.setApplicationId(17L);
        item.setApplicationStatus("SUBMITTED");
        item.setRespondedAt(LocalDateTime.of(2026, 10, 27, 14, 20));
        var log = new CoordinatorStudentData.ContactLog();
        log.setContactLogId(4L);
        log.setContactType("PHONE");
        log.setCreatedAt(LocalDateTime.of(2026, 10, 26, 9, 10));
        when(mapper.findStudent(7L)).thenReturn(stored);
        when(mapper.findRegisteredSummary(7L)).thenReturn(registered);
        when(mapper.findRecommendations(7L)).thenReturn(List.of(recent, older));
        when(mapper.findHistoryItems(7L)).thenReturn(List.of(item));
        when(mapper.findContactLogs(7L)).thenReturn(List.of(log));

        var detail = service.detail(7L);

        assertSame(competency, detail.competencyProfile());
        assertEquals(new CoordinatorStudentResponse.Registered(2, 3, 4, true), detail.registered());
        assertEquals(2, detail.history().size());
        assertEquals(31L, detail.history().get(0).recommendationId());
        assertTrue(detail.history().get(0).items().isEmpty());
        var response = detail.history().get(1).items().get(0);
        assertEquals(91L, response.itemId());
        assertEquals(17L, response.applicationId());
        assertEquals("SUBMITTED", response.applicationStatus());
        assertEquals(item.getRespondedAt().atOffset(ZoneOffset.ofHours(9)), response.respondedAt());
        assertNull(response.passNote());
        assertNull(detail.contactLogs().get(0).note());
        assertEquals(log.getCreatedAt().atOffset(ZoneOffset.ofHours(9)), detail.contactLogs().get(0).createdAt());
    }

    @Test
    void detailWithoutSourcesKeepsNullCompetencyAndEmptyCollections() {
        when(mapper.findStudent(7L)).thenReturn(student());
        when(mapper.findRegisteredSummary(7L)).thenReturn(new CoordinatorStudentData.RegisteredSummary());

        var detail = service.detail(7L);

        assertNull(detail.competencyProfile());
        assertEquals(new CoordinatorStudentResponse.Registered(0, 0, 0, false), detail.registered());
        assertTrue(detail.history().isEmpty());
        assertTrue(detail.contactLogs().isEmpty());
    }

    @Test
    void missingStudentStopsBeforeReadingDetailOrInsertingContact() {
        BusinessException detailFailure = assertThrows(BusinessException.class, () -> service.detail(99L));
        BusinessException writeFailure = assertThrows(BusinessException.class,
                () -> service.addContactLog(98L, new ContactLogCreateRequest("PHONE", "모의 상담")));

        assertEquals(ErrorCode.STUDENT_NOT_FOUND, detailFailure.getErrorCode());
        assertEquals(ErrorCode.STUDENT_NOT_FOUND, writeFailure.getErrorCode());
        verify(mapper).findStudent(99L);
        verify(mapper).findStudent(98L);
        verifyNoMoreInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PHONE", "MESSAGE", "MEETING"})
    void createsOnlyContactLogAndPreservesNoteWithSecondPrecisionSeoulTime(String type) {
        String note = "  모의 상담 기록\n원문 유지  ";
        when(mapper.findStudent(7L)).thenReturn(student());
        when(mapper.insertContactLog(any())).thenAnswer(invocation -> {
            CoordinatorStudentData.ContactLog row = invocation.getArgument(0);
            row.setContactLogId(51L);
            return 1;
        });

        var result = service.addContactLog(7L, new ContactLogCreateRequest(type, note));

        ArgumentCaptor<CoordinatorStudentData.ContactLog> row = ArgumentCaptor.forClass(
                CoordinatorStudentData.ContactLog.class);
        verify(mapper).findStudent(7L);
        verify(mapper).insertContactLog(row.capture());
        verifyNoMoreInteractions(mapper);
        assertEquals(7L, row.getValue().getStudentId());
        assertEquals(type, result.contactType());
        assertSame(note, result.note());
        assertEquals(51L, result.contactLogId());
        assertEquals(LocalDateTime.of(2026, 10, 28, 12, 4, 5), row.getValue().getCreatedAt());
        assertEquals(row.getValue().getCreatedAt().atOffset(ZoneOffset.ofHours(9)), result.createdAt());
    }

    @Test
    void acceptsFullTextByteBoundaryWithoutTrimmingOrMutatingTheOriginal() {
        String note = "가".repeat(21845);
        when(mapper.findStudent(7L)).thenReturn(student());
        when(mapper.insertContactLog(any())).thenAnswer(invocation -> {
            ((CoordinatorStudentData.ContactLog) invocation.getArgument(0)).setContactLogId(51L);
            return 1;
        });

        assertSame(note, service.addContactLog(7L, new ContactLogCreateRequest("PHONE", note)).note());
    }

    @ParameterizedTest(name = "[{index}] invalid contact request")
    @MethodSource("invalidContactRequests")
    void invalidContactInputNeverReadsOrWritesDb(ContactLogCreateRequest request) {
        BusinessException failure = assertThrows(BusinessException.class, () -> service.addContactLog(7L, request));

        assertEquals(ErrorCode.INVALID_REQUEST, failure.getErrorCode());
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @MethodSource("failedInsertResults")
    void unsuccessfulInsertOrMissingPositiveGeneratedKeyCannotReturnSuccess(int affected, Long generatedKey) {
        when(mapper.findStudent(7L)).thenReturn(student());
        when(mapper.insertContactLog(any())).thenAnswer(invocation -> {
            ((CoordinatorStudentData.ContactLog) invocation.getArgument(0)).setContactLogId(generatedKey);
            return affected;
        });

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.addContactLog(7L, new ContactLogCreateRequest("PHONE", "모의 상담")));

        assertEquals(ErrorCode.INTERNAL_ERROR, failure.getErrorCode());
    }

    private static Stream<Arguments> invalidListQueries() {
        return Stream.of(Arguments.of("seeking", null, 1, 20), Arguments.of("SEEKING ", null, 1, 20),
                Arguments.of(null, "waiting", 1, 20), Arguments.of(null, "UNKNOWN", 1, 20),
                Arguments.of(null, null, 0, 20), Arguments.of(null, null, -1, 20),
                Arguments.of(null, null, 1, 0), Arguments.of(null, null, 1, 101));
    }

    private static Stream<Arguments> invalidContactRequests() {
        return Stream.of(Arguments.of((Object) null),
                Arguments.of(new ContactLogCreateRequest(null, "모의 상담")),
                Arguments.of(new ContactLogCreateRequest("phone", "모의 상담")),
                Arguments.of(new ContactLogCreateRequest("SMS", "모의 상담")),
                Arguments.of(new ContactLogCreateRequest("PHONE", null)),
                Arguments.of(new ContactLogCreateRequest("PHONE", " \n\t")),
                Arguments.of(new ContactLogCreateRequest("PHONE", "가".repeat(21846))),
                Arguments.of(new ContactLogCreateRequest("PHONE", "🙂".repeat(16384))));
    }

    private static Stream<Arguments> failedInsertResults() {
        return Stream.of(Arguments.of(0, 51L), Arguments.of(1, null),
                Arguments.of(1, 0L), Arguments.of(1, -1L));
    }

    private static CoordinatorStudentData.Student student() {
        var student = new CoordinatorStudentData.Student();
        student.setStudentId(7L);
        student.setName("모의 수료생");
        student.setCourseLabel("모의 과정 1기");
        student.setCompletedOn(LocalDate.of(2026, 8, 31));
        student.setStatus("SEEKING");
        student.setSubStatus("WAITING");
        student.setLastResponseAt(LocalDateTime.of(2026, 10, 27, 9, 10));
        return student;
    }

    private static CoordinatorStudentData.Recommendation recommendation(Long id) {
        var recommendation = new CoordinatorStudentData.Recommendation();
        recommendation.setRecommendationId(id);
        recommendation.setCreatedAt(LocalDateTime.of(2026, 10, 27, 9, 0));
        return recommendation;
    }
}
