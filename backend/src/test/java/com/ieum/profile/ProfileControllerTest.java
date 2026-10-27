// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import com.ieum.common.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProfileControllerTest {

    private final ObjectMapper json = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private ProfileService profileService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        profileService = mock(ProfileService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProfileController(profileService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .build();
    }

    @Test
    void returnsDataEnvelopeWithNullKeysAndDateStrings() throws Exception {
        when(profileService.getProfile(7L)).thenReturn(profile(null, null, null));

        MvcResult result = mvc.perform(get("/api/students/7/profile"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.studentId").value(7))
                .andExpect(jsonPath("$.data.basic.completedOn").value("2026-08-29"))
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertFalse(body.has("error"));
        JsonNode data = body.get("data");
        for (String field : List.of("profileUpdatedAt", "coverLetter", "competencyProfile")) {
            assertTrue(data.has(field), field + " 키를 생략하면 안 된다");
            assertTrue(data.get(field).isNull(), field + " 값은 null이어야 한다");
        }
        assertTrue(data.get("basic").has("email"));
        assertTrue(data.get("basic").get("email").isNull());
        for (String field : List.of("education", "careers", "certificates", "skills", "projects")) {
            assertTrue(data.get(field).isArray());
            assertEquals(0, data.get(field).size());
        }
    }

    @Test
    void serializesTimestampsAsIsoStringsWithSeoulOffset() throws Exception {
        OffsetDateTime extractedAt = OffsetDateTime.of(2026, 10, 26, 11, 20, 0, 0, ZoneOffset.ofHours(9));
        OffsetDateTime coverLetterUpdatedAt = extractedAt.minusDays(1);
        JsonNode competency = json.createObjectNode().set("roles", json.createArrayNode().add("백엔드 개발"));
        when(profileService.getProfile(7L)).thenReturn(profile(
                extractedAt, new StudentProfileResponse.CoverLetter("모의 자기소개서", coverLetterUpdatedAt), competency));

        MvcResult result = mvc.perform(get("/api/students/7/profile"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
        assertTrue(data.get("profileUpdatedAt").asText().endsWith("+09:00"));
        assertEquals(extractedAt, OffsetDateTime.parse(data.get("profileUpdatedAt").asText()));
        assertEquals(coverLetterUpdatedAt, OffsetDateTime.parse(data.get("coverLetter").get("updatedAt").asText()));
        assertEquals(competency, data.get("competencyProfile"));
    }

    @Test
    void missingStudentReturns404WithCommonErrorEnvelope() throws Exception {
        when(profileService.getProfile(9999L)).thenThrow(new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        mvc.perform(get("/api/students/9999/profile"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("STUDENT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.STUDENT_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void nonNumericStudentIdReturns400WithoutCallingService() throws Exception {
        mvc.perform(get("/api/students/not-a-number/profile"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(profileService);
    }

    private static StudentProfileResponse profile(
            OffsetDateTime extractedAt, StudentProfileResponse.CoverLetter coverLetter, JsonNode competency) {
        return new StudentProfileResponse(
                7L,
                extractedAt,
                new StudentProfileResponse.Basic(
                        "모의 수료생", null, null, null, null, null, LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(
                        2L, "모의 과정", "1기", "모의 캠퍼스", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 8, 29)),
                List.of(), List.of(), List.of(), List.of(), List.of(), coverLetter, competency);
    }
}
