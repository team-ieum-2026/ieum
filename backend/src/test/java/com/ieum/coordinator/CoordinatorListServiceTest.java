// 주인: BE-B
package com.ieum.coordinator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CoordinatorListServiceTest {
    private final CoordinatorListMapper mapper = mock(CoordinatorListMapper.class);
    private final CoordinatorListService service = new CoordinatorListService(mapper);

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "ALL")
    void allApplicationsMeansReviewStagesOnly(String status) {
        when(mapper.findApplications(any())).thenReturn(List.of());
        service.applications(status);
        verify(mapper).findApplications(List.of("SUBMITTED", "REJECTED", "APPROVED", "DELIVERED"));
    }

    @Test
    void applicationRowKeepsDeadlineAndConvertsTimesToSeoul() {
        var row = new CoordinatorListData.ApplicationRow();
        row.setApplicationId(3L);
        row.setStudentId(7L);
        row.setStatus("APPROVED");
        row.setSubmittedAt(LocalDateTime.of(2026, 11, 2, 9, 0));
        row.setReviewedAt(LocalDateTime.of(2026, 11, 2, 10, 0));
        row.setDeadlineOn(LocalDate.of(2026, 11, 6));
        row.setUntilFilled(true);
        when(mapper.findApplications(List.of("APPROVED"))).thenReturn(List.of(row));

        var item = service.applications("APPROVED").get(0);

        assertEquals("2026-11-02T09:00+09:00", item.submittedAt().toString());
        assertEquals("2026-11-02T10:00+09:00", item.reviewedAt().toString());
        assertNull(item.deliveredAt());
        assertEquals(LocalDate.of(2026, 11, 6), item.deadlineOn());
        assertTrue(item.untilFilled());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "GENERATING", "FAILED", "submitted", ""})
    void unknownOrStudentSideApplicationStatusIs400(String status) {
        var e = assertThrows(BusinessException.class, () -> service.applications(status));
        assertEquals(ErrorCode.INVALID_REQUEST, e.getErrorCode());
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "ALL")
    void allHelpRequestsHasNoFilter(String status) {
        when(mapper.findHelpRequests(null)).thenReturn(List.of());
        service.helpRequests(status);
        verify(mapper).findHelpRequests(null);
    }

    @Test
    void helpRequestRowKeepsCloseInfo() {
        var row = new CoordinatorListData.HelpRequestRow();
        row.setHelpRequestId(19L);
        row.setStatus("CLOSED");
        row.setCreatedAt(LocalDateTime.of(2026, 11, 1, 9, 0));
        row.setClosedAt(LocalDateTime.of(2026, 11, 2, 9, 0));
        row.setCloseReason("EMPLOYED");
        when(mapper.findHelpRequests("CLOSED")).thenReturn(List.of(row));

        var item = service.helpRequests("CLOSED").get(0);

        assertEquals("CLOSED", item.status());
        assertEquals("2026-11-02T09:00+09:00", item.closedAt().toString());
        assertEquals("EMPLOYED", item.closeReason());
        assertNull(item.jobTitle());
        assertNull(item.helpType());
    }

    @ParameterizedTest
    @ValueSource(strings = {"NEED_HELP", "open", ""})
    void unknownHelpStatusIs400(String status) {
        var e = assertThrows(BusinessException.class, () -> service.helpRequests(status));
        assertEquals(ErrorCode.INVALID_REQUEST, e.getErrorCode());
        verifyNoInteractions(mapper);
    }
}
