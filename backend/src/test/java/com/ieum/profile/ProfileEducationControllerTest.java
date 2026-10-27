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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

class ProfileEducationControllerTest {

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
    void addsEducationAndReturnsFullProfileWithGeneratedIdAndDate() throws Exception {
        JsonNode item = validItem();
        when(service.addItem(eq(7L), any(ProfileItemRequest.class))).thenReturn(profile(true));

        MvcResult result = mvc.perform(post("/api/students/7/profile/items")
                        .contentType(MediaType.APPLICATION_JSON).content(body(item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value(7))
                .andExpect(jsonPath("$.data.education[0].itemId").value(55))
                .andExpect(jsonPath("$.data.education[0].source").value("USER"))
                .andExpect(jsonPath("$.data.education[0].schoolName").value("모의대학교"))
                .andExpect(jsonPath("$.data.education[0].graduatedOn").value("2026-02-28"))
                .andReturn();

        JsonNode data = responseData(result);
        assertEquals(11, data.size(), "기존 M8 전체 응답을 유지해야 한다");
        assertTrue(data.get("education").get(0).has("major"));
        assertTrue(data.get("education").get(0).get("major").isNull());
        assertTrue(data.get("profileUpdatedAt").asText().endsWith("+09:00"));
        verify(service).addItem(7L, new ProfileItemRequest("EDUCATION", item));
    }

    @Test
    void patchesOnlySentEducationFieldAndReturnsNullableKey() throws Exception {
        JsonNode item = json.createObjectNode().putNull("major");
        when(service.updateItem(eq(7L), eq(55L), any(ProfileItemPatchRequest.class))).thenReturn(profile(true));

        MvcResult result = mvc.perform(patch("/api/students/7/profile/items/55")
                        .contentType(MediaType.APPLICATION_JSON).content(body(item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.education[0].schoolName").value("모의대학교"))
                .andReturn();

        assertTrue(responseData(result).get("education").get(0).get("major").isNull());
        verify(service).updateItem(7L, 55L, new ProfileItemPatchRequest("EDUCATION", item));
    }

    @Test
    void deletesEducationAndReturnsProfileWithEmptyEducationList() throws Exception {
        when(service.deleteItem(7L, 55L, "EDUCATION")).thenReturn(profile(false));

        MvcResult result = mvc.perform(delete("/api/students/7/profile/items/55")
                        .queryParam("section", "EDUCATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.education").isEmpty())
                .andReturn();

        assertEquals(11, responseData(result).size());
        verify(service).deleteItem(7L, 55L, "EDUCATION");
    }

    @ParameterizedTest
    @MethodSource("invalidCreateBodies")
    void invalidSchoolEnumDateOrReadOnlySectionReturns400BeforeService(String requestBody) throws Exception {
        mvc.perform(post("/api/students/7/profile/items")
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(service);
    }

    @Test
    void invalidPatchedDegreeReturns400BeforeService() throws Exception {
        mvc.perform(patch("/api/students/7/profile/items/55")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(json.createObjectNode().put("degree", "INVALID"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(service);
    }

    @Test
    void missingOrForeignEducationReturnsExistingNotFoundEnvelope() throws Exception {
        when(service.deleteItem(7L, 9999L, "EDUCATION")).thenThrow(new ProfileItemNotFoundException());

        mvc.perform(delete("/api/students/7/profile/items/9999").queryParam("section", "EDUCATION"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private String body(JsonNode item) {
        var request = json.createObjectNode().put("section", "EDUCATION");
        request.set("item", item);
        return request.toString();
    }

    private JsonNode validItem() {
        return json.createObjectNode().put("schoolName", "모의대학교").putNull("major")
                .put("degree", "BACHELOR").put("status", "GRADUATED").put("graduatedOn", "2026-02-28");
    }

    private static Stream<String> invalidCreateBodies() {
        return Stream.of(
                "{\"section\":\"EDUCATION\",\"item\":{\"schoolName\":\" \"}}",
                "{\"section\":\"EDUCATION\",\"item\":{\"schoolName\":\"모의대학교\",\"degree\":\"INVALID\"}}",
                "{\"section\":\"EDUCATION\",\"item\":{\"schoolName\":\"모의대학교\",\"status\":\"graduated\"}}",
                "{\"section\":\"EDUCATION\",\"item\":{\"schoolName\":\"모의대학교\",\"graduatedOn\":\"2026-02-30\"}}",
                "{\"section\":\"EDUCATION\",\"item\":{\"schoolName\":\"" + "가".repeat(101) + "\"}}",
                "{\"section\":\"TRAINING\",\"item\":{\"schoolName\":\"모의대학교\"}}");
    }

    private StudentProfileResponse profile(boolean includeEducation) {
        var education = new StudentProfileResponse.Education(
                55L, "USER", "모의대학교", null, "BACHELOR", "GRADUATED", LocalDate.of(2026, 2, 28));
        return new StudentProfileResponse(
                7L, OffsetDateTime.of(2026, 10, 26, 16, 0, 0, 0, ZoneOffset.ofHours(9)),
                new StudentProfileResponse.Basic("모의 수료생", null, null, null, null, null, LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(
                        2L, "모의 과정", "1기", "모의 캠퍼스", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 8, 29)),
                includeEducation ? List.of(education) : List.of(), List.of(), List.of(), List.of(), List.of(),
                null, json.createObjectNode());
    }
}
