// 주인: BE-A
package com.ieum.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ieum.recommendation.RecommendationMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
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
    private final RecommendationMapper recommendationMapper;

    public MockAiClient(ObjectMapper objectMapper, RecommendationMapper recommendationMapper) {
        this.objectMapper = objectMapper;
        this.recommendationMapper = recommendationMapper;
    }

    @Override
    public JsonNode extractProfile(JsonNode request) {
        return load("profile_extract.json");
    }

    @Override
    public JsonNode indexJobs(JsonNode request) {
        return load("matching_index.json");
    }

    /**
     * 고정 추천. AI-B 고정 응답과 같이 프로필이 비어 있으면 items: [].
     * 공고 번호는 고정값 대신 지금 DB의 활성 공고 중 최근 등록 순으로 고른다 (배치 후 501~503이 꺼져도 M1이 비지 않게).
     * 근거 문장은 matching_recommend.json 것을 순서대로 붙인다.
     */
    @Override
    public JsonNode recommend(JsonNode request) {
        ObjectNode data = objectMapper.createObjectNode();
        ArrayNode items = data.putArray("items");
        JsonNode profile = request.path("profile");
        if (!profile.isObject() || profile.isEmpty()) {
            return data;
        }

        List<Long> exclude = new ArrayList<>();
        request.path("excludeJobPostingIds").forEach(id -> exclude.add(id.asLong()));
        int topK = Math.max(request.path("topK").asInt(3), 0);
        List<Long> jobIds = recommendationMapper.selectRecentActiveJobIds(exclude, topK);

        JsonNode templates = load("matching_recommend.json").path("items");
        for (int i = 0; i < jobIds.size(); i++) {
            ObjectNode item = items.addObject();
            item.put("jobPostingId", jobIds.get(i));
            item.put("rank", i + 1);
            item.set("reasons", templates.path(i % Math.max(templates.size(), 1)).path("reasons").deepCopy());
        }
        return data;
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
