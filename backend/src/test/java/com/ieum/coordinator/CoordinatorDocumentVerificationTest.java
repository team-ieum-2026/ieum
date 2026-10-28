// 주인: BE-B
package com.ieum.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CoordinatorDocumentVerificationTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void existingC4FixtureMatchesItsIndependentlyDeclaredSummaryWithoutChangingTheDocument() throws Exception {
        JsonNode fixture = JSON.readTree(Files.readString(Path.of("..", "frontend", "public", "mock",
                "C4_coordinator_application.json"))).path("data");
        JsonNode document = fixture.path("document");
        JsonNode before = document.deepCopy();
        var expected = JSON.treeToValue(fixture.path("verification"), CoordinatorApplicationResponse.Verification.class);

        assertEquals(expected, CoordinatorDocumentVerification.summarize(document));
        assertEquals(before, document);
    }

    @Test
    void countsSentencePathsAndUserItemsButNeverEvidenceMetadataOrCurrentProfileFlags() throws Exception {
        JsonNode document = JSON.readTree("""
                {
                  "companyInfoStatus":"PARTIAL", "excludedSentenceCount":4,
                  "sections":[
                    {"section":"BASIC","source":"USER","fields":{"name":"모의"}},
                    {"section":"SKILLS","source":"AI","tags":["모의 기술"],"summary":[
                      {"sentenceId":"s1","source":"AI","evidence":[
                        {"refType":"PROJECT","refId":11,"source":"USER"},
                        {"refType":"PROJECT","refId":12,"source":"USER"}]},
                      {"sentenceId":"s2","source":"USER","evidence":[]}]},
                    {"section":"PROJECTS","items":[{"includeInApply":false,"role":"모의 역할",
                      "bullets":[{"sentenceId":"p1","source":"AI","evidence":[
                        {"refType":"PROJECT","refId":11}]}]}]},
                    {"section":"COVER_LETTER","sentences":[
                      {"sentenceId":"c1","source":"AI","evidence":[]},
                      {"sentenceId":"c2","source":"USER","evidence":[]}]},
                    {"section":"CERTIFICATES","items":[{"source":"USER","name":"모의 자격증"}]},
                    {"section":"TRAINING","items":[{"source":"REGISTERED"}],
                      "sentences":[{"source":"AI","evidence":[{}]}]}
                  ]
                }
                """);
        JsonNode before = document.deepCopy();

        assertEquals(new CoordinatorApplicationResponse.Verification(3, 2, 4, 4, "PARTIAL"),
                CoordinatorDocumentVerification.summarize(document));
        assertEquals(before, document, "근거 없는 AI 문장을 제거하거나 제외 수를 바꾸지 않는다");
    }

    @Test
    void noDocumentReturnsZeroSummaryAndNotFoundCompanyStatus() {
        var empty = new CoordinatorApplicationResponse.Verification(0, 0, 0, 0, "NOT_FOUND");
        assertEquals(empty, CoordinatorDocumentVerification.summarize(null));
        assertEquals(empty, CoordinatorDocumentVerification.summarize(JSON.nullNode()));
        assertEquals(empty, CoordinatorDocumentVerification.summarize(JSON.createObjectNode()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "-1", "2.5", "2147483648", "\"3\"", "true"})
    void invalidExcludedMetadataDoesNotCreateANegativeOrFabricatedCount(String metadata) throws Exception {
        JsonNode document = JSON.readTree("{\"companyInfoStatus\":\"UNKNOWN\",\"excludedSentenceCount\":"
                + metadata + "}");

        assertEquals(new CoordinatorApplicationResponse.Verification(0, 0, 0, 0, "NOT_FOUND"),
                CoordinatorDocumentVerification.summarize(document));
    }

    @Test
    void arrayShapeAndMissingEvidenceAreHandledWithoutCountingAiSectionsAsSentences() throws Exception {
        JsonNode document = JSON.readTree("""
                {"companyInfoStatus":"FOUND","excludedSentenceCount":2,"sections":[
                  {"section":"SKILLS","source":"AI","summary":{"source":"AI","evidence":[{}]}},
                  {"section":"PROJECTS","items":[null,{"bullets":[null,"모의",1,
                    {"source":"AI"},{"source":"AI","evidence":null},
                    {"source":"AI","evidence":{}},{"source":"REGISTERED","evidence":[{}]}]}]}
                ]}
                """);

        assertEquals(new CoordinatorApplicationResponse.Verification(3, 0, 2, 0, "FOUND"),
                CoordinatorDocumentVerification.summarize(document));
    }
}
