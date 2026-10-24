// 주인: BE-A
package com.ieum.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 실제 AI 서버(app.ai.server-url)를 RestClient 로 호출한다. app.ai.mock=false 일 때만 등록. */
@Component
@ConditionalOnProperty(name = "app.ai.mock", havingValue = "false")
public class RestAiClient implements AiClient {

    private final RestClient restClient;

    public RestAiClient(@Value("${app.ai.server-url}") String serverUrl) {
        this.restClient = RestClient.builder().baseUrl(serverUrl).build();
    }

    @Override
    public JsonNode extractProfile(JsonNode request) {
        // TODO(BE-B · S2): 요청 형식은 docs/API.md 4장 · AI-A 확정 후 맞춤
        return post("/ai/profile/extract", request);
    }

    @Override
    public JsonNode indexJobs(JsonNode request) {
        // TODO(BE-A-03): content_hash 가 바뀐 공고만 보냄
        return post("/ai/matching/index", request);
    }

    @Override
    public JsonNode recommend(JsonNode request) {
        // TODO(BE-A-04): { studentId, profile, excludeJobPostingIds, topK }
        return post("/ai/matching/recommend", request);
    }

    @Override
    public JsonNode collectCompanyInfo(JsonNode request) {
        // TODO(BE-A-10)
        return post("/ai/company/collect", request);
    }

    @Override
    public JsonNode draftDocument(JsonNode request) {
        // TODO(BE-A-07): 비동기 호출, 타임아웃은 60초 이상으로
        return post("/ai/document/draft", request);
    }

    @Override
    public JsonNode health() {
        try {
            JsonNode body = restClient.get().uri("/ai/health").retrieve().body(JsonNode.class);
            return unwrapData(body);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.AI_SERVER_ERROR);
        }
    }

    private JsonNode post(String path, JsonNode request) {
        try {
            JsonNode body = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            return unwrapData(body);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.AI_SERVER_ERROR);
        }
    }

    /** AI 서버 응답 { "data": ... } 에서 data 를 꺼낸다. error 가 있으면 AI_SERVER_ERROR. */
    private JsonNode unwrapData(JsonNode body) {
        if (body == null || body.hasNonNull("error") || !body.has("data")) {
            throw new BusinessException(ErrorCode.AI_SERVER_ERROR);
        }
        return body.get("data");
    }
}
