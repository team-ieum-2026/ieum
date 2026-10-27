// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

class ProfileItemControllerTest {

    private final ObjectMapper json = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private ProfileItemService profileItemService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        profileItemService = mock(ProfileItemService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProfileItemController(profileItemService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .build();
    }

    @Test
    void addsCertificateAndReturnsFullProfileInDataEnvelope() throws Exception {
        OffsetDateTime updatedAt = OffsetDateTime.of(2026, 10, 26, 14, 0, 0, 0, ZoneOffset.ofHours(9));
        var certificate = new StudentProfileResponse.Certificate(
                31L, "USER", "모의 자격증", null, LocalDate.of(2026, 9, 1));
        var profile = new StudentProfileResponse(
                7L, updatedAt,
                new StudentProfileResponse.Basic(
                        "모의 수료생", null, null, null, null, null, LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(
                        2L, "모의 과정", "1기", "모의 캠퍼스", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 8, 29)),
                List.of(), List.of(), List.of(certificate), List.of(), List.of(), null,
                json.createObjectNode().set("certificates", json.createArrayNode().add("모의 자격증")));
        when(profileItemService.addItem(eq(7L), any(ProfileItemRequest.class))).thenReturn(profile);

        MvcResult result = mvc.perform(post("/api/students/7/profile/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"section":"CERTIFICATES","item":{
                                  "name":"모의 자격증","issuer":null,"acquiredOn":"2026-09-01"
                                }}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value(7))
                .andExpect(jsonPath("$.data.certificates[0].itemId").value(31))
                .andExpect(jsonPath("$.data.certificates[0].source").value("USER"))
                .andExpect(jsonPath("$.data.certificates[0].acquiredOn").value("2026-09-01"))
                .andExpect(jsonPath("$.data.competencyProfile.certificates[0]").value("모의 자격증"))
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertFalse(body.has("error"));
        JsonNode data = body.get("data");
        assertEquals(updatedAt, OffsetDateTime.parse(data.get("profileUpdatedAt").asText()));
        assertTrue(data.get("certificates").get(0).has("issuer"));
        assertTrue(data.get("certificates").get(0).get("issuer").isNull());
        assertTrue(data.has("coverLetter"));
        assertTrue(data.get("coverLetter").isNull());
        for (String field : List.of("education", "careers", "skills", "projects")) {
            assertTrue(data.get(field).isArray(), field);
        }
        verify(profileItemService).addItem(7L, new ProfileItemRequest(
                "CERTIFICATES", json.valueToTree(new ProfileItemRequest.CertificateItem(
                        "모의 자격증", null, LocalDate.of(2026, 9, 1)))));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void invalidRequestReturns400WithoutCallingService(String body) throws Exception {
        mvc.perform(post("/api/students/7/profile/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(profileItemService);
    }

    @Test
    void missingStudentReturns404WithCommonErrorEnvelope() throws Exception {
        when(profileItemService.addItem(eq(9999L), any(ProfileItemRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        mvc.perform(post("/api/students/9999/profile/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"section":"CERTIFICATES","item":{"name":"모의 자격증","issuer":null,"acquiredOn":null}}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("STUDENT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.STUDENT_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private static Stream<String> invalidBodies() {
        return Stream.of(
                """
                {"section":"CERTIFICATES","item":{"name":"  ","issuer":null,"acquiredOn":null}}
                """,
                """
                {"section":"CERTIFICATES","item":{"name":null,"issuer":null,"acquiredOn":null}}
                """,
                """
                {"section":"CERTIFICATES","item":{"name":"모의 자격증","issuer":null,"acquiredOn":"bad-date"}}
                """,
                """
                {"section":"CERTIFICATES","item":{"name":"모의 자격증","issuer":null,"acquiredOn":"+10000-01-01"}}
                """,
                """
                {"section":"CERTIFICATES","item":{"name":"모의 자격증","issuer":null,"acquiredOn":"0001-01-01"}}
                """,
                "{\"section\":\"CERTIFICATES\",\"item\":{\"name\":\"" + "가".repeat(101) + "\",\"issuer\":null,\"acquiredOn\":null}}",
                "{\"section\":\"CERTIFICATES\",\"item\":{\"name\":\"모의 자격증\",\"issuer\":\"" + "가".repeat(101) + "\",\"acquiredOn\":null}}",
                """
                {"section":"EDUCATION","item":{"name":"모의 자격증","issuer":null,"acquiredOn":null}}
                """,
                """
                {"section":"CERTIFICATES","item":null}
                """,
                """
                {"section":"CERTIFICATES"}
                """);
    }
}
