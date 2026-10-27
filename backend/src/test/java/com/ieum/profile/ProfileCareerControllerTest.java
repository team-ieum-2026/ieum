// Owner: BE-B
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

class ProfileCareerControllerTest {

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
    void addsCareerAndReturnsFullProfileWithGeneratedIdIsoDateAndNullableKeys() throws Exception {
        JsonNode item = json.createObjectNode().put("companyName", "Mock Company")
                .putNull("position").put("startedOn", "2024-01-15").putNull("endedOn").putNull("description");
        when(service.addItem(eq(7L), any(ProfileItemRequest.class))).thenReturn(profile(true));

        MvcResult result = mvc.perform(post("/api/students/7/profile/items")
                        .contentType(MediaType.APPLICATION_JSON).content(body(item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value(7))
                .andExpect(jsonPath("$.data.careers[0].itemId").value(77))
                .andExpect(jsonPath("$.data.careers[0].source").value("USER"))
                .andExpect(jsonPath("$.data.careers[0].companyName").value("Mock Company"))
                .andExpect(jsonPath("$.data.careers[0].startedOn").value("2024-01-15"))
                .andReturn();

        JsonNode data = responseData(result);
        JsonNode career = data.get("careers").get(0);
        assertEquals(11, data.size());
        assertEquals(7, career.size());
        assertTrue(career.get("position").isNull());
        assertTrue(career.get("endedOn").isNull());
        assertTrue(career.get("description").isNull());
        assertTrue(data.get("profileUpdatedAt").asText().endsWith("+09:00"));
        verify(service).addItem(7L, new ProfileItemRequest("CAREER", item));
    }

    @Test
    void acceptsPartialCareerPatchAndExplicitNullWithoutChangingResponseEnvelope() throws Exception {
        JsonNode item = json.createObjectNode().putNull("endedOn");
        when(service.updateItem(eq(7L), eq(77L), any(ProfileItemPatchRequest.class))).thenReturn(profile(true));

        MvcResult result = mvc.perform(patch("/api/students/7/profile/items/77")
                        .contentType(MediaType.APPLICATION_JSON).content(body(item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.careers[0].companyName").value("Mock Company"))
                .andExpect(jsonPath("$.data.careers[0].startedOn").value("2024-01-15"))
                .andReturn();

        assertEquals(11, responseData(result).size());
        assertTrue(responseData(result).get("careers").get(0).get("endedOn").isNull());
        verify(service).updateItem(7L, 77L, new ProfileItemPatchRequest("CAREER", item));
    }

    @Test
    void deletesCareerAndReturnsFullProfileWithEmptyCareers() throws Exception {
        when(service.deleteItem(7L, 77L, "CAREER")).thenReturn(profile(false));

        MvcResult result = mvc.perform(delete("/api/students/7/profile/items/77")
                        .queryParam("section", "CAREER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.careers").isEmpty())
                .andReturn();

        assertEquals(11, responseData(result).size());
        verify(service).deleteItem(7L, 77L, "CAREER");
    }

    @ParameterizedTest
    @MethodSource("invalidCreateBodies")
    void rejectsInvalidCareerValuesAndReadOnlySectionBeforeService(String requestBody) throws Exception {
        mvc.perform(post("/api/students/7/profile/items")
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("invalidPatchItems")
    void rejectsInvalidExplicitPatchValuesBeforeService(String item) throws Exception {
        mvc.perform(patch("/api/students/7/profile/items/77")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"section\":\"CAREER\",\"item\":" + item + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(service);
    }

    @Test
    void missingOrForeignCareerReturnsNotFoundEnvelope() throws Exception {
        when(service.deleteItem(7L, 9999L, "CAREER")).thenThrow(new ProfileItemNotFoundException());

        mvc.perform(delete("/api/students/7/profile/items/9999").queryParam("section", "CAREER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private String body(JsonNode item) {
        var request = json.createObjectNode().put("section", "CAREER");
        request.set("item", item);
        return request.toString();
    }

    private static Stream<String> invalidCreateBodies() {
        String oversizedDescription = "\uac00".repeat(21846);
        return Stream.of(
                "{\"section\":\"CAREER\",\"item\":{}}",
                "{\"section\":\"CAREER\",\"item\":{\"companyName\":\" \"}}",
                "{\"section\":\"CAREER\",\"item\":{\"companyName\":\"" + "A".repeat(101) + "\"}}",
                "{\"section\":\"CAREER\",\"item\":{\"companyName\":\"Mock Company\",\"position\":42}}",
                "{\"section\":\"CAREER\",\"item\":{\"companyName\":\"Mock Company\",\"startedOn\":\"2026-02-30\"}}",
                "{\"section\":\"CAREER\",\"item\":{\"companyName\":\"Mock Company\",\"startedOn\":\"2026-1-01\"}}",
                "{\"section\":\"CAREER\",\"item\":{\"companyName\":\"Mock Company\",\"startedOn\":\"0999-01-01\"}}",
                "{\"section\":\"CAREER\",\"item\":{\"companyName\":\"Mock Company\",\"startedOn\":\"2025-02-01\",\"endedOn\":\"2025-01-31\"}}",
                "{\"section\":\"CAREER\",\"item\":{\"companyName\":\"Mock Company\",\"description\":\"" + oversizedDescription + "\"}}",
                "{\"section\":\"TRAINING\",\"item\":{\"companyName\":\"Mock Company\"}}");
    }

    private static Stream<String> invalidPatchItems() {
        return Stream.of("{\"companyName\":null}", "{\"endedOn\":\"2026-02-30\"}", "{\"description\":[]}");
    }

    private StudentProfileResponse profile(boolean includeCareer) {
        var career = new StudentProfileResponse.Career(
                77L, "USER", "Mock Company", null, LocalDate.of(2024, 1, 15), null, null);
        return new StudentProfileResponse(
                7L, OffsetDateTime.of(2026, 10, 26, 16, 0, 0, 0, ZoneOffset.ofHours(9)),
                new StudentProfileResponse.Basic("Mock Student", null, null, null, null, null, LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(
                        2L, "Mock Course", "1", "Mock Campus", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 8, 29)),
                List.of(), includeCareer ? List.of(career) : List.of(), List.of(), List.of(), List.of(),
                null, json.createObjectNode());
    }
}
