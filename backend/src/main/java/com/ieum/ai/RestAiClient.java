// 주인: BE-A
package com.ieum.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 실제 AI 서버(app.ai.server-url)를 RestClient 로 호출한다. app.ai.mock=false 일 때만 등록. */
@Component
@ConditionalOnProperty(name = "app.ai.mock", havingValue = "false")
public class RestAiClient implements AiClient {

    private final RestClient restClient;

    public RestAiClient(@Value("${app.ai.server-url}") String serverUrl) {
        // JDK HttpClient 기본값(HTTP/2)은 평문 http 에 h2c 업그레이드 헤더를 붙이고,
        // uvicorn 은 그 요청의 본문을 버려 422(본문 없음)가 난다. HTTP/1.1 로 고정.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        // 추천(리랭커)이 1명당 30~60초 걸릴 수 있어 여유를 둔다
        factory.setReadTimeout(Duration.ofSeconds(120));
        this.restClient = RestClient.builder().baseUrl(serverUrl).requestFactory(factory).build();
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
