// 주인: BE-A
package com.ieum.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StudentApplicationServiceTest {

    private ApplicationMapper mapper;
    private StudentApplicationService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ApplicationMapper.class);
        service = new StudentApplicationService(mapper, new ObjectMapper());
    }

    @Test
    void unknownStudentIsNotFound() {
        when(mapper.countStudent(99L)).thenReturn(0);

        BusinessException e = assertThrows(BusinessException.class, () -> service.list(99L));
        assertEquals(ErrorCode.STUDENT_NOT_FOUND, e.getErrorCode());
    }

    @Test
    void emptyStudentReturnsEmptyLists() {
        when(mapper.countStudent(1L)).thenReturn(1);
        when(mapper.selectByStudent(1L)).thenReturn(List.of());
        when(mapper.selectLatestOtherItems(1L)).thenReturn(List.of());

        StudentApplicationsResponse res = service.list(1L);

        assertEquals(List.of(), res.applications());
        assertEquals(List.of(), res.otherItems());
    }

    @Test
    void mapsApplicationsAndOtherItems() {
        when(mapper.countStudent(1L)).thenReturn(1);
        String document = """
                { "sections": [
                  { "section": "BASIC", "source": "REGISTERED", "fields": {} },
                  { "section": "CERTIFICATES", "items": [
                    { "itemId": "cert-1", "source": "REGISTERED" },
                    { "itemId": "cert-2", "source": "USER" } ] },
                  { "section": "COVER_LETTER", "sentences": [
                    { "sentenceId": "cl-1", "source": "AI", "evidence": [ { "refType": "PROJECT", "source": "USER" } ] },
                    { "sentenceId": "cl-4", "source": "USER", "evidence": [] } ] } ] }
                """;
        when(mapper.selectByStudent(1L)).thenReturn(List.of(
                listItem(8L, "GENERATING", null, null),
                listItem(7L, "SUBMITTED", LocalDateTime.of(2026, 10, 24, 10, 12), document),
                listItem(6L, "DRAFT", null, "{ broken")));
        when(mapper.selectLatestOtherItems(1L)).thenReturn(List.of(
                otherItem(92L, "PASS", "JOB_MISMATCH"),
                otherItem(93L, "NONE", null),
                otherItem(94L, "HELP", "OTHER")));

        StudentApplicationsResponse res = service.list(1L);

        assertEquals(3, res.applications().size());
        StudentApplicationsResponse.Application generating = res.applications().get(0);
        assertEquals(8L, generating.applicationId());
        assertNull(generating.submittedAt());
        assertEquals(0, generating.userEditedCount());

        StudentApplicationsResponse.Application submitted = res.applications().get(1);
        assertEquals("SUBMITTED", submitted.status());
        assertEquals(OffsetDateTime.parse("2026-10-24T10:12:00+09:00"), submitted.submittedAt());
        assertEquals(2, submitted.userEditedCount());

        assertEquals(0, res.applications().get(2).userEditedCount());

        assertEquals("JOB_MISMATCH", res.otherItems().get(0).passReason());
        assertNull(res.otherItems().get(1).passReason());
        assertEquals("HELP", res.otherItems().get(2).response());
        assertNull(res.otherItems().get(2).passReason());
    }

    private static ApplicationRows.ListItem listItem(Long id, String status, LocalDateTime submittedAt, String document) {
        ApplicationRows.ListItem row = new ApplicationRows.ListItem();
        row.setApplicationId(id);
        row.setCompanyName("(주)온다랩");
        row.setTitle("백엔드 인턴");
        row.setStatus(status);
        row.setSubmittedAt(submittedAt);
        row.setDocument(document);
        return row;
    }

    private static ApplicationRows.OtherItem otherItem(Long itemId, String response, String passReason) {
        ApplicationRows.OtherItem row = new ApplicationRows.OtherItem();
        row.setItemId(itemId);
        row.setCompanyName("데이터웨이브(주)");
        row.setTitle("주니어 데이터 엔지니어");
        row.setResponse(response);
        row.setPassReason(passReason);
        return row;
    }
}
