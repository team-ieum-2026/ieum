package com.ieum.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import com.ieum.profile.ProfileService;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** 검토 요청(submit)의 공고 마감일 검사: 마감일이 지나면 409 JOB_CLOSED, 당일 · 마감일 없음은 허용 */
class ApplicationSubmitDeadlineTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    private ApplicationDocumentMapper mapper;
    private ApplicationEventPublisher events;
    private ApplicationDocumentService service;
    private ApplicationDocumentMapper.Row row;

    @BeforeEach
    void setUp() {
        mapper = mock(ApplicationDocumentMapper.class);
        events = mock(ApplicationEventPublisher.class);
        service = new ApplicationDocumentService(mapper, mock(ProfileService.class), new ObjectMapper(), events,
                mock(DocumentGenerationListener.class));
        row = new ApplicationDocumentMapper.Row();
        row.setApplicationId(7L);
        row.setStudentId(3L);
        row.setStatus("DRAFT");
        when(mapper.selectDetail(7L)).thenReturn(row);
        when(mapper.submit(7L)).thenReturn(1);
    }

    @Test
    void submitAfterDeadlineIs409WithoutChangingStatus() {
        row.setDeadlineOn(TODAY.minusDays(1));

        var e = assertThrows(BusinessException.class, () -> service.submit(7L));

        assertEquals(ErrorCode.JOB_CLOSED, e.getErrorCode());
        verify(mapper, never()).submit(anyLong());
        verify(mapper, never()).touchLastResponse(anyLong());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void submitOnDeadlineDayOrWithoutDeadlineIsAllowed() {
        row.setDeadlineOn(TODAY);
        assertEquals("SUBMITTED", service.submit(7L).status());

        row.setDeadlineOn(null);
        assertEquals("SUBMITTED", service.submit(7L).status());
    }

    @Test
    void alreadySubmittedStaysInvalidStatusEvenAfterDeadline() {
        row.setStatus("SUBMITTED");
        row.setDeadlineOn(TODAY.minusDays(1));
        when(mapper.submit(7L)).thenReturn(0);

        var e = assertThrows(BusinessException.class, () -> service.submit(7L));

        assertEquals(ErrorCode.INVALID_STATUS, e.getErrorCode());
    }
}
