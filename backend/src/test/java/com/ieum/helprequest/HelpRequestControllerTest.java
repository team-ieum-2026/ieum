// 주인: BE-B
package com.ieum.helprequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import com.ieum.common.GlobalExceptionHandler;
import com.ieum.coordinator.CoordinatorHelpRequestController;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HelpRequestControllerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final HelpRequestService service = mock(HelpRequestService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new HelpRequestController(service),
                        new CoordinatorHelpRequestController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
    }

    @Test
    void creationMatchesExistingM4Contract() throws Exception {
        when(service.create(any())).thenReturn(new HelpRequestResult(4L, "OPEN"));
        var response = mvc.perform(post("/api/help-requests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":1,\"recommendationItemId\":93}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = json.readTree(response.getResponse().getContentAsString());
        assertEquals(1, body.size());
        assertEquals(2, body.path("data").size());
        assertEquals(4, body.path("data").path("helpRequestId").asLong());
        assertEquals("OPEN", body.path("data").path("status").asText());
        verify(service).create(new HelpRequestCreateRequest(1L, 93L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[]", "{", "{\"studentId\":1}",
            "{\"studentId\":0,\"recommendationItemId\":1}",
            "{\"studentId\":1,\"recommendationItemId\":null}",
            "{\"studentId\":1,\"recommendationItemId\":-1}"})
    void invalidCreateDoesNotCallService(String body) throws Exception {
        mvc.perform(post("/api/help-requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void partialPatchDistinguishesAbsentFieldFromExplicitNullAndKeepsNullResponseKeys() throws Exception {
        when(service.update(eq(4L), any())).thenReturn(new HelpRequestUpdateResult(4L, "OPEN", null, null));
        var response = mvc.perform(patch("/api/help-requests/4").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memo\":null}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = json.readTree(response.getResponse().getContentAsString()).path("data");
        assertEquals(4, data.size());
        assertTrue(data.get("contactMethod").isNull());
        assertTrue(data.get("memo").isNull());
        var request = ArgumentCaptor.forClass(HelpRequestUpdateRequest.class);
        verify(service).update(eq(4L), request.capture());
        assertFalse(request.getValue().hasContactMethod());
        assertTrue(request.getValue().hasMemo());
        assertNull(request.getValue().getMemo());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[]", "{\"contactMethod\":\"FAX\"}",
            "{\"contactMethod\":123}", "{\"contactMethod\":[]}", "{\"memo\":123}", "{\"memo\":{}}"})
    void invalidDetailsDoNotCallService(String body) throws Exception {
        mvc.perform(patch("/api/help-requests/4").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void memoUsesUtf8TextColumnLimit() throws Exception {
        String body = json.createObjectNode().put("memo", "가".repeat(21_846)).toString();
        mvc.perform(patch("/api/help-requests/4").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPEN", "CONTACTED"})
    void coordinatorStatusMatchesExistingC1Contract(String status) throws Exception {
        when(service.updateStatus(eq(4L), any())).thenReturn(new HelpRequestResult(4L, status));
        mvc.perform(patch("/api/coordinator/help-requests/4").contentType(MediaType.APPLICATION_JSON)
                        .content(json.createObjectNode().put("status", status).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(status));
        verify(service).updateStatus(4L, new HelpRequestStatusUpdateRequest(status));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{\"status\":null}", "{\"status\":\"\"}",
            "{\"status\":\"CLOSED\"}", "{\"status\":\"contacted\"}"})
    void invalidCoordinatorStatusDoesNotCallService(String body) throws Exception {
        mvc.perform(patch("/api/coordinator/help-requests/4").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/help-requests/404", "/api/coordinator/help-requests/404"})
    void missingHelpUsesExistingNotFoundEnvelope(String path) throws Exception {
        if (path.contains("coordinator")) when(service.updateStatus(eq(404L), any()))
                .thenThrow(new HelpRequestNotFoundException());
        else when(service.update(eq(404L), any())).thenThrow(new HelpRequestNotFoundException());
        mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                        .content(path.contains("coordinator") ? "{\"status\":\"CONTACTED\"}" : "{\"memo\":null}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void alreadyRespondedUsesExistingConflictEnvelope() throws Exception {
        when(service.create(any())).thenThrow(new BusinessException(ErrorCode.ALREADY_RESPONDED));
        mvc.perform(post("/api/help-requests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":1,\"recommendationItemId\":93}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ALREADY_RESPONDED"));
    }
}
