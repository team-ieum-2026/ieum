// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ProfileDocumentItemServiceTest {

    private static final ObjectMapper JSON = JsonMapper.builder().addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    @Mock
    private ProfileMapper mapper;
    @Mock
    private AiClient ai;

    @Mock
    private ApplicationEventPublisher events;

    private ProfileService service;

    @BeforeEach
    void setUp() {
        service = spy(new ProfileService(mapper, events));
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDUCATION", "CAREER", "CERTIFICATES"})
    void documentAddReturnsDatabaseIdAndIgnoresClientOwnershipFields(String section) {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        prepareInsert(section);
        ObjectNode item = validItem(section).put("studentId", 999L).put("itemId", 999L).put("source", "AI");

        assertEquals(77L, service.addItem(7L, section, item));

        InOrder order = inOrder(mapper, service, events);
        order.verify(mapper).lockStudent(7L);
        JsonNode saved = captureSaved(section, true, order);
        assertAll(
                () -> assertEquals(7L, saved.get("studentId").longValue()),
                () -> assertEquals(77L, saved.get("itemId").longValue()),
                () -> assertFalse(saved.has("source")));
        for (String field : fields(section)) assertEquals(item.get(field), saved.get(field));
        verifyRefreshEvent(order);
    }

    @ParameterizedTest
    @MethodSource("invalidAddItems")
    void documentAddRejectsInvalidValuesBeforeWritingOrCallingAi(String section, JsonNode item) {
        when(mapper.lockStudent(7L)).thenReturn(7L);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.addItem(7L, section, item));

        assertEquals(ErrorCode.INVALID_REQUEST, failure.getErrorCode());
        verifyNoRecordWrites();
        verifyNoInteractions(ai);
        verify(service, never()).getCurrentProfile(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDUCATION", "CAREER", "CERTIFICATES"})
    void documentPatchPreservesOmittedFieldsClearsNullAndKeepsItemOwnership(String section) {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        prepareExisting(section, 77L);
        prepareUpdate(section);
        ObjectNode item = switch (section) {
            case "EDUCATION" -> JSON.createObjectNode().putNull("major").put("status", "EXPECTED");
            case "CAREER" -> JSON.createObjectNode().putNull("endedOn").put("position", "Updated Mock Position");
            case "CERTIFICATES" -> JSON.createObjectNode().putNull("acquiredOn").put("issuer", "Updated Mock Issuer");
            default -> throw new IllegalArgumentException(section);
        };
        item.put("studentId", 999L).put("itemId", 999L).put("source", "AI");

        service.updateItem(7L, section, 77L, item);

        InOrder order = inOrder(mapper, service, events);
        order.verify(mapper).lockStudent(7L);
        verifyLookup(section, 77L, order);
        JsonNode saved = captureSaved(section, false, order);
        ObjectNode expected = validItem(section);
        if ("EDUCATION".equals(section)) expected.putNull("major").put("status", "EXPECTED");
        else if ("CAREER".equals(section)) expected.putNull("endedOn").put("position", "Updated Mock Position");
        else expected.putNull("acquiredOn").put("issuer", "Updated Mock Issuer");
        assertAll(
                () -> assertEquals(7L, saved.get("studentId").longValue()),
                () -> assertEquals(77L, saved.get("itemId").longValue()),
                () -> assertFalse(saved.has("source")));
        for (String field : fields(section)) assertEquals(expected.get(field), saved.get(field));
        verifyRefreshEvent(order);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDUCATION", "CAREER", "CERTIFICATES"})
    void documentPatchWithOnlyProtectedFieldsCannotRewriteProfile(String section) {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        // Invalid patches may be rejected before the existing record is read.
        ObjectNode item = JSON.createObjectNode().put("studentId", 999L).put("itemId", 999L).put("source", "AI");

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.updateItem(7L, section, 77L, item));

        assertEquals(ErrorCode.INVALID_REQUEST, failure.getErrorCode());
        verifyNoRecordWrites();
        verifyNoInteractions(ai);
        verify(service, never()).getCurrentProfile(any());
    }

    @Test
    void partialCareerDatePatchIsCheckedAgainstOmittedStoredDate() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        prepareExisting("CAREER", 77L);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.updateItem(
                7L, "CAREER", 77L, JSON.createObjectNode().put("endedOn", "2022-12-31")));

        assertEquals(ErrorCode.INVALID_REQUEST, failure.getErrorCode());
        verify(mapper).findCareerItem(7L, 77L);
        verifyNoRecordWrites();
        verifyNoInteractions(ai);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDUCATION", "CAREER", "CERTIFICATES"})
    void documentDeleteChecksOwnershipAndQueuesRefreshAfterDeletingRecord(String section) {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        prepareExisting(section, 77L);
        prepareDelete(section);

        service.deleteItem(7L, section, 77L);

        InOrder order = inOrder(mapper, service, events);
        order.verify(mapper).lockStudent(7L);
        verifyLookup(section, 77L, order);
        verifyDelete(section, order);
        order.verify(events).publishEvent(new ProfileChangedEvent(7L));
        verify(service, never()).getProfile(any());
        verify(mapper, never()).findProjects(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDUCATION", "CAREER", "CERTIFICATES"})
    void missingOrForeignRecordsUseDocumentBusinessExceptionForUpdateAndDelete(String section) {
        when(mapper.lockStudent(7L)).thenReturn(7L);

        BusinessException update = assertThrows(BusinessException.class,
                () -> service.updateItem(7L, section, 9999L, validItem(section)));
        BusinessException delete = assertThrows(BusinessException.class,
                () -> service.deleteItem(7L, section, 9999L));

        assertAll(
                () -> assertEquals(ErrorCode.INVALID_REQUEST, update.getErrorCode()),
                () -> assertEquals(ErrorCode.INVALID_REQUEST, delete.getErrorCode()));
        verifyNoRecordWrites();
        verifyNoInteractions(ai);
        verify(service, never()).getCurrentProfile(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDUCATION", "CAREER", "CERTIFICATES"})
    void missingStudentCannotAddUpdateOrDeleteProfileRecords(String section) {
        when(mapper.lockStudent(9999L)).thenReturn(null);

        BusinessException add = assertThrows(BusinessException.class,
                () -> service.addItem(9999L, section, validItem(section)));
        BusinessException update = assertThrows(BusinessException.class,
                () -> service.updateItem(9999L, section, 77L, validItem(section)));
        BusinessException delete = assertThrows(BusinessException.class,
                () -> service.deleteItem(9999L, section, 77L));

        assertAll(
                () -> assertEquals(ErrorCode.STUDENT_NOT_FOUND, add.getErrorCode()),
                () -> assertEquals(ErrorCode.STUDENT_NOT_FOUND, update.getErrorCode()),
                () -> assertEquals(ErrorCode.STUDENT_NOT_FOUND, delete.getErrorCode()));
        verifyNoRecordWrites();
        verifyNoInteractions(ai);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BASIC", "MILITARY", "SKILLS", "TRAINING"})
    void singletonAndTrainingSectionsCannotBeAddedOrDeleted(String section) {
        when(mapper.lockStudent(7L)).thenReturn(7L);

        BusinessException add = assertThrows(BusinessException.class,
                () -> service.addItem(7L, section, JSON.createObjectNode()));
        BusinessException delete = assertThrows(BusinessException.class,
                () -> service.deleteItem(7L, section, 7L));

        assertAll(
                () -> assertEquals(ErrorCode.INVALID_REQUEST, add.getErrorCode()),
                () -> assertEquals(ErrorCode.INVALID_REQUEST, delete.getErrorCode()));
        verifyNoRecordWrites();
        verifyNoInteractions(ai);
    }

    @Test
    void trainingCannotBeUpdatedThroughDocumentProfileService() {
        when(mapper.lockStudent(7L)).thenReturn(7L);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.updateItem(
                7L, "TRAINING", 7L, JSON.createObjectNode().put("name", "Mock Training")));

        assertEquals(ErrorCode.INVALID_REQUEST, failure.getErrorCode());
        verifyNoRecordWrites();
        verifyNoInteractions(ai);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "UNKNOWN", "education", " EDUCATION ", "COVER_LETTER"})
    void unsupportedSectionsAreRejectedWithInvalidRequestForEveryOperation(String section) {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        JsonNode item = JSON.createObjectNode().put("schoolName", "Mock School");

        BusinessException add = assertThrows(BusinessException.class, () -> service.addItem(7L, section, item));
        BusinessException update = assertThrows(BusinessException.class,
                () -> service.updateItem(7L, section, 77L, item));
        BusinessException delete = assertThrows(BusinessException.class, () -> service.deleteItem(7L, section, 77L));

        assertAll(
                () -> assertEquals(ErrorCode.INVALID_REQUEST, add.getErrorCode()),
                () -> assertEquals(ErrorCode.INVALID_REQUEST, update.getErrorCode()),
                () -> assertEquals(ErrorCode.INVALID_REQUEST, delete.getErrorCode()));
        verifyNoRecordWrites();
        verifyNoInteractions(ai);
    }

    @ParameterizedTest
    @CsvSource({
            "EDUCATION, ADD", "EDUCATION, UPDATE", "EDUCATION, DELETE",
            "CAREER, ADD", "CAREER, UPDATE", "CAREER, DELETE",
            "CERTIFICATES, ADD", "CERTIFICATES, UPDATE", "CERTIFICATES, DELETE"
    })
    void documentMutationPublishesRefreshWithoutCallingAi(String section, String operation) {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        if ("ADD".equals(operation)) prepareInsert(section);
        else {
            prepareExisting(section, 77L);
            if ("UPDATE".equals(operation)) prepareUpdate(section);
            else prepareDelete(section);
        }

        if ("ADD".equals(operation)) service.addItem(7L, section, validItem(section));
        else if ("UPDATE".equals(operation)) service.updateItem(7L, section, 77L, validItem(section));
        else service.deleteItem(7L, section, 77L);

        verify(events).publishEvent(new ProfileChangedEvent(7L));
        verify(mapper, never()).upsertCompetencyProfile(any(), any(), anyString());
        verify(service, never()).getProfile(any());
        verifyNoInteractions(ai);
    }

    private void prepareInsert(String section) {
        switch (section) {
            case "EDUCATION" -> doAnswer(invocation -> {
                invocation.<ProfileData.Education>getArgument(0).setItemId(77L);
                return 1;
            }).when(mapper).insertEducation(any(ProfileData.Education.class));
            case "CAREER" -> doAnswer(invocation -> {
                invocation.<ProfileData.Career>getArgument(0).setItemId(77L);
                return 1;
            }).when(mapper).insertCareer(any(ProfileData.Career.class));
            case "CERTIFICATES" -> doAnswer(invocation -> {
                invocation.<ProfileData.Certificate>getArgument(0).setItemId(77L);
                return 1;
            }).when(mapper).insertCertificate(any(ProfileData.Certificate.class));
            default -> throw new IllegalArgumentException(section);
        }
    }

    private void prepareExisting(String section, Long itemId) {
        switch (section) {
            case "EDUCATION" -> when(mapper.findEducationItem(7L, itemId)).thenReturn(education());
            case "CAREER" -> when(mapper.findCareerItem(7L, itemId)).thenReturn(career());
            case "CERTIFICATES" -> when(mapper.findCertificate(7L, itemId)).thenReturn(certificate());
            default -> throw new IllegalArgumentException(section);
        }
    }

    private void prepareUpdate(String section) {
        switch (section) {
            case "EDUCATION" -> when(mapper.updateEducation(any(ProfileData.Education.class))).thenReturn(1);
            case "CAREER" -> when(mapper.updateCareer(any(ProfileData.Career.class))).thenReturn(1);
            case "CERTIFICATES" -> when(mapper.updateCertificate(any(ProfileData.Certificate.class))).thenReturn(1);
            default -> throw new IllegalArgumentException(section);
        }
    }

    private void prepareDelete(String section) {
        switch (section) {
            case "EDUCATION" -> when(mapper.deleteEducation(7L, 77L)).thenReturn(1);
            case "CAREER" -> when(mapper.deleteCareer(7L, 77L)).thenReturn(1);
            case "CERTIFICATES" -> when(mapper.deleteCertificate(7L, 77L)).thenReturn(1);
            default -> throw new IllegalArgumentException(section);
        }
    }

    private JsonNode captureSaved(String section, boolean inserted, InOrder order) {
        if ("EDUCATION".equals(section)) {
            var saved = ArgumentCaptor.forClass(ProfileData.Education.class);
            if (inserted) order.verify(mapper).insertEducation(saved.capture());
            else order.verify(mapper).updateEducation(saved.capture());
            return JSON.valueToTree(saved.getValue());
        }
        if ("CAREER".equals(section)) {
            var saved = ArgumentCaptor.forClass(ProfileData.Career.class);
            if (inserted) order.verify(mapper).insertCareer(saved.capture());
            else order.verify(mapper).updateCareer(saved.capture());
            return JSON.valueToTree(saved.getValue());
        }
        var saved = ArgumentCaptor.forClass(ProfileData.Certificate.class);
        if (inserted) order.verify(mapper).insertCertificate(saved.capture());
        else order.verify(mapper).updateCertificate(saved.capture());
        return JSON.valueToTree(saved.getValue());
    }

    private void verifyLookup(String section, Long itemId, InOrder order) {
        switch (section) {
            case "EDUCATION" -> order.verify(mapper).findEducationItem(7L, itemId);
            case "CAREER" -> order.verify(mapper).findCareerItem(7L, itemId);
            case "CERTIFICATES" -> order.verify(mapper).findCertificate(7L, itemId);
            default -> throw new IllegalArgumentException(section);
        }
    }

    private void verifyDelete(String section, InOrder order) {
        switch (section) {
            case "EDUCATION" -> order.verify(mapper).deleteEducation(7L, 77L);
            case "CAREER" -> order.verify(mapper).deleteCareer(7L, 77L);
            case "CERTIFICATES" -> order.verify(mapper).deleteCertificate(7L, 77L);
            default -> throw new IllegalArgumentException(section);
        }
    }

    private void verifyRefreshEvent(InOrder order) {
        order.verify(events).publishEvent(new ProfileChangedEvent(7L));
        verify(service, never()).getProfile(any());
        verify(mapper, never()).findProjects(any());
    }

    private void verifyNoRecordWrites() {
        verify(mapper, never()).insertEducation(any());
        verify(mapper, never()).updateEducation(any());
        verify(mapper, never()).deleteEducation(any(), any());
        verify(mapper, never()).insertCareer(any());
        verify(mapper, never()).updateCareer(any());
        verify(mapper, never()).deleteCareer(any(), any());
        verify(mapper, never()).insertCertificate(any());
        verify(mapper, never()).updateCertificate(any());
        verify(mapper, never()).deleteCertificate(any(), any());
        verify(mapper, never()).upsertCompetencyProfile(any(), any(), anyString());
    }

    private static Stream<Arguments> invalidAddItems() {
        return Stream.of(
                Arguments.of("EDUCATION", null),
                Arguments.of("CAREER", JSON.createArrayNode()),
                Arguments.of("CERTIFICATES", JSON.getNodeFactory().textNode("Mock Certificate")),
                Arguments.of("EDUCATION", JSON.createObjectNode()),
                Arguments.of("CAREER", JSON.createObjectNode()),
                Arguments.of("CERTIFICATES", JSON.createObjectNode()),
                Arguments.of("EDUCATION", validItem("EDUCATION").putNull("schoolName")),
                Arguments.of("CAREER", validItem("CAREER").put("companyName", " ")),
                Arguments.of("CERTIFICATES", validItem("CERTIFICATES").put("name", 42)),
                Arguments.of("EDUCATION", validItem("EDUCATION").put("schoolName", "x".repeat(101))),
                Arguments.of("CAREER", validItem("CAREER").put("position", "x".repeat(101))),
                Arguments.of("CERTIFICATES", validItem("CERTIFICATES").put("issuer", "x".repeat(101))),
                Arguments.of("EDUCATION", validItem("EDUCATION").put("degree", "UNKNOWN")),
                Arguments.of("EDUCATION", validItem("EDUCATION").put("status", "graduated")),
                Arguments.of("EDUCATION", validItem("EDUCATION").put("graduatedOn", "2024-02-30")),
                Arguments.of("CAREER", validItem("CAREER").put("endedOn", "2022-12-31")),
                Arguments.of("CERTIFICATES", validItem("CERTIFICATES").put("acquiredOn", "2024-2-3")),
                Arguments.of("CAREER", validItem("CAREER").put("description", "가".repeat(21846))));
    }

    private static ObjectNode validItem(String section) {
        return switch (section) {
            case "EDUCATION" -> JSON.createObjectNode().put("schoolName", "Mock School").put("major", "Mock Major")
                    .put("degree", "BACHELOR").put("status", "GRADUATED").put("graduatedOn", "2024-02-29");
            case "CAREER" -> JSON.createObjectNode().put("companyName", "Mock Company").put("position", "Mock Position")
                    .put("startedOn", "2023-01-15").put("endedOn", "2024-06-30").put("description", "Mock Description");
            case "CERTIFICATES" -> JSON.createObjectNode().put("name", "Mock Certificate").put("issuer", "Mock Issuer")
                    .put("acquiredOn", "2024-03-20");
            default -> throw new IllegalArgumentException(section);
        };
    }

    private static List<String> fields(String section) {
        return switch (section) {
            case "EDUCATION" -> List.of("schoolName", "major", "degree", "status", "graduatedOn");
            case "CAREER" -> List.of("companyName", "position", "startedOn", "endedOn", "description");
            case "CERTIFICATES" -> List.of("name", "issuer", "acquiredOn");
            default -> throw new IllegalArgumentException(section);
        };
    }

    private static String profileField(String section) {
        return switch (section) {
            case "EDUCATION" -> "education";
            case "CAREER" -> "careers";
            case "CERTIFICATES" -> "certificates";
            default -> throw new IllegalArgumentException(section);
        };
    }

    private static StudentProfileResponse.Education education() {
        return new StudentProfileResponse.Education(
                77L, "REGISTERED", "Mock School", "Mock Major", "BACHELOR", "GRADUATED", LocalDate.of(2024, 2, 29));
    }

    private static StudentProfileResponse.Career career() {
        return new StudentProfileResponse.Career(77L, "REGISTERED", "Mock Company", "Mock Position",
                LocalDate.of(2023, 1, 15), LocalDate.of(2024, 6, 30), "Mock Description");
    }

    private static StudentProfileResponse.Certificate certificate() {
        return new StudentProfileResponse.Certificate(
                77L, "REGISTERED", "Mock Certificate", "Mock Issuer", LocalDate.of(2024, 3, 20));
    }

    private static StudentProfileResponse profile(String deletedSection) {
        return new StudentProfileResponse(
                7L, null,
                new StudentProfileResponse.Basic("Mock Student", null, null, null, null, null, LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(
                        2L, "Mock Course", "1", "Mock Campus", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 8, 29)),
                "EDUCATION".equals(deletedSection) ? List.of() : List.of(education()),
                "CAREER".equals(deletedSection) ? List.of() : List.of(career()),
                "CERTIFICATES".equals(deletedSection) ? List.of() : List.of(certificate()),
                List.of(), List.of(), null, null);
    }
}
