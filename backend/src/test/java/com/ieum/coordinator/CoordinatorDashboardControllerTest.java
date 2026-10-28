// 주인: BE-B
package com.ieum.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ieum.common.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CoordinatorDashboardControllerTest {

    private static final OffsetDateTime AS_OF = OffsetDateTime.of(2026, 10, 28, 12, 0, 0, 0,
            ZoneOffset.ofHours(9));
    private static final Set<String> DASHBOARD_KEYS = Set.of("asOf", "totalStudents", "helpRequests",
            "statusSummary", "reviewQueue", "callList", "notesToCheck");
    private static final ObjectMapper JSON = JsonMapper.builder().addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    private CoordinatorDashboardService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(CoordinatorDashboardService.class);
        mvc = MockMvcBuilders.standaloneSetup(new CoordinatorDashboardController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(JSON))
                .build();
    }

    @Test
    void emptyDashboardKeepsAllSevenKeysEmptyListsAndEveryZeroCounter() throws Exception {
        when(service.getDashboard()).thenReturn(new CoordinatorDashboardResponse(
                AS_OF, 0L, List.of(), summary(0, 0, 0, 0, 0, 0, 0), List.of(), List.of(), List.of()));

        JsonNode body = body(mvc.perform(get("/api/coordinator/dashboard"))
                .andExpect(status().isOk()).andReturn());

        assertEquals(Set.of("data"), keys(body));
        JsonNode data = body.get("data");
        assertEquals(DASHBOARD_KEYS, keys(data));
        assertEquals(0L, data.get("totalStudents").longValue());
        assertEquals(AS_OF, OffsetDateTime.parse(data.get("asOf").textValue()));
        assertTrue(data.get("asOf").textValue().endsWith("+09:00"));
        for (String list : List.of("helpRequests", "reviewQueue", "callList", "notesToCheck")) {
            assertTrue(data.get(list).isArray());
            assertTrue(data.get(list).isEmpty());
        }
        JsonNode counters = data.get("statusSummary");
        assertEquals(Set.of("EMPLOYED", "SEEKING", "NEED_HELP", "CHECK_REQUIRED"), keys(counters));
        for (String status : List.of("EMPLOYED", "NEED_HELP", "CHECK_REQUIRED")) {
            assertEquals(0L, counters.get(status).longValue());
        }
        assertEquals(Set.of("total", "APPLYING", "WAITING", "OTHER"), keys(counters.get("SEEKING")));
        counters.get("SEEKING").forEach(value -> assertEquals(0L, value.longValue()));
        verify(service).getDashboard();
        verifyNoMoreInteractions(service);
    }

    @Test
    void populatedDashboardMatchesExistingFeFieldsAndKeepsNullableValuesAndSeoulDates() throws Exception {
        when(service.getDashboard()).thenReturn(new CoordinatorDashboardResponse(
                AS_OF, 3L,
                List.of(new CoordinatorDashboardResponse.HelpRequest(
                        4L, 12L, "도움 검증 모의", "모의 과정 1기", AS_OF.minusHours(1), null, null, null)),
                summary(0, 1, 1, 0, 0, 1, 1),
                List.of(new CoordinatorDashboardResponse.ReviewQueueItem(
                        7L, "서류 검증 모의", "모의 공고", AS_OF.minusHours(2))),
                List.of(new CoordinatorDashboardResponse.CallListItem(21L, "전화 검증 모의", null, 10)),
                List.of(new CoordinatorDashboardResponse.NoteToCheck(33L, "비고 검증 모의", "모의 비고"))));

        JsonNode data = body(mvc.perform(get("/api/coordinator/dashboard"))
                .andExpect(status().isOk()).andReturn()).get("data");

        assertEquals(DASHBOARD_KEYS, keys(data));
        JsonNode help = data.get("helpRequests").get(0);
        assertEquals(Set.of("helpRequestId", "studentId", "studentName", "courseLabel", "createdAt",
                "jobTitle", "contactMethod", "memo"), keys(help));
        assertEquals(4L, help.get("helpRequestId").longValue());
        for (String field : List.of("jobTitle", "contactMethod", "memo")) {
            assertTrue(help.has(field));
            assertTrue(help.get(field).isNull());
        }
        assertEquals(AS_OF.minusHours(1), OffsetDateTime.parse(help.get("createdAt").textValue()));
        assertTrue(help.get("createdAt").textValue().endsWith("+09:00"));
        JsonNode review = data.get("reviewQueue").get(0);
        assertEquals(Set.of("applicationId", "studentName", "jobTitle", "submittedAt"), keys(review));
        assertEquals(AS_OF.minusHours(2), OffsetDateTime.parse(review.get("submittedAt").textValue()));
        assertTrue(review.get("submittedAt").textValue().endsWith("+09:00"));
        JsonNode call = data.get("callList").get(0);
        assertEquals(Set.of("studentId", "studentName", "lastResponseAt", "daysSinceResponse"), keys(call));
        assertTrue(call.has("lastResponseAt"));
        assertTrue(call.get("lastResponseAt").isNull());
        assertEquals(10L, call.get("daysSinceResponse").longValue());
        assertEquals(Set.of("studentId", "studentName", "passNote"), keys(data.get("notesToCheck").get(0)));
        assertEquals("모의 비고", data.get("notesToCheck").get(0).get("passNote").textValue());
        assertEquals(1L, data.get("statusSummary").get("SEEKING").get("APPLYING").longValue());
        assertEquals(1L, data.get("statusSummary").get("NEED_HELP").longValue());
        assertEquals(1L, data.get("statusSummary").get("CHECK_REQUIRED").longValue());
    }

    @Test
    void actualResponseTimestampIsSerializedWithSeoulOffset() throws Exception {
        when(service.getDashboard()).thenReturn(new CoordinatorDashboardResponse(
                AS_OF, 1L, List.of(), summary(0, 0, 0, 0, 0, 0, 1), List.of(),
                List.of(new CoordinatorDashboardResponse.CallListItem(
                        1L, "전화 검증 모의", AS_OF.minusDays(7), 7)), List.of()));

        JsonNode call = body(mvc.perform(get("/api/coordinator/dashboard"))
                .andExpect(status().isOk()).andReturn()).get("data").get("callList").get(0);

        assertEquals(AS_OF.minusDays(7), OffsetDateTime.parse(call.get("lastResponseAt").textValue()));
        assertTrue(call.get("lastResponseAt").textValue().endsWith("+09:00"));
    }

    @Test
    void unexpectedServiceFailureKeepsCommonInternalErrorEnvelope() throws Exception {
        when(service.getDashboard()).thenThrow(new IllegalStateException("Mock dashboard read failure"));

        JsonNode body = body(mvc.perform(get("/api/coordinator/dashboard"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.data").doesNotExist()).andReturn());

        assertEquals(Set.of("error"), keys(body));
        assertEquals(Set.of("code", "message"), keys(body.get("error")));
        assertFalse(body.get("error").get("message").textValue().isBlank());
    }

    private static CoordinatorDashboardResponse.StatusSummary summary(
            long employed, long seeking, long applying, long waiting, long other, long needHelp, long checkRequired) {
        return new CoordinatorDashboardResponse.StatusSummary(employed,
                new CoordinatorDashboardResponse.Seeking(seeking, applying, waiting, other), needHelp, checkRequired);
    }

    private static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static Set<String> keys(JsonNode node) {
        Set<String> result = new HashSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }
}
