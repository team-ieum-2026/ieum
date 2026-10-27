// 주인: BE-A
package com.ieum.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ieum.ai.AiClient;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 시연 기업 정보 수집 결과 저장 (BE-A-10, S7).
 *  1) 기업마다 AI POST /ai/company/collect { companyName } 호출
 *  2) 응답 data.companyInfo (없으면 data 자체) 를 status + info JSON 으로 나눠 company_info upsert
 *  3) company_name 은 요청한 이름으로 저장 (job_posting.company_name 과 이름 일치로 연결, docs/ERD.md)
 *  4) NOT_FOUND 도 저장 → 서류 생성 때 "공고 외 정보 확인 불가"
 *  5) 한 기업이 실패하면 그 기업의 기존 데이터는 그대로 두고 다음 기업으로 넘어간다
 */
@Service
public class CompanyInfoService {

    private static final Logger log = LoggerFactory.getLogger(CompanyInfoService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Set<String> STATUSES = Set.of("FOUND", "PARTIAL", "NOT_FOUND");

    private final AiClient aiClient;
    private final CompanyInfoMapper companyInfoMapper;
    private final ObjectMapper objectMapper;

    public CompanyInfoService(AiClient aiClient, CompanyInfoMapper companyInfoMapper, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.companyInfoMapper = companyInfoMapper;
        this.objectMapper = objectMapper;
    }

    public CompanyCollectResult collect(List<String> companyNames) {
        String now = OffsetDateTime.now(KST).truncatedTo(ChronoUnit.SECONDS).toString();
        Set<String> names = new LinkedHashSet<>();
        for (String name : companyNames) {
            if (name != null && !name.isBlank()) {
                names.add(name.trim());
            }
        }
        if (names.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "수집할 기업명을 하나 이상 적어 주세요.");
        }

        List<CompanyCollectResult.Item> items = new ArrayList<>();
        for (String name : names) {
            try {
                items.add(CompanyCollectResult.Item.saved(collectOne(name)));
            } catch (RuntimeException e) {
                log.warn("기업 정보 수집 실패 ({}): {}", name, e.getMessage());
                items.add(CompanyCollectResult.Item.failed(name, e.getMessage()));
            }
        }
        int saved = (int) items.stream().filter(i -> "SAVED".equals(i.result())).count();
        log.info("기업 정보 수집 완료: 요청 {} · 저장 {} · 실패 {}", names.size(), saved, names.size() - saved);
        return new CompanyCollectResult(names.size(), saved, names.size() - saved, items, now);
    }

    /** 서류 생성(BE-A-07)에서 쓰는 조회. 없으면 null */
    public CompanyInfo findByCompanyName(String companyName) {
        return companyInfoMapper.selectByCompanyName(companyName);
    }

    private CompanyInfo collectOne(String companyName) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("companyName", companyName);
        JsonNode data = aiClient.collectCompanyInfo(request);
        JsonNode collected = data != null && data.has("companyInfo") ? data.get("companyInfo") : data;
        if (collected == null || !collected.isObject()) {
            throw new BusinessException(ErrorCode.AI_SERVER_ERROR, "AI 응답에 companyInfo가 없어요.");
        }
        String status = collected.path("status").asText("");
        if (!STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.AI_SERVER_ERROR, "AI 응답의 status가 올바르지 않아요: " + status);
        }

        CompanyInfo row = new CompanyInfo();
        row.setCompanyName(companyName);
        row.setStatus(status);
        row.setInfo(toInfo(collected));
        companyInfoMapper.upsert(row);
        return companyInfoMapper.selectByCompanyName(companyName);
    }

    /** DB info 컬럼 모양으로 맞춘다. 못 찾은 항목은 빈 배열 · null (지어내지 않음) */
    private ObjectNode toInfo(JsonNode collected) {
        ObjectNode info = objectMapper.createObjectNode();
        info.set("talentKeywords", arrayOrEmpty(collected.get("talentKeywords")));
        info.set("coreValues", arrayOrEmpty(collected.get("coreValues")));
        JsonNode recentIssue = collected.get("recentIssue");
        if (recentIssue != null && recentIssue.isTextual() && !recentIssue.asText().isBlank()) {
            info.put("recentIssue", recentIssue.asText());
        } else {
            info.putNull("recentIssue");
        }
        info.set("sources", arrayOrEmpty(collected.get("sources")));
        return info;
    }

    private JsonNode arrayOrEmpty(JsonNode node) {
        return node != null && node.isArray() ? node.deepCopy() : objectMapper.createArrayNode();
    }
}
