// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

class ProfileItemMutationControllerTest {

    private final ObjectMapper json = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private ProfileItemService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ProfileItemService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProfileItemController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .build();
    }

    @Test
    void patchPreservesPartialRequestAndReturnsFullProfile() throws Exception {
        JsonNode item = json.createObjectNode().putNull("issuer");
        when(service.updateItem(eq(7L), eq(31L), any(ProfileItemPatchRequest.class)))
                .thenReturn(profile(true));

        MvcResult result = mvc.perform(patch("/api/students/7/profile/items/31")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"section":"CERTIFICATES","item":{"issuer":null}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value(7))
                .andExpect(jsonPath("$.data.certificates[0].itemId").value(31))
                .andExpect(jsonPath("$.data.certificates[0].source").value("USER"))
                .andReturn();

        JsonNode data = json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
        assertEquals(11, data.size(), "M8 전체 프로필 형식을 유지해야 한다");
        assertTrue(data.get("certificates").get(0).has("issuer"));
        assertTrue(data.get("certificates").get(0).get("issuer").isNull());
        assertTrue(data.get("profileUpdatedAt").asText().endsWith("+09:00"));
        verify(service).updateItem(7L, 31L, new ProfileItemPatchRequest("CERTIFICATES", item));
    }

    @Test
    void deletePassesSectionQueryAndReturnsFullProfile() throws Exception {
        when(service.deleteItem(7L, 31L, "CERTIFICATES")).thenReturn(profile(false));

        MvcResult result = mvc.perform(delete("/api/students/7/profile/items/31")
                        .queryParam("section", "CERTIFICATES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value(7))
                .andExpect(jsonPath("$.data.certificates").isEmpty())
                .andReturn();

        JsonNode data = json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
        assertEquals(11, data.size());
        assertTrue(data.has("coverLetter"));
        verify(service).deleteItem(7L, 31L, "CERTIFICATES");
    }

    @ParameterizedTest
    @MethodSource("invalidPatchBodies")
    void invalidPatchEnvelopeReturns400BeforeCallingService(String body) throws Exception {
        mvc.perform(patch("/api/students/7/profile/items/31")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(service);
    }

    @Test
    void deleteWithoutSectionReturns400BeforeCallingService() throws Exception {
        mvc.perform(delete("/api/students/7/profile/items/31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(service);
    }

    @Test
    void nonNumericItemIdReturns400BeforeCallingService() throws Exception {
        mvc.perform(delete("/api/students/7/profile/items/not-a-number")
                        .queryParam("section", "CERTIFICATES"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(service);
    }

    @Test
    void patchOfMissingOrForeignItemReturnsExistingNotFoundEnvelope() throws Exception {
        when(service.updateItem(eq(7L), eq(9999L), any(ProfileItemPatchRequest.class)))
                .thenThrow(new ProfileItemNotFoundException());

        mvc.perform(patch("/api/students/7/profile/items/9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"section":"CERTIFICATES","item":{"name":"모의 수정 자격증"}}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").isString())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void deleteOfMissingOrForeignItemReturnsExistingNotFoundEnvelope() throws Exception {
        when(service.deleteItem(7L, 9999L, "CERTIFICATES"))
                .thenThrow(new ProfileItemNotFoundException());

        mvc.perform(delete("/api/students/7/profile/items/9999")
                        .queryParam("section", "CERTIFICATES"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void missingStudentStillUsesStudentNotFoundCode() throws Exception {
        when(service.deleteItem(9999L, 31L, "CERTIFICATES"))
                .thenThrow(new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        mvc.perform(delete("/api/students/9999/profile/items/31")
                        .queryParam("section", "CERTIFICATES"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("STUDENT_NOT_FOUND"));
    }

    private static Stream<String> invalidPatchBodies() {
        return Stream.of(
                """
                {"section":"EDUCATION","item":{"name":"모의 자격증"}}
                """,
                """
                {"item":{"name":"모의 자격증"}}
                """,
                """
                {"section":"CERTIFICATES"}
                """);
    }

    private StudentProfileResponse profile(boolean includeCertificate) {
        var certificate = new StudentProfileResponse.Certificate(
                31L, "USER", "모의 수정 자격증", null, LocalDate.of(2026, 9, 1));
        return new StudentProfileResponse(
                7L, OffsetDateTime.of(2026, 10, 26, 14, 0, 0, 0, ZoneOffset.ofHours(9)),
                new StudentProfileResponse.Basic(
                        "모의 수료생", null, null, null, null, null, LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(
                        2L, "모의 과정", "1기", "모의 캠퍼스", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 8, 29)),
                List.of(), List.of(), includeCertificate ? List.of(certificate) : List.of(),
                List.of(), List.of(), null, json.createObjectNode());
    }
}
