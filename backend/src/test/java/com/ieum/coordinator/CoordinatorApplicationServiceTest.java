// 주인: BE-B
package com.ieum.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class CoordinatorApplicationServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T01:02:03.987Z"), ZoneOffset.UTC);
    private static final LocalDateTime REVIEWED_AT = LocalDateTime.of(2026, 10, 28, 10, 2, 3);
    private static final String NOTE = "  모의 보완 요청\n상담 기록에도 원문 유지  ";

    @Mock private CoordinatorApplicationMapper mapper;
    @Mock private ApplicationEventPublisher events;
    private CoordinatorApplicationService service;

    @BeforeEach
    void setUp() {
        service = new CoordinatorApplicationService(mapper, events, CLOCK);
    }

    @ParameterizedTest
    @CsvSource({"APPROVE,SUBMITTED,APPROVED", "DELIVER,APPROVED,DELIVERED"})
    void approvesAndDeliversAfterLockingAndEmitsOriginalStudentAndApplicationIds(
            String action, String initial, String expected) {
        when(mapper.lockApplication(7L)).thenReturn(target(initial));
        if ("APPROVE".equals(action)) when(mapper.approve(7L, REVIEWED_AT)).thenReturn(1);
        else when(mapper.deliver(7L, REVIEWED_AT)).thenReturn(1);

        var result = mutate(action, 7L);

        assertEquals(7L, result.applicationId());
        assertEquals(expected, result.status());
        var order = inOrder(mapper, events);
        order.verify(mapper).lockApplication(7L);
        if ("APPROVE".equals(action)) order.verify(mapper).approve(7L, REVIEWED_AT);
        else order.verify(mapper).deliver(7L, REVIEWED_AT);
        order.verify(events).publishEvent(new ApplicationReviewChangedEvent(17L, 7L));
        verifyNoMoreInteractions(mapper, events);
    }

    @Test
    void rejectionStoresOriginalNoteAndMessageAuditAtOneSeoulTimestampBeforePublishing() {
        when(mapper.lockApplication(7L)).thenReturn(target("SUBMITTED"));
        when(mapper.reject(7L, NOTE, REVIEWED_AT)).thenReturn(1);
        when(mapper.insertRejectContact(17L, NOTE, REVIEWED_AT)).thenReturn(1);

        var result = service.reject(7L, new CoordinatorApplicationRejectRequest(NOTE));

        assertEquals(7L, result.applicationId());
        assertEquals("REJECTED", result.status());
        var order = inOrder(mapper, events);
        order.verify(mapper).lockApplication(7L);
        order.verify(mapper).reject(7L, NOTE, REVIEWED_AT);
        order.verify(mapper).insertRejectContact(17L, NOTE, REVIEWED_AT);
        order.verify(events).publishEvent(new ApplicationReviewChangedEvent(17L, 7L));
        verifyNoMoreInteractions(mapper, events);
    }

    @Test
    void repeatedRejectionCannotCreateAnotherAuditMessage() {
        when(mapper.lockApplication(7L)).thenReturn(target("SUBMITTED"), target("REJECTED"));
        when(mapper.reject(7L, NOTE, REVIEWED_AT)).thenReturn(1);
        when(mapper.insertRejectContact(17L, NOTE, REVIEWED_AT)).thenReturn(1);
        service.reject(7L, new CoordinatorApplicationRejectRequest(NOTE));

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.reject(7L, new CoordinatorApplicationRejectRequest(NOTE)));

        assertEquals(ErrorCode.INVALID_STATUS, failure.getErrorCode());
        verify(mapper, times(2)).lockApplication(7L);
        verify(mapper).reject(7L, NOTE, REVIEWED_AT);
        verify(mapper).insertRejectContact(17L, NOTE, REVIEWED_AT);
        verify(events).publishEvent(new ApplicationReviewChangedEvent(17L, 7L));
        verifyNoMoreInteractions(mapper, events);
    }

    @ParameterizedTest(name = "{0} rejects stored status {1}")
    @MethodSource("invalidTransitions")
    void invalidStoredStateNeverWritesOrPublishes(String action, String initial) {
        when(mapper.lockApplication(7L)).thenReturn(target(initial));

        BusinessException failure = assertThrows(BusinessException.class, () -> mutate(action, 7L));

        assertEquals(ErrorCode.INVALID_STATUS, failure.getErrorCode());
        verify(mapper).lockApplication(7L);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(events);
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVE", "REJECT", "DELIVER"})
    void conditionalUpdateLosingARaceReturnsConflictWithoutAuditOrEvent(String action) {
        when(mapper.lockApplication(7L)).thenReturn(target("DELIVER".equals(action) ? "APPROVED" : "SUBMITTED"));

        BusinessException failure = assertThrows(BusinessException.class, () -> mutate(action, 7L));

        assertEquals(ErrorCode.INVALID_STATUS, failure.getErrorCode());
        verify(mapper, never()).insertRejectContact(anyLong(), any(), any());
        verifyNoInteractions(events);
    }

    @Test
    void failedAuditInsertPreventsSuccessfulRejectionAndEventPublication() {
        when(mapper.lockApplication(7L)).thenReturn(target("SUBMITTED"));
        when(mapper.reject(7L, NOTE, REVIEWED_AT)).thenReturn(1);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.reject(7L, new CoordinatorApplicationRejectRequest(NOTE)));

        assertEquals(ErrorCode.INTERNAL_ERROR, failure.getErrorCode());
        verify(mapper).insertRejectContact(17L, NOTE, REVIEWED_AT);
        verifyNoInteractions(events);
    }

    @Test
    void updateFailurePropagatesWithoutAuditOrPublishing() {
        IllegalStateException failure = new IllegalStateException("simulated review write failure");
        when(mapper.lockApplication(7L)).thenReturn(target("SUBMITTED"));
        when(mapper.reject(7L, NOTE, REVIEWED_AT)).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> service.reject(7L, new CoordinatorApplicationRejectRequest(NOTE))));

        verify(mapper, never()).insertRejectContact(anyLong(), any(), any());
        verifyNoInteractions(events);
    }

    @Test
    void publisherFailurePropagatesSoTheOriginalTransactionCanRollBack() {
        IllegalStateException failure = new IllegalStateException("simulated event publication failure");
        when(mapper.lockApplication(7L)).thenReturn(target("SUBMITTED"));
        when(mapper.approve(7L, REVIEWED_AT)).thenReturn(1);
        doThrow(failure).when(events).publishEvent(new ApplicationReviewChangedEvent(17L, 7L));

        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.approve(7L)));

        verify(mapper).approve(7L, REVIEWED_AT);
    }

    @ParameterizedTest(name = "[{index}] invalid rejection note")
    @MethodSource("invalidNotes")
    void invalidRejectionInputNeverReadsOrWritesDb(CoordinatorApplicationRejectRequest request) {
        BusinessException failure = assertThrows(BusinessException.class, () -> service.reject(7L, request));

        assertEquals(ErrorCode.INVALID_REQUEST, failure.getErrorCode());
        verifyNoInteractions(mapper, events);
    }

    @Test
    void maximumUtf8TextLengthIsAcceptedWithoutTrimming() {
        String note = "가".repeat(21845);
        when(mapper.lockApplication(7L)).thenReturn(target("SUBMITTED"));
        when(mapper.reject(7L, note, REVIEWED_AT)).thenReturn(1);
        when(mapper.insertRejectContact(17L, note, REVIEWED_AT)).thenReturn(1);

        assertEquals("REJECTED", service.reject(7L, new CoordinatorApplicationRejectRequest(note)).status());

        verify(mapper).reject(7L, note, REVIEWED_AT);
        verify(mapper).insertRejectContact(17L, note, REVIEWED_AT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DETAIL", "APPROVE", "REJECT", "DELIVER"})
    void missingApplicationUsesExistingNotFoundErrorAndDoesNotPublish(String action) {
        BusinessException failure = assertThrows(BusinessException.class, () -> invoke(action, 99L));

        assertEquals(ErrorCode.APPLICATION_NOT_FOUND, failure.getErrorCode());
        if ("DETAIL".equals(action)) verify(mapper).findDetail(99L);
        else verify(mapper).lockApplication(99L);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(events);
    }

    @ParameterizedTest
    @MethodSource("invalidIds")
    void invalidApplicationIdsAreRejectedBeforeDbAccess(String action, Long id) {
        BusinessException failure = assertThrows(BusinessException.class, () -> invoke(action, id));

        assertEquals(ErrorCode.INVALID_REQUEST, failure.getErrorCode());
        verifyNoInteractions(mapper, events);
    }

    @Test
    void detailPreservesRawDocumentAndBuildsStudentJobAndVerificationFromSameRow() throws Exception {
        JsonNode document = JSON.readTree("""
                {"companyInfoStatus":"PARTIAL","excludedSentenceCount":2,"sections":[
                  {"section":"SKILLS","source":"AI","summary":[
                    {"sentenceId":"sk-1","text":"모의 문장","source":"AI",
                     "evidence":[{"refType":"PROJECT","refId":41}]}]}]}
                """);
        when(mapper.findDetail(7L)).thenReturn(detail("SUBMITTED", document));

        var result = service.detail(7L);

        assertEquals(7L, result.applicationId());
        assertEquals("SUBMITTED", result.status());
        assertEquals(17L, result.student().studentId());
        assertEquals(27L, result.job().jobPostingId());
        assertSame(document, result.document());
        assertEquals(1, result.verification().aiSentenceCount());
        assertEquals(1, result.verification().aiSentenceWithEvidence());
        assertEquals(2, result.verification().excludedSentenceCount());
        assertEquals(0, result.verification().userEditedCount());
        assertEquals("PARTIAL", result.verification().companyInfoStatus());
        verify(mapper).findDetail(7L);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(events);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GENERATING", "FAILED"})
    void generatingAndFailedStatesHideStoredDocumentAndReturnEmptyVerification(String status) {
        when(mapper.findDetail(7L)).thenReturn(detail(status, JSON.createObjectNode()
                .put("excludedSentenceCount", 20).put("companyInfoStatus", "FOUND")));

        var result = service.detail(7L);

        assertNull(result.document());
        assertEquals(0, result.verification().aiSentenceCount());
        assertEquals(0, result.verification().aiSentenceWithEvidence());
        assertEquals(0, result.verification().userEditedCount());
        assertEquals(0, result.verification().excludedSentenceCount());
        assertEquals("NOT_FOUND", result.verification().companyInfoStatus());
    }

    @Test
    void absentDocumentRemainsNullInsteadOfCreatingAReviewDocument() {
        when(mapper.findDetail(7L)).thenReturn(detail("SUBMITTED", null));

        var result = service.detail(7L);

        assertNull(result.document());
        assertEquals(0, result.verification().aiSentenceCount());
        assertEquals("NOT_FOUND", result.verification().companyInfoStatus());
    }

    @ParameterizedTest(name = "[{index}] malformed document")
    @MethodSource("invalidDocuments")
    void nonObjectDocumentCannotBePresentedAsAValidReview(JsonNode document) {
        when(mapper.findDetail(7L)).thenReturn(detail("SUBMITTED", document));

        BusinessException failure = assertThrows(BusinessException.class, () -> service.detail(7L));

        assertEquals(ErrorCode.INTERNAL_ERROR, failure.getErrorCode());
        verifyNoInteractions(events);
    }

    private CoordinatorApplicationResponse.StatusResult mutate(String action, Long id) {
        return switch (action) {
            case "APPROVE" -> service.approve(id);
            case "REJECT" -> service.reject(id, new CoordinatorApplicationRejectRequest(NOTE));
            case "DELIVER" -> service.deliver(id);
            default -> throw new IllegalArgumentException("알 수 없는 테스트 작업");
        };
    }

    private Object invoke(String action, Long id) {
        return "DETAIL".equals(action) ? service.detail(id) : mutate(action, id);
    }

    private static Stream<Arguments> invalidTransitions() {
        return Stream.of("APPROVE", "REJECT", "DELIVER").flatMap(action ->
                Stream.of("GENERATING", "DRAFT", "SUBMITTED", "APPROVED", "REJECTED", "DELIVERED", "FAILED",
                                "submitted", "approved")
                        .filter(status -> !("DELIVER".equals(action) ? "APPROVED" : "SUBMITTED").equals(status))
                        .map(status -> Arguments.of(action, status)));
    }

    private static Stream<Arguments> invalidIds() {
        return Stream.of("DETAIL", "APPROVE", "REJECT", "DELIVER").flatMap(action ->
                Stream.of(null, 0L, -1L).map(id -> Arguments.of(action, id)));
    }

    private static Stream<Arguments> invalidNotes() {
        return Stream.of(Arguments.of((Object) null), Arguments.of(new CoordinatorApplicationRejectRequest(null)),
                Arguments.of(new CoordinatorApplicationRejectRequest("")),
                Arguments.of(new CoordinatorApplicationRejectRequest(" \n\t")),
                Arguments.of(new CoordinatorApplicationRejectRequest("가".repeat(21846))),
                Arguments.of(new CoordinatorApplicationRejectRequest("🙂".repeat(16384))));
    }

    private static Stream<JsonNode> invalidDocuments() {
        return Stream.of(JSON.createArrayNode(), JSON.getNodeFactory().textNode("invalid"),
                JSON.getNodeFactory().numberNode(1), JSON.getNodeFactory().booleanNode(true));
    }

    private static CoordinatorApplicationData.ReviewTarget target(String status) {
        var target = new CoordinatorApplicationData.ReviewTarget();
        target.setApplicationId(7L);
        target.setStudentId(17L);
        target.setStatus(status);
        return target;
    }

    private static CoordinatorApplicationData.Detail detail(String status, JsonNode document) {
        var detail = new CoordinatorApplicationData.Detail();
        detail.setApplicationId(7L);
        detail.setStudentId(17L);
        detail.setStudentName("모의 수료생");
        detail.setJobPostingId(27L);
        detail.setCompanyName("모의 기업");
        detail.setTitle("모의 개발 공고");
        detail.setStatus(status);
        detail.setDocument(document);
        return detail;
    }
}
