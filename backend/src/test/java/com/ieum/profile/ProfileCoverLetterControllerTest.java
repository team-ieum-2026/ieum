// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProfileCoverLetterControllerTest {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private ProfileItemService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ProfileItemService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProfileCoverLetterController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(JSON))
                .build();
    }

    @Test
    void preservesOriginalContentAndReturnsFullProfileWithNullKeysAndDates() throws Exception {
        String content = "모의 자기소개서입니다.\n\"문제 해결\" 경험을 정리했어요.\\프로젝트";
        OffsetDateTime updatedAt = OffsetDateTime.of(2026, 10, 26, 15, 0, 0, 0, ZoneOffset.ofHours(9));
        when(service.updateCoverLetter(7L, content)).thenReturn(profile(content, updatedAt));

        MvcResult result = mvc.perform(put("/api/students/7/cover-letter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.createObjectNode().put("content", content).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value(7))
                .andExpect(jsonPath("$.data.coverLetter.content").value(content))
                .andReturn();

        JsonNode body = JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertFalse(body.has("error"));
        JsonNode data = body.get("data");
        assertEquals(11, data.size(), "기존 M8 전체 프로필 응답을 유지해야 한다");
        assertEquals(updatedAt, OffsetDateTime.parse(data.get("profileUpdatedAt").asText()));
        assertEquals(updatedAt, OffsetDateTime.parse(data.get("coverLetter").get("updatedAt").asText()));
        assertTrue(data.get("basic").has("email"));
        assertTrue(data.get("basic").get("email").isNull());
        for (String field : List.of("education", "careers", "certificates", "skills", "projects")) {
            assertTrue(data.get(field).isArray(), field);
        }
        verify(service).updateCoverLetter(7L, content);
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void missingNullOrBlankContentReturns400WithoutWriting(String body) throws Exception {
        mvc.perform(put("/api/students/7/cover-letter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(service);
    }

    @Test
    void nonNumericStudentIdReturns400WithoutCallingService() throws Exception {
        mvc.perform(put("/api/students/not-a-number/cover-letter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"모의 자기소개서\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(service);
    }

    @Test
    void missingStudentReturns404WithCommonErrorEnvelope() throws Exception {
        when(service.updateCoverLetter(9999L, "모의 자기소개서"))
                .thenThrow(new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        mvc.perform(put("/api/students/9999/cover-letter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"모의 자기소개서\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("STUDENT_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private static Stream<String> invalidBodies() {
        return Stream.of("{}", "{\"content\":null}",
                JSON.createObjectNode().put("content", "").toString(),
                JSON.createObjectNode().put("content", " \n\t ").toString());
    }

    private static StudentProfileResponse profile(String content, OffsetDateTime updatedAt) {
        return new StudentProfileResponse(
                7L, updatedAt,
                new StudentProfileResponse.Basic(
                        "모의 수료생", null, null, null, null, null, LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(
                        2L, "모의 과정", "1기", "모의 캠퍼스", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 8, 29)),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                new StudentProfileResponse.CoverLetter(content, updatedAt),
                JSON.createObjectNode().set("roles", JSON.createArrayNode().add("모의 역할")));
    }
}
