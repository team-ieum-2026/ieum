// 주인: BE-A
package com.ieum.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 서울 열린데이터광장 recMntList 호출.
 * GET {baseUrl}/{key}/json/recMntList/{start}/{end}/ · 한 번에 최대 1,000건 (docs/JOB_API.md "호출")
 * 키는 로그에 남기지 않는다.
 */
@Component
public class SeoulJobClient {

    private static final Logger log = LoggerFactory.getLogger(SeoulJobClient.class);
    private static final String OK_CODE = "INFO-000";
    private static final int MAX_TRY = 3;

    private final SeoulJobProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public SeoulJobClient(SeoulJobProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.restClient = RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory).build();
    }

    /** 전체 공고를 페이지 단위로 모두 받는다. 한 페이지라도 끝내 실패하면 SeoulApiException. */
    public List<JsonNode> fetchAll() {
        if (!props.hasKey()) {
            throw new SeoulApiException("SEOUL_OPENAPI_KEY가 비어 있어요.");
        }
        int size = props.pageSize();
        JsonNode first = fetchPage(1, size);
        int total = first.path("list_total_count").asInt(0);
        List<JsonNode> rows = new ArrayList<>(Math.max(total, 0));
        first.path("row").forEach(rows::add);

        for (int start = size + 1; start <= total; start += size) {
            int end = Math.min(start + size - 1, total);
            fetchPage(start, end).path("row").forEach(rows::add);
        }
        log.info("서울 채용정보 수집: 전체 {}건 중 {}건 받음", total, rows.size());
        return rows;
    }

    /** recMntList 객체({ list_total_count, RESULT, row[] })를 돌려준다. 실패하면 최대 3번 시도. */
    JsonNode fetchPage(int start, int end) {
        String path = "/" + props.openapiKey() + "/json/" + props.jobService() + "/" + start + "/" + end + "/";
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_TRY; attempt++) {
            try {
                String body = restClient.get().uri(path).retrieve().body(String.class);
                JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
                JsonNode list = root.path(props.jobService());
                String code = list.path("RESULT").path("CODE").asText(root.path("RESULT").path("CODE").asText(""));
                if (OK_CODE.equals(code) && list.has("row")) {
                    return list;
                }
                last = new SeoulApiException("서울 API 응답 코드 " + code + " (" + start + "~" + end + ")");
            } catch (SeoulApiException e) {
                last = e;
            } catch (Exception e) {
                last = new SeoulApiException("서울 API 호출 실패 (" + start + "~" + end + "): " + e.getClass().getSimpleName(), e);
            }
            log.warn("서울 API {}~{} 시도 {}/{} 실패: {}", start, end, attempt, MAX_TRY, last.getMessage());
        }
        throw last instanceof SeoulApiException s ? s : new SeoulApiException("서울 API 호출 실패", last);
    }
}
