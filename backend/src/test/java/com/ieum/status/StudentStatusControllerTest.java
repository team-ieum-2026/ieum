// 주인: BE-B
package com.ieum.status;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ieum.common.GlobalExceptionHandler;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StudentStatusControllerTest {
    private final StatusService service = mock(StatusService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var json = JsonMapper.builder().addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        mvc = MockMvcBuilders.standaloneSetup(new StudentStatusController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMPLOYED", "SEEKING", "RESTING"})
    void selectsOnlyADeclaredCurrentIntentAndKeepsNullKeys(String status) throws Exception {
        when(service.selectStatus(eq(7L), any())).thenReturn(new StudentStatusResult(7L, status, null,
                OffsetDateTime.parse("2026-11-02T12:00:00+09:00")));
        mvc.perform(put("/api/students/7/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(status))
                .andExpect(jsonPath("$.data.studentId").value(7))
                .andExpect(jsonPath("$.data.subStatus").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-11-02T12:00:00+09:00"));
        verify(service).selectStatus(7L, new StudentStatusRequest(status));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{\"status\":null}", "{\"status\":\"NEED_HELP\"}",
            "{\"status\":\"CHECK_REQUIRED\"}", "{\"status\":\"resting\"}", "{\"status\":123}"})
    void rejectsInvalidChoicesBeforeTheService(String body) throws Exception {
        mvc.perform(put("/api/students/7/status").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void readsTheCurrentStatusWithoutSelectingANewOne() throws Exception {
        when(service.getStatus(7L)).thenReturn(new StudentStatusResult(7L, "RESTING", null, null));
        mvc.perform(get("/api/students/7/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESTING"));
        verify(service).getStatus(7L);
        verifyNoMoreInteractions(service);
    }
}
