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

class ProfileProjectControllerTest {

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
    void createsProjectUsingExistingEnvelopeAndKeepsOriginalsOutOfM8Response() throws Exception {
        JsonNode item = json.readTree("""
                {"title":"Mock Project","periodText":"6 weeks","teamSize":4,
                 "techStack":["Java"],"roleNote":"Original role record","readme":"Original README"}
                """);
        when(service.addItem(eq(7L), any(ProfileItemRequest.class))).thenReturn(profile(true));

        MvcResult result = mvc.perform(post("/api/students/7/profile/items")
                        .contentType(MediaType.APPLICATION_JSON).content(body("PROJECTS", item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects[0].itemId").value(77))
                .andExpect(jsonPath("$.data.projects[0].title").value("Mock Project"))
                .andExpect(jsonPath("$.data.projects[0].techStack[0]").value("Java"))
                .andExpect(jsonPath("$.data.projects[0].includeInApply").value(true))
                .andReturn();

        JsonNode data = responseData(result);
        assertEquals(11, data.size());
        assertEquals(7, data.get("projects").get(0).size());
        assertTrue(data.get("projects").get(0).get("portfolioFileName").isNull());
        assertTrue(data.get("coverLetter").isNull());
        verify(service).addItem(7L, new ProfileItemRequest("PROJECTS", item));
    }

    @Test
    void allowsM8ToExplicitlyPatchOriginalReadmeAndClearRoleRecord() throws Exception {
        JsonNode item = json.createObjectNode().put("readme", "Revised original README").putNull("roleNote");
        when(service.updateItem(eq(7L), eq(77L), any(ProfileItemPatchRequest.class))).thenReturn(profile(true));

        mvc.perform(patch("/api/students/7/profile/items/77")
                        .contentType(MediaType.APPLICATION_JSON).content(body("PROJECTS", item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects[0].itemId").value(77));

        verify(service).updateItem(7L, 77L, new ProfileItemPatchRequest("PROJECTS", item));
    }

    @Test
    void updatesPortfolioIncludeThroughPortfolioSection() throws Exception {
        JsonNode item = json.createObjectNode().put("include", false);
        when(service.updateItem(eq(7L), eq(77L), any(ProfileItemPatchRequest.class))).thenReturn(profile(true));

        mvc.perform(patch("/api/students/7/profile/items/77")
                        .contentType(MediaType.APPLICATION_JSON).content(body("PORTFOLIO", item)))
                .andExpect(status().isOk());

        verify(service).updateItem(7L, 77L, new ProfileItemPatchRequest("PORTFOLIO", item));
    }

    @Test
    void deletesM8ProjectAndReturnsUpdatedFullProfile() throws Exception {
        when(service.deleteItem(7L, 77L, "PROJECTS")).thenReturn(profile(false));

        MvcResult result = mvc.perform(delete("/api/students/7/profile/items/77").queryParam("section", "PROJECTS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects").isEmpty())
                .andReturn();

        assertEquals(11, responseData(result).size());
        verify(service).deleteItem(7L, 77L, "PROJECTS");
    }

    @ParameterizedTest
    @MethodSource("invalidProjectItems")
    void rejectsMalformedProjectValuesBeforeCallingService(String item) throws Exception {
        mvc.perform(post("/api/students/7/profile/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"section\":\"PROJECTS\",\"item\":" + item + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("invalidPortfolioItems")
    void requiresBooleanIncludeForPortfolioPatch(String item) throws Exception {
        mvc.perform(patch("/api/students/7/profile/items/77")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"section\":\"PORTFOLIO\",\"item\":" + item + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(service);
    }

    @Test
    void missingOrForeignProjectUsesExistingNotFoundEnvelope() throws Exception {
        when(service.deleteItem(7L, 9999L, "PROJECTS")).thenThrow(new ProfileItemNotFoundException());

        mvc.perform(delete("/api/students/7/profile/items/9999").queryParam("section", "PROJECTS"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private static Stream<String> invalidProjectItems() {
        return Stream.of(
                "{}",
                "{\"title\":\" \"}",
                "{\"title\":\"" + "A".repeat(151) + "\"}",
                "{\"title\":\"Mock Project\",\"periodText\":\"" + "A".repeat(51) + "\"}",
                "{\"title\":\"Mock Project\",\"teamSize\":0}",
                "{\"title\":\"Mock Project\",\"teamSize\":1.5}",
                "{\"title\":\"Mock Project\",\"techStack\":\"Java\"}",
                "{\"title\":\"Mock Project\",\"techStack\":[\" \" ]}",
                "{\"title\":\"Mock Project\",\"roleNote\":42}",
                "{\"title\":\"Mock Project\",\"roleNote\":\"" + "\uac00".repeat(21846) + "\"}",
                "{\"title\":\"Mock Project\",\"readme\":[]}");
    }

    private static Stream<String> invalidPortfolioItems() {
        return Stream.of("{}", "{\"include\":null}", "{\"include\":\"false\"}");
    }

    private String body(String section, JsonNode item) {
        var body = json.createObjectNode().put("section", section);
        body.set("item", item);
        return body.toString();
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private StudentProfileResponse profile(boolean includeProject) {
        var project = new StudentProfileResponse.Project(
                77L, "Mock Project", "6 weeks", 4, List.of("Java"), null, true);
        return new StudentProfileResponse(
                7L, null,
                new StudentProfileResponse.Basic("Mock Student", null, null, null, null, null, LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(
                        2L, "Mock Course", "1", "Mock Campus", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 8, 29)),
                List.of(), List.of(), List.of(), List.of(), includeProject ? List.of(project) : List.of(), null, null);
    }
}
