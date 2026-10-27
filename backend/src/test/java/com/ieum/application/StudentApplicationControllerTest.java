// 주인: BE-A
package com.ieum.application;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StudentApplicationControllerTest {

    private final ObjectMapper json = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private StudentApplicationService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(StudentApplicationService.class);
        mvc = MockMvcBuilders.standaloneSetup(new StudentApplicationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .build();
    }

    @Test
    void returnsMockShapeWithNullKeys() throws Exception {
        when(service.list(1L)).thenReturn(new StudentApplicationsResponse(
                List.of(
                        new StudentApplicationsResponse.Application(7L, "(주)온다랩", "백엔드 인턴 (AI 서비스팀)", "SUBMITTED",
                                OffsetDateTime.parse("2026-10-24T10:12:00+09:00"), 1),
                        new StudentApplicationsResponse.Application(8L, "(주)온다랩", "백엔드 인턴", "DRAFT", null, 0)),
                List.of(new StudentApplicationsResponse.OtherItem(93L, "(주)서울에이아이랩", "AI 서비스 기획 보조", "NONE", null))));

        MvcResult result = mvc.perform(get("/api/students/1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applications[0].applicationId").value(7))
                .andExpect(jsonPath("$.data.applications[0].submittedAt").value("2026-10-24T10:12:00+09:00"))
                .andExpect(jsonPath("$.data.applications[0].userEditedCount").value(1))
                .andExpect(jsonPath("$.data.otherItems[0].response").value("NONE"))
                .andReturn();

        // 값이 없어도 키는 빼지 않고 null (docs/API.md 1장)
        JsonNode data = json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
        assertTrue(data.get("applications").get(1).has("submittedAt"));
        assertTrue(data.get("applications").get(1).get("submittedAt").isNull());
        assertTrue(data.get("otherItems").get(0).has("passReason"));
        assertTrue(data.get("otherItems").get(0).get("passReason").isNull());
    }

    @Test
    void unknownStudentReturns404() throws Exception {
        when(service.list(99L)).thenThrow(new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        mvc.perform(get("/api/students/99/applications"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("STUDENT_NOT_FOUND"));
    }
}
