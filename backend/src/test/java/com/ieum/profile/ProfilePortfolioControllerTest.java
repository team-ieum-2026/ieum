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
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import com.ieum.common.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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

class ProfilePortfolioControllerTest {

    private final ObjectMapper json = JsonMapper.builder().addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
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
    void registersFilenameOnExistingProjectAndKeepsFullResponseShape() throws Exception {
        JsonNode item = json.createObjectNode().put("fileName", "모의 포트폴리오.pdf").put("include", false);
        when(service.updateItem(eq(7L), eq(77L), any(ProfileItemPatchRequest.class)))
                .thenReturn(profile("모의 포트폴리오.pdf", false));

        MvcResult result = mvc.perform(patch("/api/students/7/profile/items/77")
                        .contentType(MediaType.APPLICATION_JSON).content(body(item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects[0].itemId").value(77))
                .andExpect(jsonPath("$.data.projects[0].title").value("Mock Project"))
                .andExpect(jsonPath("$.data.projects[0].portfolioFileName").value("모의 포트폴리오.pdf"))
                .andExpect(jsonPath("$.data.projects[0].includeInApply").value(false))
                .andReturn();

        JsonNode data = responseData(result);
        assertEquals(11, data.size());
        assertEquals(7, data.get("projects").get(0).size());
        verify(service).updateItem(7L, 77L, new ProfileItemPatchRequest("PORTFOLIO", item));
    }

    @Test
    void acceptsFilenameOnlyPatch() throws Exception {
        JsonNode item = json.createObjectNode().put("fileName", "mock-replacement.pdf");
        when(service.updateItem(eq(7L), eq(77L), any(ProfileItemPatchRequest.class)))
                .thenReturn(profile("mock-replacement.pdf", true));

        mvc.perform(patch("/api/students/7/profile/items/77")
                        .contentType(MediaType.APPLICATION_JSON).content(body(item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects[0].includeInApply").value(true));

        verify(service).updateItem(7L, 77L, new ProfileItemPatchRequest("PORTFOLIO", item));
    }

    @Test
    void acceptsExplicitNullFilenameAndKeepsItsResponseKey() throws Exception {
        JsonNode item = json.createObjectNode().putNull("fileName");
        when(service.updateItem(eq(7L), eq(77L), any(ProfileItemPatchRequest.class)))
                .thenReturn(profile(null, true));

        MvcResult result = mvc.perform(patch("/api/students/7/profile/items/77")
                        .contentType(MediaType.APPLICATION_JSON).content(body(item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects[0].includeInApply").value(true))
                .andReturn();

        JsonNode project = responseData(result).get("projects").get(0);
        assertTrue(project.has("portfolioFileName"));
        assertTrue(project.get("portfolioFileName").isNull());
    }

    @Test
    void deletingPortfolioKeepsProjectAndReturnsEmptyAttachment() throws Exception {
        when(service.deleteItem(7L, 77L, "PORTFOLIO")).thenReturn(profile(null, false));

        MvcResult result = mvc.perform(delete("/api/students/7/profile/items/77")
                        .queryParam("section", "PORTFOLIO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects[0].itemId").value(77))
                .andExpect(jsonPath("$.data.projects[0].title").value("Mock Project"))
                .andExpect(jsonPath("$.data.projects[0].includeInApply").value(false))
                .andReturn();

        assertTrue(responseData(result).get("projects").get(0).get("portfolioFileName").isNull());
        verify(service).deleteItem(7L, 77L, "PORTFOLIO");
    }

    @ParameterizedTest
    @MethodSource("invalidPortfolioItems")
    void rejectsMalformedPortfolioFieldsBeforeCallingService(String item) throws Exception {
        mvc.perform(patch("/api/students/7/profile/items/77")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"section\":\"PORTFOLIO\",\"item\":" + item + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void missingOrForeignProjectUsesExistingNotFoundEnvelope() throws Exception {
        when(service.deleteItem(7L, 9999L, "PORTFOLIO")).thenThrow(new ProfileItemNotFoundException());

        mvc.perform(delete("/api/students/7/profile/items/9999").queryParam("section", "PORTFOLIO"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void missingStudentUsesStudentNotFoundEnvelope() throws Exception {
        when(service.updateItem(eq(9999L), eq(77L), any(ProfileItemPatchRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        mvc.perform(patch("/api/students/9999/profile/items/77")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(json.createObjectNode().put("fileName", "mock.pdf"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("STUDENT_NOT_FOUND"));
    }

    @Test
    void portfolioCannotCreateAnIndependentProjectRowThroughPost() throws Exception {
        mvc.perform(post("/api/students/7/profile/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(json.createObjectNode().put("fileName", "mock.pdf").put("include", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    private static Stream<String> invalidPortfolioItems() {
        return Stream.of("null", "[]", "{}", "{\"fileName\":\"\"}", "{\"fileName\":\" \"}",
                "{\"fileName\":\"" + "A".repeat(301) + "\"}", "{\"fileName\":42}",
                "{\"fileName\":[]}", "{\"include\":null}", "{\"include\":\"true\"}");
    }

    private String body(JsonNode item) {
        return json.createObjectNode().put("section", "PORTFOLIO").set("item", item).toString();
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private static StudentProfileResponse profile(String filename, boolean include) {
        var project = new StudentProfileResponse.Project(
                77L, "Mock Project", "6 weeks", 4, List.of("Java"), filename, include);
        return new StudentProfileResponse(7L, null,
                new StudentProfileResponse.Basic("Mock Student", null, null, null, null, null, LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(2L, "Mock Course", "1", "Mock Campus", null, null),
                List.of(), List.of(), List.of(), List.of(), List.of(project), null, null);
    }
}
