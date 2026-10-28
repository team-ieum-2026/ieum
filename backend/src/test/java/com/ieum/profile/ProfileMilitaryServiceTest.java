// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ieum.ai.AiClient;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ProfileMilitaryServiceTest {

    private static final ObjectMapper JSON = JsonMapper.builder().addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
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
    void updatesOnlyMilitaryColumnAndQueuesRefreshBeforeReturningSavedProfile() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        StudentProfileResponse updated = profile("SERVED");
        prepareSavedProfile(updated);
        var item = JSON.createObjectNode().put("value", "SERVED").put("name", "Ignored Mock Name")
                .put("desiredJob", "Ignored Job").put("courseId", 99L)
                .put("completedOn", "2000-01-01").put("lastResponseAt", "2000-01-01T00:00:00")
                .put("status", "EMPLOYED");
        item.putArray("skills").add("Ignored Skill");

        assertSame(updated, service.updateItem(7L, 7L, new ProfileItemPatchRequest("MILITARY", item)));

        InOrder order = inOrder(mapper, profiles, events);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).updateMilitary(7L, "SERVED");
        order.verify(events).publishEvent(new ProfileChangedEvent(7L));
        order.verify(profiles).getProfile(7L);
        verify(mapper, never()).updateBasic(any());
        verify(mapper, never()).findCurrentStudent(any());
    }

    @Test
    void explicitNullClearsMilitaryThroughPatchAndQueuesRefresh() {
        when(mapper.lockStudent(7L)).thenReturn(7L);
        prepareSavedProfile(profile(null));

        StudentProfileResponse result = service.updateItem(7L, 7L,
                new ProfileItemPatchRequest("MILITARY", JSON.createObjectNode().putNull("value")));

        verify(mapper).updateMilitary(7L, null);
        assertNull(result.basic().militaryStatus());
        verify(events).publishEvent(new ProfileChangedEvent(7L));
    }

    @Test
    void wrongSingletonIdCannotWriteAnotherStudentsMilitaryStatus() {
        when(mapper.lockStudent(7L)).thenReturn(7L);

        assertThrows(ProfileItemNotFoundException.class, () -> service.updateItem(7L, 8L,
                new ProfileItemPatchRequest("MILITARY", JSON.createObjectNode().put("value", "SERVED"))));

        verify(mapper, never()).updateMilitary(any(), any());
        verifyNoInteractions(profiles, ai, events);
    }

    @Test
    void missingStudentUsesAgreedBusinessExceptionBeforeWriting() {
        when(mapper.lockStudent(9999L)).thenReturn(null);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.updateItem(9999L, 9999L,
                new ProfileItemPatchRequest("MILITARY", JSON.createObjectNode().put("value", "SERVED"))));

        assertEquals(ErrorCode.STUDENT_NOT_FOUND, failure.getErrorCode());
        verify(mapper, never()).updateMilitary(any(), any());
        verifyNoInteractions(profiles, ai, events);
    }

    @Test
    void directServiceCannotPersistUnsupportedMilitaryValue() {
        when(mapper.lockStudent(7L)).thenReturn(7L);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.updateItem(7L, 7L,
                new ProfileItemPatchRequest("MILITARY", JSON.createObjectNode().put("value", "UNKNOWN"))));

        assertEquals(ErrorCode.INVALID_REQUEST, failure.getErrorCode());
        verify(mapper, never()).updateMilitary(any(), any());
        verifyNoInteractions(profiles, ai, events);
    }

    @Test
    void militaryCannotBeAddedOrDeletedAsAnIndependentProfileRow() {
        when(mapper.lockStudent(7L)).thenReturn(7L);

        BusinessException addFailure = assertThrows(BusinessException.class, () -> service.addItem(7L,
                new ProfileItemRequest("MILITARY", JSON.createObjectNode().put("value", "SERVED"))));
        BusinessException deleteFailure = assertThrows(BusinessException.class,
                () -> service.deleteItem(7L, 7L, "MILITARY"));

        assertEquals(ErrorCode.INVALID_REQUEST, addFailure.getErrorCode());
        assertEquals(ErrorCode.INVALID_REQUEST, deleteFailure.getErrorCode());
        verify(mapper, never()).updateMilitary(any(), any());
        verifyNoInteractions(profiles, ai, events);
    }

    @Test
    void documentMilitaryUpdateUsesSharedWriterAndQueuesRefresh() {
        ProfileService documentService = spy(new ProfileService(mapper, events));
        when(mapper.lockStudent(7L)).thenReturn(7L);

        documentService.updateItem(7L, "MILITARY", 7L, JSON.createObjectNode().put("value", "EXEMPT"));

        InOrder order = inOrder(mapper, documentService, events);
        order.verify(mapper).lockStudent(7L);
        order.verify(mapper).updateMilitary(7L, "EXEMPT");
        order.verify(events).publishEvent(new ProfileChangedEvent(7L));
        verify(documentService, never()).getProfile(any());
        verify(mapper, never()).findProjects(any());
    }

    private void prepareSavedProfile(StudentProfileResponse result) {
        when(profiles.getProfile(7L)).thenReturn(result);
    }

    private static StudentProfileResponse profile(String militaryStatus) {
        return new StudentProfileResponse(7L, null,
                new StudentProfileResponse.Basic("Mock Student", "mock@example.com", null,
                        "Mock Backend", "Mock Region", militaryStatus, LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(2L, "Mock Course", "1", "Mock Campus", null, null),
                List.of(), List.of(), List.of(), List.of(), List.of(), null, null);
    }
}
