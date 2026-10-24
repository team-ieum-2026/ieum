// 주인: BE-A
package com.ieum.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * AI 서버 없이 고정 응답을 돌려주는 구현. app.ai.mock=true(기본) 일 때 등록.
 * 고정 응답은 src/main/resources/ai-mock/*.json (docs/API.md 4장 · frontend/public/mock 과 같은 모양).
 */
@Component
@ConditionalOnProperty(name = "app.ai.mock", havingValue = "true", matchIfMissing = true)
public class MockAiClient implements AiClient {

    private final ObjectMapper objectMapper;

    public MockAiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public JsonNode extractProfile(JsonNode request) {
        return load("profile_extract.json");
    }

    @Override
    public JsonNode indexJobs(JsonNode request) {
        return load("matching_index.json");
    }

    @Override
    public JsonNode recommend(JsonNode request) {
        return load("matching_recommend.json");
    }

    @Override
    public JsonNode collectCompanyInfo(JsonNode request) {
        return load("company_collect.json");
    }

    @Override
    public JsonNode draftDocument(JsonNode request) {
        return load("document_draft.json");
    }

    @Override
    public JsonNode health() {
        return load("health.json");
    }

    private JsonNode load(String fileName) {
        try (InputStream in = new ClassPathResource("ai-mock/" + fileName).getInputStream()) {
            return objectMapper.readTree(in).get("data");
        } catch (IOException e) {
            throw new UncheckedIOException("AI 고정 응답 파일을 읽지 못했어요: " + fileName, e);
        }
    }
}
