// 주인: BE-B
package com.ieum.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import com.ieum.common.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CoordinatorApplicationControllerTest {

    private static final String BASE = "/api/coordinator/applications/7";
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final Set<String> DETAIL_KEYS = Set.of("applicationId", "status", "student", "job",
            "document", "verification");
    private static final Set<String> VERIFICATION_KEYS = Set.of("aiSentenceCount", "aiSentenceWithEvidence",
            "excludedSentenceCount", "userEditedCount", "companyInfoStatus");

    private CoordinatorApplicationService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(CoordinatorApplicationService.class);
        mvc = MockMvcBuilders.standaloneSetup(new CoordinatorApplicationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(JSON))
                .build();
    }

    @Test
    void detailKeepsAllSixKeysRawDocumentAndEveryVerificationField() throws Exception {
        CoordinatorApplicationResponse fixture = detailFixture();
        when(service.detail(7L)).thenReturn(fixture);

        JsonNode envelope = body(mvc.perform(get(BASE)).andExpect(status().isOk()).andReturn());
        assertEquals(Set.of("data"), keys(envelope));
        JsonNode data = envelope.get("data");
        assertEquals(DETAIL_KEYS, keys(data));
        assertEquals(7L, data.get("applicationId").longValue());
        assertEquals("SUBMITTED", data.get("status").textValue());
        assertEquals(Set.of("studentId", "name"), keys(data.get("student")));
        assertEquals(91L, data.path("student").path("studentId").longValue());
        assertEquals("검토 모의 수료생", data.path("student").path("name").textValue());
        assertEquals(Set.of("jobPostingId", "companyName", "title"), keys(data.get("job")));
        assertEquals(81L, data.path("job").path("jobPostingId").longValue());
        assertEquals("검토 모의 기업", data.path("job").path("companyName").textValue());
        assertEquals("검토 모의 공고", data.path("job").path("title").textValue());
        assertEquals(fixture.document(), data.get("document"));
        JsonNode verification = data.get("verification");
        assertEquals(VERIFICATION_KEYS, keys(verification));
        assertEquals(2, verification.get("aiSentenceCount").intValue());
        assertEquals(1, verification.get("aiSentenceWithEvidence").intValue());
        assertEquals(3, verification.get("excludedSentenceCount").intValue());
        assertEquals(2, verification.get("userEditedCount").intValue());
        assertEquals("PARTIAL", verification.get("companyInfoStatus").textValue());
        verify(service).detail(7L);
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GENERATING", "FAILED", "DRAFT"})
    void unavailableDocumentRemainsNullWithAllZeroCountersAndNotFoundCompanyInfo(String applicationStatus)
            throws Exception {
        ObjectNode fixture = (ObjectNode) JSON.valueToTree(detailFixture());
        fixture.put("status", applicationStatus);
        fixture.putNull("document");
        ObjectNode verification = (ObjectNode) fixture.get("verification");
        for (String key : List.of("aiSentenceCount", "aiSentenceWithEvidence", "excludedSentenceCount",
                "userEditedCount")) verification.put(key, 0);
        verification.put("companyInfoStatus", "NOT_FOUND");
        when(service.detail(7L)).thenReturn(JSON.treeToValue(fixture, CoordinatorApplicationResponse.class));

        JsonNode data = body(mvc.perform(get(BASE)).andExpect(status().isOk()).andReturn()).get("data");

        assertEquals(DETAIL_KEYS, keys(data));
        assertEquals(applicationStatus, data.get("status").textValue());
        assertTrue(data.has("document") && data.get("document").isNull());
        assertEquals(VERIFICATION_KEYS, keys(data.get("verification")));
        assertEquals(verification, data.get("verification"));
        verify(service).detail(7L);
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "deliver"})
    void approveAndDeliverAcceptNoBodyAndNoContentTypeHeader(String action) throws Exception {
        stubSuccess(action);

        JsonNode envelope = body(mvc.perform(post(BASE + "/" + action))
                .andExpect(status().isOk()).andReturn());

        assertStatusResult(envelope, targetStatus(action));
        verifyAction(action);
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("emptyActionBodies")
    void approveAndDeliverAlsoAcceptExplicitNullOrEmptyObject(String action, String content) throws Exception {
        stubSuccess(action);

        JsonNode envelope = body(mvc.perform(post(BASE + "/" + action)
                .contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isOk()).andReturn());

        assertStatusResult(envelope, targetStatus(action));
        verifyAction(action);
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("nonemptyActionBodies")
    void approveAndDeliverRejectUnexpectedFieldsWrongJsonTypesAndMalformedJson(String action, String content)
            throws Exception {
        JsonNode envelope = body(mvc.perform(post(BASE + "/" + action)
                .contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isBadRequest()).andReturn());

        assertError(envelope, "INVALID_REQUEST");
        verifyNoInteractions(service);
    }

    @Test
    void rejectKeepsOriginalMemoAndReturnsOnlyApplicationIdAndStatus() throws Exception {
        String note = "  모의 보완 사항입니다.\n근거를 확인해 주세요.  ";
        when(service.reject(eq(7L), any(CoordinatorApplicationRejectRequest.class)))
                .thenReturn(statusResult("REJECTED"));

        JsonNode envelope = body(mvc.perform(post(BASE + "/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsBytes(JSON.createObjectNode().put("note", note))))
                .andExpect(status().isOk()).andReturn());

        assertStatusResult(envelope, "REJECTED");
        ArgumentCaptor<CoordinatorApplicationRejectRequest> request =
                ArgumentCaptor.forClass(CoordinatorApplicationRejectRequest.class);
        verify(service).reject(eq(7L), request.capture());
        assertEquals(note, request.getValue().note());
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("invalidRejectBodies")
    void rejectDoesNotCoerceMissingNullOrNonStringNotesOrIgnoreUnknownFields(String content) throws Exception {
        JsonNode envelope = body(mvc.perform(post(BASE + "/reject")
                .contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isBadRequest()).andReturn());

        assertError(envelope, "INVALID_REQUEST");
        verifyNoInteractions(service);
    }

    @Test
    void rejectRequiresARequestBody() throws Exception {
        JsonNode envelope = body(mvc.perform(post(BASE + "/reject"))
                .andExpect(status().isBadRequest()).andReturn());

        assertError(envelope, "INVALID_REQUEST");
        verifyNoInteractions(service);
    }

    @Test
    void blankStringMemoReachesSemanticValidationWithoutChangingItsValue() throws Exception {
        String note = " \n\t ";
        when(service.reject(eq(7L), any(CoordinatorApplicationRejectRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.INVALID_REQUEST));

        JsonNode envelope = body(mvc.perform(post(BASE + "/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsBytes(JSON.createObjectNode().put("note", note))))
                .andExpect(status().isBadRequest()).andReturn());

        assertError(envelope, "INVALID_REQUEST");
        ArgumentCaptor<CoordinatorApplicationRejectRequest> request =
                ArgumentCaptor.forClass(CoordinatorApplicationRejectRequest.class);
        verify(service).reject(eq(7L), request.capture());
        assertEquals(note, request.getValue().note());
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"detail", "approve", "reject", "deliver"})
    void missingApplicationUsesTheExisting404Envelope(String action) throws Exception {
        stubFailure(action, ErrorCode.APPLICATION_NOT_FOUND);

        JsonNode envelope = body(mvc.perform(request(action, "7"))
                .andExpect(status().isNotFound()).andReturn());

        assertError(envelope, "APPLICATION_NOT_FOUND");
        verifyAction(action);
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject", "deliver"})
    void invalidTransitionUses409WithoutChangingTheErrorEnvelope(String action) throws Exception {
        stubFailure(action, ErrorCode.INVALID_STATUS);

        JsonNode envelope = body(mvc.perform(request(action, "7"))
                .andExpect(status().isConflict()).andReturn());

        assertError(envelope, "INVALID_STATUS");
        verifyAction(action);
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("invalidPathIds")
    void nonnumericOrOverflowedApplicationIdReturns400BeforeAnyServiceCall(String action, String id)
            throws Exception {
        JsonNode envelope = body(mvc.perform(request(action, id))
                .andExpect(status().isBadRequest()).andReturn());

        assertError(envelope, "INVALID_REQUEST");
        verifyNoInteractions(service);
    }

    private void stubSuccess(String action) throws Exception {
        if ("approve".equals(action)) when(service.approve(7L)).thenReturn(statusResult("APPROVED"));
        else when(service.deliver(7L)).thenReturn(statusResult("DELIVERED"));
    }

    private void stubFailure(String action, ErrorCode error) {
        BusinessException failure = new BusinessException(error);
        switch (action) {
            case "detail" -> when(service.detail(7L)).thenThrow(failure);
            case "approve" -> when(service.approve(7L)).thenThrow(failure);
            case "reject" -> when(service.reject(eq(7L), any(CoordinatorApplicationRejectRequest.class)))
                    .thenThrow(failure);
            case "deliver" -> when(service.deliver(7L)).thenThrow(failure);
            default -> throw new IllegalArgumentException(action);
        }
    }

    private void verifyAction(String action) {
        switch (action) {
            case "detail" -> verify(service).detail(7L);
            case "approve" -> verify(service).approve(7L);
            case "reject" -> verify(service).reject(eq(7L), any(CoordinatorApplicationRejectRequest.class));
            case "deliver" -> verify(service).deliver(7L);
            default -> throw new IllegalArgumentException(action);
        }
    }

    private static MockHttpServletRequestBuilder request(String action, String id) {
        String path = "/api/coordinator/applications/" + id;
        if ("detail".equals(action)) return get(path);
        MockHttpServletRequestBuilder request = post(path + "/" + action);
        return "reject".equals(action) ? request.contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"모의 보완 사항\"}") : request;
    }

    private static Stream<Arguments> emptyActionBodies() {
        return Stream.of("approve", "deliver")
                .flatMap(action -> Stream.of("null", "{}").map(body -> Arguments.of(action, body)));
    }

    private static Stream<Arguments> nonemptyActionBodies() {
        return Stream.of("approve", "deliver").flatMap(action -> Stream.of(
                "[]", "true", "7", "\"메모\"", "{\"note\":\"모의 메모\"}", "{\"note\":null}", "{")
                .map(body -> Arguments.of(action, body)));
    }

    private static Stream<String> invalidRejectBodies() {
        return Stream.of("null", "[]", "true", "7", "\"메모\"", "{}", "{",
                "{\"note\":null}", "{\"note\":7}", "{\"note\":true}",
                "{\"note\":[]}", "{\"note\":{}}", "{\"unknown\":\"모의 메모\"}",
                "{\"note\":\"모의 메모\",\"extra\":null}");
    }

    private static Stream<Arguments> invalidPathIds() {
        return Stream.of("detail", "approve", "reject", "deliver")
                .flatMap(action -> Stream.of("abc", "9223372036854775808")
                        .map(id -> Arguments.of(action, id)));
    }

    private static String targetStatus(String action) {
        return "approve".equals(action) ? "APPROVED" : "DELIVERED";
    }

    private static CoordinatorApplicationResponse.StatusResult statusResult(String applicationStatus)
            throws Exception {
        ObjectNode value = JSON.createObjectNode().put("applicationId", 7L).put("status", applicationStatus);
        return JSON.treeToValue(value, CoordinatorApplicationResponse.StatusResult.class);
    }

    private static CoordinatorApplicationResponse detailFixture() throws Exception {
        return JSON.readValue("""
                {
                  "applicationId": 7,
                  "status": "SUBMITTED",
                  "student": {"studentId": 91, "name": "검토 모의 수료생"},
                  "job": {"jobPostingId": 81, "companyName": "검토 모의 기업", "title": "검토 모의 공고"},
                  "document": {
                    "companyInfoStatus": "PARTIAL",
                    "companyInfoNote": null,
                    "excludedSentenceCount": 3,
                    "sections": [
                      {"section": "SKILLS", "source": "USER", "tags": [], "summary": [
                        {"sentenceId": "sk-1", "text": "모의 기술 요약", "source": "AI", "evidence": []}
                      ]},
                      {"section": "PROJECTS", "items": [{"itemId": "prj-71", "role": null, "bullets": [
                        {"sentenceId": "pj-1", "text": "모의 프로젝트 문장", "source": "AI", "evidence": [
                          {"refType": "PROJECT", "refId": 71, "label": "모의 근거", "quote": "모의 원문",
                           "url": null, "collectedAt": "2026-10-28T12:00:00+09:00"}
                        ]}
                      ]}]},
                      {"section": "PORTFOLIO", "items": [{"itemId": "pf-71", "fileName": "mock.pdf", "include": false}]},
                      {"section": "COVER_LETTER", "sentences": [
                        {"sentenceId": "cl-1", "text": "모의 직접 수정", "source": "USER", "evidence": []}
                      ]}
                    ]
                  },
                  "verification": {"aiSentenceCount": 2, "aiSentenceWithEvidence": 1,
                    "excludedSentenceCount": 3, "userEditedCount": 2, "companyInfoStatus": "PARTIAL"}
                }
                """, CoordinatorApplicationResponse.class);
    }

    private static void assertStatusResult(JsonNode envelope, String applicationStatus) {
        assertEquals(Set.of("data"), keys(envelope));
        JsonNode data = envelope.get("data");
        assertEquals(Set.of("applicationId", "status"), keys(data));
        assertEquals(7L, data.get("applicationId").longValue());
        assertEquals(applicationStatus, data.get("status").textValue());
    }

    private static void assertError(JsonNode envelope, String code) {
        assertEquals(Set.of("error"), keys(envelope));
        assertEquals(Set.of("code", "message"), keys(envelope.get("error")));
        assertEquals(code, envelope.path("error").path("code").textValue());
        assertTrue(envelope.path("error").path("message").isTextual());
        assertTrue(!envelope.path("error").path("message").textValue().isBlank());
    }

    private static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static Set<String> keys(JsonNode value) {
        Set<String> keys = new HashSet<>();
        value.fieldNames().forEachRemaining(keys::add);
        return keys;
    }
}
