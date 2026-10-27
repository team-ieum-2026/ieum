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

class ProfileSkillsControllerTest {

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

    @ParameterizedTest
    @MethodSource("validTags")
    void replacesOrClearsSkillsUsingExistingFullProfileResponse(List<String> tags) throws Exception {
        JsonNode item = json.createObjectNode().set("tags", json.valueToTree(tags));
        when(service.updateItem(eq(7L), eq(7L), any(ProfileItemPatchRequest.class))).thenReturn(profile(tags));

        MvcResult result = mvc.perform(patch("/api/students/7/profile/items/7")
                        .contentType(MediaType.APPLICATION_JSON).content(body(item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value(7))
                .andReturn();

        JsonNode data = json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
        assertEquals(11, data.size());
        assertEquals(7, data.get("basic").size());
        assertTrue(data.get("skills").isArray());
        assertEquals(json.valueToTree(tags), data.get("skills"));
        assertTrue(data.get("coverLetter").isNull());
        verify(service).updateItem(7L, 7L, new ProfileItemPatchRequest("SKILLS", item));
    }

    @ParameterizedTest
    @MethodSource("invalidSkillsItems")
    void rejectsMissingTagsAndMalformedValuesBeforeCallingService(String item) throws Exception {
        mvc.perform(patch("/api/students/7/profile/items/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"section\":\"SKILLS\",\"item\":" + item + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void wrongSingletonIdUsesExistingNotFoundEnvelope() throws Exception {
        when(service.updateItem(eq(7L), eq(8L), any(ProfileItemPatchRequest.class)))
                .thenThrow(new ProfileItemNotFoundException());

        mvc.perform(patch("/api/students/7/profile/items/8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(json.createObjectNode().set("tags", json.createArrayNode().add("Java")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void missingStudentUsesStudentNotFoundEnvelope() throws Exception {
        when(service.updateItem(eq(9999L), eq(9999L), any(ProfileItemPatchRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        mvc.perform(patch("/api/students/9999/profile/items/9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(json.createObjectNode().set("tags", json.createArrayNode().add("Java")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("STUDENT_NOT_FOUND"));
    }

    private static Stream<List<String>> validTags() {
        return Stream.of(List.of("Java", " Java ", "Java", "Spring Boot"), List.of());
    }

    private static Stream<String> invalidSkillsItems() {
        return Stream.of("null", "[]", "{}", "{\"tags\":null}", "{\"tags\":\"Java\"}",
                "{\"tags\":42}", "{\"tags\":{}}", "{\"tags\":[null]}", "{\"tags\":[42]}",
                "{\"tags\":[\" \" ]}", "{\"summary\":[],\"source\":\"AI\"}");
    }

    private String body(JsonNode item) {
        return json.createObjectNode().put("section", "SKILLS").set("item", item).toString();
    }

    private static StudentProfileResponse profile(List<String> tags) {
        return new StudentProfileResponse(7L, null,
                new StudentProfileResponse.Basic("Mock Student", "mock@example.com", null,
                        "Mock Backend", "Mock Region", null, LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(2L, "Mock Course", "1", "Mock Campus", null, null),
                List.of(), List.of(), List.of(), tags, List.of(), null, null);
    }
}
