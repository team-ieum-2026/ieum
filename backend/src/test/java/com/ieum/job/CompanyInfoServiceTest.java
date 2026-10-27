// 주인: BE-A
package com.ieum.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.ai.AiClient;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanyInfoServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private AiClient aiClient;

    @Mock
    private CompanyInfoMapper companyInfoMapper;

    private CompanyInfoService service;

    @BeforeEach
    void setUp() {
        service = new CompanyInfoService(aiClient, companyInfoMapper, JSON);
    }

    @Test
    void 수집_결과를_요청한_기업명으로_저장한다() throws Exception {
        when(aiClient.collectCompanyInfo(any())).thenReturn(JSON.readTree("""
                { "companyInfo": { "companyName": "다른표기", "status": "PARTIAL",
                  "talentKeywords": ["책임"], "coreValues": ["협업"], "recentIssue": null,
                  "sources": [ { "item": "talentKeywords", "url": "https://example.com", "collectedAt": "2026-10-24T08:00:00+09:00" } ] } }
                """));
        when(companyInfoMapper.selectByCompanyName("(주)온다랩")).thenReturn(row(3L, "(주)온다랩", "PARTIAL"));

        CompanyCollectResult result = service.collect(List.of(" (주)온다랩 "));

        ArgumentCaptor<CompanyInfo> saved = ArgumentCaptor.forClass(CompanyInfo.class);
        verify(companyInfoMapper).upsert(saved.capture());
        assertEquals("(주)온다랩", saved.getValue().getCompanyName());
        assertEquals("PARTIAL", saved.getValue().getStatus());
        JsonNode info = saved.getValue().getInfo();
        assertEquals("책임", info.path("talentKeywords").path(0).asText());
        assertEquals("협업", info.path("coreValues").path(0).asText());
        assertEquals(true, info.get("recentIssue").isNull());
        assertEquals("https://example.com", info.path("sources").path(0).path("url").asText());
        assertNull(info.get("companyName"));

        verify(aiClient).collectCompanyInfo(argThat(req -> "(주)온다랩".equals(req.path("companyName").asText())));
        assertEquals(1, result.savedCount());
        assertEquals(3L, result.items().get(0).companyInfoId());
        assertEquals("SAVED", result.items().get(0).result());
    }

    @Test
    void NOT_FOUND도_빈_항목으로_저장한다() throws Exception {
        when(aiClient.collectCompanyInfo(any())).thenReturn(JSON.readTree("""
                { "companyName": "없는회사", "status": "NOT_FOUND" }
                """));
        when(companyInfoMapper.selectByCompanyName("없는회사")).thenReturn(row(4L, "없는회사", "NOT_FOUND"));

        CompanyCollectResult result = service.collect(List.of("없는회사"));

        ArgumentCaptor<CompanyInfo> saved = ArgumentCaptor.forClass(CompanyInfo.class);
        verify(companyInfoMapper).upsert(saved.capture());
        assertEquals("NOT_FOUND", saved.getValue().getStatus());
        assertEquals(0, saved.getValue().getInfo().path("talentKeywords").size());
        assertEquals(0, saved.getValue().getInfo().path("sources").size());
        assertEquals("NOT_FOUND", result.items().get(0).status());
    }

    @Test
    void 한_기업이_실패해도_나머지는_저장한다() throws Exception {
        when(aiClient.collectCompanyInfo(argThat(req -> req != null && "A".equals(req.path("companyName").asText()))))
                .thenThrow(new BusinessException(ErrorCode.AI_SERVER_ERROR));
        when(aiClient.collectCompanyInfo(argThat(req -> req != null && "B".equals(req.path("companyName").asText()))))
                .thenReturn(JSON.readTree("{ \"companyInfo\": { \"status\": \"FOUND\" } }"));
        when(companyInfoMapper.selectByCompanyName("B")).thenReturn(row(5L, "B", "FOUND"));

        CompanyCollectResult result = service.collect(List.of("A", "B", "B"));

        assertEquals(2, result.requestedCount());
        assertEquals(1, result.savedCount());
        assertEquals(1, result.failedCount());
        assertEquals("FAILED", result.items().get(0).result());
        assertEquals("SAVED", result.items().get(1).result());
        verify(companyInfoMapper, times(1)).upsert(any());
    }

    @Test
    void status가_틀리면_저장하지_않는다() throws Exception {
        when(aiClient.collectCompanyInfo(any())).thenReturn(JSON.readTree("{ \"companyInfo\": { \"status\": \"OK\" } }"));

        CompanyCollectResult result = service.collect(List.of("A"));

        assertEquals("FAILED", result.items().get(0).result());
        verify(companyInfoMapper, times(0)).upsert(any());
    }

    @Test
    void 기업명이_모두_비어_있으면_INVALID_REQUEST() {
        BusinessException e = assertThrows(BusinessException.class, () -> service.collect(Arrays.asList(" ", null)));

        assertEquals(ErrorCode.INVALID_REQUEST, e.getErrorCode());
        verifyNoInteractions(aiClient, companyInfoMapper);
    }

    private static CompanyInfo row(Long id, String name, String status) {
        CompanyInfo row = new CompanyInfo();
        row.setId(id);
        row.setCompanyName(name);
        row.setStatus(status);
        return row;
    }
}
