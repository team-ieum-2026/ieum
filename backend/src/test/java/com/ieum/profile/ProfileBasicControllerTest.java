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

class ProfileBasicControllerTest {

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
    void patchesBasicAtStudentIdAndKeepsExistingResponseFieldsAndNullKeys() throws Exception {
        JsonNode item = json.createObjectNode().put("name", "모의 수정 수료생")
                .put("email", "mock@example.com").putNull("githubUrl")
                .put("desiredJob", "모의 백엔드 개발").put("preferredRegion", "모의 서울");
        when(service.updateItem(eq(7L), eq(7L), any(ProfileItemPatchRequest.class))).thenReturn(profile());

        MvcResult result = mvc.perform(patch("/api/students/7/profile/items/7")
                        .contentType(MediaType.APPLICATION_JSON).content(body(item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value(7))
                .andExpect(jsonPath("$.data.basic.name").value("모의 수정 수료생"))
                .andExpect(jsonPath("$.data.basic.desiredJob").value("모의 백엔드 개발"))
                .andExpect(jsonPath("$.data.basic.preferredRegion").value("모의 서울"))
                .andReturn();

        JsonNode data = json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
        assertEquals(11, data.size());
        assertEquals(7, data.get("basic").size());
        assertTrue(data.get("basic").has("githubUrl"));
        assertTrue(data.get("basic").get("githubUrl").isNull());
        verify(service).updateItem(7L, 7L, new ProfileItemPatchRequest("BASIC", item));
    }

    @ParameterizedTest
    @MethodSource("invalidBasicItems")
    void rejectsMalformedBasicValuesBeforeCallingService(String item) throws Exception {
        mvc.perform(patch("/api/students/7/profile/items/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"section\":\"BASIC\",\"item\":" + item + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsProtectedOnlyPatchWithoutCallingService() throws Exception {
        mvc.perform(patch("/api/students/7/profile/items/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(json.createObjectNode().put("completedOn", "2026-01-01")
                                .put("militaryStatus", "SERVED").put("lastResponseAt", "2026-01-01T00:00:00"))))
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
                        .content(body(json.createObjectNode().put("desiredJob", "모의 백엔드 개발"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void missingStudentUsesStudentNotFoundEnvelope() throws Exception {
        when(service.updateItem(eq(9999L), eq(9999L), any(ProfileItemPatchRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.STUDENT_NOT_FOUND));
        mvc.perform(patch("/api/students/9999/profile/items/9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(json.createObjectNode().put("desiredJob", "모의 백엔드 개발"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("STUDENT_NOT_FOUND"));
    }

    private static Stream<String> invalidBasicItems() {
        return Stream.of("null", "[]", "{}", "{\"name\":null}", "{\"name\":\" \"}",
                "{\"name\":\"" + "A".repeat(51) + "\"}",
                "{\"email\":\"" + "A".repeat(101) + "\"}",
                "{\"githubUrl\":\"" + "A".repeat(201) + "\"}",
                "{\"desiredJob\":\"" + "A".repeat(101) + "\"}",
                "{\"preferredRegion\":\"" + "A".repeat(101) + "\"}",
                "{\"desiredJob\":42}");
    }

    private String body(JsonNode item) {
        return json.createObjectNode().put("section", "BASIC").set("item", item).toString();
    }

    private StudentProfileResponse profile() {
        return new StudentProfileResponse(7L, null,
                new StudentProfileResponse.Basic("모의 수정 수료생", "mock@example.com", null,
                        "모의 백엔드 개발", "모의 서울", null, LocalDate.of(2026, 8, 29)),
                new StudentProfileResponse.Course(2L, "Mock Course", "1", "Mock Campus", null, null),
                List.of(), List.of(), List.of(), List.of(), List.of(), null, null);
    }
}
