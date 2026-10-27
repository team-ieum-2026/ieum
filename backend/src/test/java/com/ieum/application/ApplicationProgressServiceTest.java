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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApplicationProgressServiceTest {

    private ApplicationMapper mapper;
    private ApplicationProgressService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ApplicationMapper.class);
        service = new ApplicationProgressService(mapper, new ObjectMapper());
    }

    @Test
    void unknownApplicationIsNotFound() {
        BusinessException e = assertThrows(BusinessException.class, () -> service.getProgress(99L));
        assertEquals(ErrorCode.APPLICATION_NOT_FOUND, e.getErrorCode());
    }

    @Test
    void generatingMatchesMock() {
        when(mapper.selectProgress(7L)).thenReturn(progress("GENERATING"));
        when(mapper.selectCompanyInfo("(주)온다랩"))
                .thenReturn(company("PARTIAL", "{\"talentKeywords\":[\"책임감\"],\"coreValues\":[\"성장\"],\"recentIssue\":null}"));

        ApplicationProgressResponse res = service.getProgress(7L);

        assertEquals("GENERATING", res.status());
        assertEquals(List.of("COMPANY_INFO", "COVER_LETTER", "RESUME"), res.steps().stream().map(s -> s.key()).toList());
        assertEquals(List.of("DONE", "RUNNING", "WAITING"), res.steps().stream().map(s -> s.state()).toList());
        assertEquals("인재상 · 핵심 가치 확인 · 최근 뉴스는 찾지 못함", res.steps().get(0).note());
        assertNull(res.steps().get(1).note());
    }

    @Test
    void failedMarksCoverLetterFailed() {
        when(mapper.selectProgress(7L)).thenReturn(progress("FAILED"));

        ApplicationProgressResponse res = service.getProgress(7L);

        assertEquals(List.of("DONE", "FAILED", "WAITING"), res.steps().stream().map(s -> s.state()).toList());
        assertEquals("공고 외 정보 확인 불가", res.steps().get(0).note());
    }

    @Test
    void draftAndLaterAreAllDone() {
        for (String status : List.of("DRAFT", "SUBMITTED", "APPROVED", "REJECTED", "DELIVERED")) {
            when(mapper.selectProgress(7L)).thenReturn(progress(status));
            assertEquals(List.of("DONE", "DONE", "DONE"),
                    service.getProgress(7L).steps().stream().map(s -> s.state()).toList(), status);
        }
    }

    @Test
    void companyNotes() {
        assertEquals("공고 외 정보 확인 불가", service.companyNote(null));
        assertEquals("공고 외 정보 확인 불가", service.companyNote(company("NOT_FOUND", null)));
        assertEquals("인재상 · 핵심 가치 · 최근 뉴스 확인", service.companyNote(company("FOUND",
                "{\"talentKeywords\":[\"a\"],\"coreValues\":[\"b\"],\"recentIssue\":\"c\"}")));
        assertEquals("최근 뉴스 확인 · 인재상 · 핵심 가치는 찾지 못함", service.companyNote(company("PARTIAL",
                "{\"talentKeywords\":[],\"coreValues\":[],\"recentIssue\":\"c\"}")));
        assertEquals("핵심 가치 · 최근 뉴스 확인 · 인재상은 찾지 못함", service.companyNote(company("PARTIAL",
                "{\"talentKeywords\":[],\"coreValues\":[\"b\"],\"recentIssue\":\"c\"}")));
        assertEquals("공고 외 정보 확인 불가", service.companyNote(company("PARTIAL", "{ broken")));
    }

    private static ApplicationRows.Progress progress(String status) {
        ApplicationRows.Progress row = new ApplicationRows.Progress();
        row.setApplicationId(7L);
        row.setStatus(status);
        row.setCompanyName("(주)온다랩");
        return row;
    }

    private static ApplicationRows.CompanyInfo company(String status, String info) {
        ApplicationRows.CompanyInfo row = new ApplicationRows.CompanyInfo();
        row.setId(3L);
        row.setCompanyName("(주)온다랩");
        row.setStatus(status);
        row.setInfo(info);
        return row;
    }
}
