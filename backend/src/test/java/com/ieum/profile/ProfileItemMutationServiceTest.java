// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ProfileItemMutationServiceTest {

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

    @Mock
    private ApplicationEventPublisher events;

    private ProfileItemService service;

    @BeforeEach
    void setUp() {
        service = new ProfileItemService(mapper, profiles, events);
    }

    @Test
    void partialPatchPreservesFieldsNotSentAndQueuesRefreshAfterUpdate() {
        prepareOwnedCertificate();
        when(mapper.updateCertificate(any(ProfileData.Certificate.class))).thenReturn(1);
        StudentProfileResponse updated = profile("모의 수정 자격증");
        prepareSavedProfile(updated);

        StudentProfileResponse result = service.updateItem(7L, 31L,
                patch(JSON.createObjectNode().put("name", "모의 수정 자격증")));

        assertSame(updated, result);
        ArgumentCaptor<ProfileData.Certificate> captured = ArgumentCaptor.forClass(ProfileData.Certificate.class);
        InOrder order = inOrder(mapper, profiles, events);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).findCertificate(7L, 31L);
        order.verify(mapper).updateCertificate(captured.capture());
        order.verify(events).publishEvent(new ProfileChangedEvent(7L));
        order.verify(profiles).getProfile(7L);
        ProfileData.Certificate saved = captured.getValue();
        assertAll(
                () -> assertEquals(7L, saved.getStudentId()),
                () -> assertEquals(31L, saved.getItemId()),
                () -> assertEquals("모의 수정 자격증", saved.getName()),
                () -> assertEquals("모의 기존 기관", saved.getIssuer()),
                () -> assertEquals(LocalDate.of(2026, 9, 1), saved.getAcquiredOn()));
    }

    @Test
    void explicitNullClearsNullableFieldsAndPreservesName() {
        prepareOwnedCertificate();
        when(mapper.updateCertificate(any(ProfileData.Certificate.class))).thenReturn(1);
        prepareSavedProfile(profile("모의 기존 자격증"));

        service.updateItem(7L, 31L,
                patch(JSON.createObjectNode().putNull("issuer").putNull("acquiredOn")));

        ArgumentCaptor<ProfileData.Certificate> captured = ArgumentCaptor.forClass(ProfileData.Certificate.class);
        verify(mapper).updateCertificate(captured.capture());
        assertAll(
                () -> assertEquals("모의 기존 자격증", captured.getValue().getName()),
                () -> assertNull(captured.getValue().getIssuer()),
                () -> assertNull(captured.getValue().getAcquiredOn()));
    }

    @Test
    void validDatePatchUsesStrictIsoDateAndPreservesOtherValues() {
        prepareOwnedCertificate();
        when(mapper.updateCertificate(any(ProfileData.Certificate.class))).thenReturn(1);
        prepareSavedProfile(profile("모의 기존 자격증"));

        service.updateItem(7L, 31L,
                patch(JSON.createObjectNode().put("acquiredOn", "2026-10-01")));

        ArgumentCaptor<ProfileData.Certificate> captured = ArgumentCaptor.forClass(ProfileData.Certificate.class);
        verify(mapper).updateCertificate(captured.capture());
        assertEquals(LocalDate.of(2026, 10, 1), captured.getValue().getAcquiredOn());
        assertEquals("모의 기존 기관", captured.getValue().getIssuer());
    }

    @Test
    void deletionQueuesRefreshAfterRemovingCertificate() {
        prepareOwnedCertificate();
        when(mapper.deleteCertificate(7L, 31L)).thenReturn(1);
        StudentProfileResponse updated = profile(null);
        prepareSavedProfile(updated);

        StudentProfileResponse result = service.deleteItem(7L, 31L, "CERTIFICATES");

        assertSame(updated, result);
        InOrder order = inOrder(mapper, profiles, events);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).findCertificate(7L, 31L);
        order.verify(mapper).deleteCertificate(7L, 31L);
        order.verify(events).publishEvent(new ProfileChangedEvent(7L));
        order.verify(profiles).getProfile(7L);
    }

    @Test
    void missingOrForeignCertificateCannotBePatched() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        when(mapper.findCertificate(7L, 9999L)).thenReturn(null);

        assertThrows(ProfileItemNotFoundException.class, () -> service.updateItem(
                7L, 9999L, patch(JSON.createObjectNode().put("name", "모의 수정"))));

        verify(mapper, never()).updateCertificate(any(ProfileData.Certificate.class));
        verifyNoInteractions(profiles, ai, events);
    }

    @Test
    void missingOrForeignCertificateCannotBeDeleted() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        when(mapper.findCertificate(7L, 9999L)).thenReturn(null);

        assertThrows(ProfileItemNotFoundException.class, () -> service.deleteItem(7L, 9999L, "CERTIFICATES"));

        verify(mapper, never()).deleteCertificate(any(), any());
        verifyNoInteractions(profiles, ai, events);
    }

    @Test
    void missingStudentDoesNotReadOrDeleteCertificate() {
        when(mapper.lockStudent(9999L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.deleteItem(9999L, 31L, "CERTIFICATES"));

        assertEquals(ErrorCode.STUDENT_NOT_FOUND, error.getErrorCode());
        verify(mapper, never()).findCertificate(any(), any());
        verify(mapper, never()).deleteCertificate(any(), any());
        verifyNoInteractions(profiles, ai, events);
    }

    @ParameterizedTest
    @MethodSource("invalidPatchItems")
    void invalidPatchCannotWriteOrCallAi(JsonNode item) {
        // 검증 순서가 달라도 Mockito의 불필요한 stub 오류를 만들지 않는다.
        org.mockito.Mockito.lenient().when(mapper.lockStudent(7L)).thenReturn(7L);
        org.mockito.Mockito.lenient().when(mapper.findCertificate(7L, 31L)).thenReturn(existingCertificate());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateItem(7L, 31L, patch(item)));

        assertEquals(ErrorCode.INVALID_REQUEST, error.getErrorCode());
        verify(mapper, never()).updateCertificate(any(ProfileData.Certificate.class));
        verifyNoInteractions(profiles, ai, events);
    }

    @Test
    void unsupportedDeleteSectionCannotWriteOrCallAi() {
        org.mockito.Mockito.lenient().when(mapper.lockStudent(7L)).thenReturn(7L);
        org.mockito.Mockito.lenient().when(mapper.findCertificate(7L, 31L)).thenReturn(existingCertificate());
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.deleteItem(7L, 31L, "TRAINING"));

        assertEquals(ErrorCode.INVALID_REQUEST, error.getErrorCode());
        verify(mapper, never()).deleteCertificate(any(), any());
        verifyNoInteractions(profiles, ai, events);
    }

    @Test
    void certificateUpdatePublishesRefreshWithoutCallingAi() {
        prepareOwnedCertificate();
        when(mapper.updateCertificate(any(ProfileData.Certificate.class))).thenReturn(1);
        prepareSavedProfile(profile("모의 수정"));

        service.updateItem(
                7L, 31L, patch(JSON.createObjectNode().put("name", "모의 수정")));

        verify(mapper, never()).upsertCompetencyProfile(any(), any(), anyString());
        verify(events).publishEvent(new ProfileChangedEvent(7L));
        verifyNoInteractions(ai);
    }

    @Test
    void certificateDeletionPublishesRefreshWithoutCallingAi() {
        prepareOwnedCertificate();
        when(mapper.deleteCertificate(7L, 31L)).thenReturn(1);
        prepareSavedProfile(profile(null));

        service.deleteItem(7L, 31L, "CERTIFICATES");

        verify(mapper, never()).upsertCompetencyProfile(any(), any(), anyString());
        verify(events).publishEvent(new ProfileChangedEvent(7L));
        verifyNoInteractions(ai);
    }

    private void prepareOwnedCertificate() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        when(mapper.findCertificate(7L, 31L)).thenReturn(existingCertificate());
    }

    private void prepareSavedProfile(StudentProfileResponse updated) {
        when(profiles.getProfile(7L)).thenReturn(updated);
    }

    private static ProfileItemPatchRequest patch(JsonNode item) {
        return new ProfileItemPatchRequest("CERTIFICATES", item);
    }

    private static StudentProfileResponse.Certificate existingCertificate() {
        return new StudentProfileResponse.Certificate(
                31L, "REGISTERED", "모의 기존 자격증", "모의 기존 기관", LocalDate.of(2026, 9, 1));
    }

    private static Stream<JsonNode> invalidPatchItems() {
        return Stream.of(
                JSON.createObjectNode(),
                JSON.createArrayNode(),
                NullNode.getInstance(),
                JSON.createObjectNode().put("unknownField", "value"),
                JSON.createObjectNode().putNull("name"),
                JSON.createObjectNode().put("name", " "),
                JSON.createObjectNode().put("name", "가".repeat(101)),
                JSON.createObjectNode().put("name", 123),
                JSON.createObjectNode().put("issuer", "가".repeat(101)),
                JSON.createObjectNode().put("issuer", false),
                JSON.createObjectNode().put("acquiredOn", 20261026),
                JSON.createObjectNode().put("acquiredOn", "2026-02-30"),
                JSON.createObjectNode().put("acquiredOn", "0001-01-01"),
                JSON.createObjectNode().put("acquiredOn", "+10000-01-01"));
    }

    private static StudentProfileResponse profile(String certificateName) {
        List<StudentProfileResponse.Certificate> certificates = certificateName == null ? List.of()
                : List.of(new StudentProfileResponse.Certificate(
                        31L, "USER", certificateName, null, LocalDate.of(2026, 9, 1)));
        return new StudentProfileResponse(
                7L, null,
                new StudentProfileResponse.Basic(
                        "모의 수료생", null, null, null, null, null, LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(
                        2L, "모의 과정", "1기", "모의 캠퍼스", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 8, 29)),
                List.of(), List.of(), certificates, List.of(), List.of(), null, null);
    }
}
