// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ieum.common.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProfileAdminControllerTest {
    private static final OffsetDateTime EXTRACTED_AT =
            OffsetDateTime.of(2026, 10, 31, 12, 30, 0, 0, ZoneOffset.ofHours(9));

    private final ObjectMapper json = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private ProfileBatchExtractionService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ProfileBatchExtractionService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProfileAdminController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .build();
    }

    @Test
    void postWithoutAuthenticationOrBodyReturnsCountsAndFailedIdsInDataEnvelope() throws Exception {
        when(service.extractMissing()).thenReturn(
                new ProfileBatchExtractionResult(20, 19, List.of(7L), EXTRACTED_AT));

        var response = mvc.perform(post("/api/admin/profiles/extract"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.targetCount").value(20))
                .andExpect(jsonPath("$.data.extractedCount").value(19))
                .andExpect(jsonPath("$.data.failedStudentIds[0]").value(7))
                .andExpect(jsonPath("$.data.extractedAt").value("2026-10-31T12:30:00+09:00"))
                .andReturn();

        JsonNode body = json.readTree(response.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertFalse(body.has("error"));
        assertEquals(4, body.get("data").size());
        assertEquals(EXTRACTED_AT, OffsetDateTime.parse(body.path("data").path("extractedAt").asText()));
        verify(service).extractMissing();
    }

    @Test
    void noMissingStudentsStillReturnsEmptyFailureArrayAndTimestamp() throws Exception {
        when(service.extractMissing()).thenReturn(
                new ProfileBatchExtractionResult(0, 0, List.of(), EXTRACTED_AT));

        mvc.perform(post("/api/admin/profiles/extract"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetCount").value(0))
                .andExpect(jsonPath("$.data.extractedCount").value(0))
                .andExpect(jsonPath("$.data.failedStudentIds").isArray())
                .andExpect(jsonPath("$.data.failedStudentIds").isEmpty())
                .andExpect(jsonPath("$.data.extractedAt").value("2026-10-31T12:30:00+09:00"));
    }
}
