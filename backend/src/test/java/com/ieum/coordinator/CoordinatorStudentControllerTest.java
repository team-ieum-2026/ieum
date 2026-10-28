// 주인: BE-B
package com.ieum.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import com.ieum.common.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CoordinatorStudentControllerTest {

    private static final ObjectMapper JSON = JsonMapper.builder().addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE).build();
    private static final Set<String> ROW_KEYS = Set.of("studentId", "name", "courseLabel", "completedOn",
            "status", "subStatus", "lastResponseAt", "hasNote");
    private static final Set<String> STUDENT_KEYS = Set.of("studentId", "name", "courseLabel", "completedOn",
            "status", "subStatus", "lastResponseAt", "hasNote", "email", "desiredJob", "preferredRegion", "reason");
    private static final Set<String> LOG_KEYS = Set.of("contactLogId", "contactType", "note", "createdAt");
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.of(2026, 10, 28, 12, 0, 0, 0,
            ZoneOffset.ofHours(9));

    private CoordinatorStudentService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(CoordinatorStudentService.class);
        mvc = MockMvcBuilders.standaloneSetup(new CoordinatorStudentController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(JSON))
                .build();
    }

    @Test
    void omittedFiltersUseDefaultPaginationAndKeepTheEmptyListContract() throws Exception {
        when(service.list(null, null, null, 1, 20))
                .thenReturn(new CoordinatorStudentResponse.ListResult(List.of(), 0, 1, 20));

        JsonNode body = body(mvc.perform(get("/api/coordinator/students"))
                .andExpect(status().isOk()).andReturn());

        assertEquals(Set.of("data"), keys(body));
        JsonNode data = body.get("data");
        assertEquals(Set.of("items", "total", "page", "size"), keys(data));
        assertTrue(data.get("items").isArray());
        assertTrue(data.get("items").isEmpty());
        assertEquals(0, data.get("total").intValue());
        assertEquals(1, data.get("page").intValue());
        assertEquals(20, data.get("size").intValue());
        verify(service).list(null, null, null, 1, 20);
    }

    @Test
    void explicitFiltersAndLiteralSearchTextAreDelegatedWithoutChangingTheValues() throws Exception {
        String query = "모의_%\\";
        when(service.list("SEEKING", "OTHER", query, 3, 5))
                .thenReturn(new CoordinatorStudentResponse.ListResult(List.of(), 11, 3, 5));

        mvc.perform(get("/api/coordinator/students").param("status", "SEEKING").param("subStatus", "OTHER")
                        .param("q", query).param("page", "3").param("size", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(11))
                .andExpect(jsonPath("$.data.page").value(3)).andExpect(jsonPath("$.data.size").value(5));

        verify(service).list("SEEKING", "OTHER", query, 3, 5);
    }

    @Test
    void explicitEmptyFiltersAreLeftForTheServiceToNormalize() throws Exception {
        when(service.list("", "", "", 1, 20))
                .thenReturn(new CoordinatorStudentResponse.ListResult(List.of(), 0, 1, 20));

        mvc.perform(get("/api/coordinator/students").param("status", "").param("subStatus", "").param("q", ""))
                .andExpect(status().isOk());

        verify(service).list("", "", "", 1, 20);
    }

    @Test
    void listRowsMatchAllEightFieldsAndRetainNullableStatusDetailAndResponseTime() throws Exception {
        var response = JSON.readValue("""
                {"items":[{"studentId":7,"name":"목록 검증 모의","courseLabel":"모의 과정 1기",
                  "completedOn":"2026-08-29","status":"CHECK_REQUIRED","subStatus":null,
                  "lastResponseAt":null,"hasNote":false}],"total":1,"page":1,"size":20}
                """, CoordinatorStudentResponse.ListResult.class);
        when(service.list(null, null, null, 1, 20)).thenReturn(response);

        JsonNode row = body(mvc.perform(get("/api/coordinator/students"))
                .andExpect(status().isOk()).andReturn()).get("data").get("items").get(0);

        assertEquals(ROW_KEYS, keys(row));
        assertEquals("2026-08-29", row.get("completedOn").textValue());
        assertTrue(row.get("subStatus").isNull());
        assertTrue(row.get("lastResponseAt").isNull());
        assertFalse(row.get("hasNote").booleanValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=not-a-number", "page=1.5", "page=2147483648", "size=invalid", "size=2147483648"})
    void nonIntegerOrOverflowedPaginationReturns400BeforeService(String parameter) throws Exception {
        String[] parts = parameter.split("=", 2);
        mvc.perform(get("/api/coordinator/students").param(parts[0], parts[1]))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void absentProfileAndOptionalFieldsRemainNullWithZeroRegisteredCountsAndEmptyLists() throws Exception {
        when(service.detail(7L)).thenReturn(emptyDetail());

        JsonNode data = body(mvc.perform(get("/api/coordinator/students/7"))
                .andExpect(status().isOk()).andReturn()).get("data");

        assertEquals(Set.of("student", "competencyProfile", "registered", "history", "contactLogs"), keys(data));
        assertEquals(STUDENT_KEYS, keys(data.get("student")));
        for (String field : List.of("subStatus", "lastResponseAt", "email", "desiredJob", "preferredRegion", "reason")) {
            assertTrue(data.get("student").has(field));
            assertTrue(data.get("student").get(field).isNull());
        }
        assertTrue(data.has("competencyProfile"));
        assertTrue(data.get("competencyProfile").isNull());
        assertEquals(Set.of("education", "certificates", "projects", "hasCoverLetter"), keys(data.get("registered")));
        for (String field : List.of("education", "certificates", "projects")) {
            assertEquals(0, data.get("registered").get(field).intValue());
        }
        assertFalse(data.get("registered").get("hasCoverLetter").booleanValue());
        assertTrue(data.get("history").isArray());
        assertTrue(data.get("history").isEmpty());
        assertTrue(data.get("contactLogs").isArray());
        assertTrue(data.get("contactLogs").isEmpty());
        verify(service).detail(7L);
    }

    @Test
    void detailKeepsEmptyRecommendationHeadersHistoryNullKeysAndExistingNullContactNotes() throws Exception {
        ObjectNode fixture = JSON.valueToTree(emptyDetail());
        fixture.set("competencyProfile", JSON.readTree("""
                {"roles":[],"skills":[],"achievements":[],"certificates":[],"desiredJob":null,"preferredRegion":null}
                """));
        fixture.set("history", JSON.readTree("""
                [{"recommendationId":32,"createdAt":"2026-10-28T09:00:00+09:00","items":[]},
                 {"recommendationId":31,"createdAt":"2026-10-27T09:00:00+09:00","items":[
                   {"itemId":91,"companyName":"모의 기업","title":"모의 공고","response":"NONE",
                    "passReason":null,"passNote":null,"respondedAt":null,"applicationId":null,"applicationStatus":null}]}]
                """));
        fixture.set("contactLogs", JSON.readTree("""
                [{"contactLogId":4,"contactType":"PHONE","note":null,"createdAt":"2026-10-28T12:00:00+09:00"}]
                """));
        when(service.detail(7L)).thenReturn(JSON.treeToValue(fixture, CoordinatorStudentResponse.Detail.class));

        JsonNode data = body(mvc.perform(get("/api/coordinator/students/7"))
                .andExpect(status().isOk()).andReturn()).get("data");

        assertEquals(Set.of("roles", "skills", "achievements", "certificates", "desiredJob", "preferredRegion"),
                keys(data.get("competencyProfile")));
        assertEquals(2, data.get("history").size());
        JsonNode empty = data.get("history").get(0);
        assertEquals(Set.of("recommendationId", "createdAt", "items"), keys(empty));
        assertTrue(empty.get("items").isEmpty());
        assertTrue(empty.get("createdAt").textValue().endsWith("+09:00"));
        JsonNode item = data.get("history").get(1).get("items").get(0);
        assertEquals(Set.of("itemId", "companyName", "title", "response", "passReason", "passNote", "respondedAt",
                "applicationId", "applicationStatus"), keys(item));
        for (String field : List.of("passReason", "passNote", "respondedAt", "applicationId", "applicationStatus")) {
            assertTrue(item.has(field));
            assertTrue(item.get(field).isNull());
        }
        JsonNode log = data.get("contactLogs").get(0);
        assertEquals(LOG_KEYS, keys(log));
        assertTrue(log.has("note"));
        assertTrue(log.get("note").isNull());
        assertTrue(log.get("createdAt").textValue().endsWith("+09:00"));
    }

    @Test
    void missingStudentReturnsTheExisting404Envelope() throws Exception {
        when(service.detail(9999L)).thenThrow(new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        mvc.perform(get("/api/coordinator/students/9999"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("STUDENT_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "9223372036854775808"})
    void malformedStudentIdReturns400BeforeAnyServiceCall(String studentId) throws Exception {
        mvc.perform(get("/api/coordinator/students/" + studentId))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PHONE", "MEETING", "MESSAGE"})
    void contactCreationSupportsAllThreeTypesAndPreservesOriginalTextAndSeoulTimestamp(String contactType)
            throws Exception {
        String note = "  모의 상담\n\"원문\"과 공백을 유지해요.\t";
        var request = new ContactLogCreateRequest(contactType, note);
        when(service.addContactLog(7L, request))
                .thenReturn(new CoordinatorStudentResponse.ContactLog(4L, contactType, note, CREATED_AT));

        JsonNode body = body(mvc.perform(post("/api/coordinator/students/7/contact-logs")
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(request)))
                .andExpect(status().isOk()).andReturn());

        assertEquals(Set.of("data"), keys(body));
        JsonNode data = body.get("data");
        assertEquals(LOG_KEYS, keys(data));
        assertEquals(4, data.get("contactLogId").intValue());
        assertEquals(contactType, data.get("contactType").textValue());
        assertEquals(note, data.get("note").textValue());
        assertEquals(CREATED_AT, OffsetDateTime.parse(data.get("createdAt").textValue()));
        assertTrue(data.get("createdAt").textValue().endsWith("+09:00"));
        verify(service).addContactLog(7L, request);
    }

    @ParameterizedTest
    @MethodSource("invalidContactBodies")
    void malformedJsonOrNonStringContactFieldsReturns400WithoutCallingService(String content) throws Exception {
        mvc.perform(post("/api/coordinator/students/7/contact-logs")
                        .contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void missingStudentDuringContactCreationUsesTheSame404Envelope() throws Exception {
        when(service.addContactLog(eq(9999L), any(ContactLogCreateRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        mvc.perform(post("/api/coordinator/students/9999/contact-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactType\":\"PHONE\",\"note\":\"모의 상담\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("STUDENT_NOT_FOUND"));
    }

    private static Stream<String> invalidContactBodies() {
        return Stream.of("null", "[]", "true", "\"not-an-object\"", "{}", "{",
                "{\"contactType\":\"PHONE\"}", "{\"note\":\"mock note\"}",
                "{\"contactType\":null,\"note\":\"mock note\"}",
                "{\"contactType\":1,\"note\":\"mock note\"}",
                "{\"contactType\":true,\"note\":\"mock note\"}",
                "{\"contactType\":[],\"note\":\"mock note\"}",
                "{\"contactType\":{},\"note\":\"mock note\"}",
                "{\"contactType\":\"PHONE\",\"note\":null}",
                "{\"contactType\":\"PHONE\",\"note\":17}",
                "{\"contactType\":\"PHONE\",\"note\":false}",
                "{\"contactType\":\"PHONE\",\"note\":[]}",
                "{\"contactType\":\"PHONE\",\"note\":{}}",
                "{\"contactType\":\"PHONE\",\"note\":\"mock note\",\"extra\":\"value\"}");
    }

    private static CoordinatorStudentResponse.Detail emptyDetail() throws Exception {
        return JSON.readValue("""
                {"student":{"studentId":7,"name":"상세 검증 모의","courseLabel":"모의 과정 1기",
                  "completedOn":"2026-08-29","status":"CHECK_REQUIRED","subStatus":null,
                  "lastResponseAt":null,"hasNote":false,"email":null,"desiredJob":null,
                  "preferredRegion":null,"reason":null},"competencyProfile":null,
                 "registered":{"education":0,"certificates":0,"projects":0,"hasCoverLetter":false},
                 "history":[],"contactLogs":[]}
                """, CoordinatorStudentResponse.Detail.class);
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
