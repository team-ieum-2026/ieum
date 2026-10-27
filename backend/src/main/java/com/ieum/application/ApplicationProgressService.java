// 주인: BE-A
package com.ieum.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * M5 서류 생성 진행 상태 (BE-A-07, S7). FE 가 2초마다 조회한다.
 * AI 서류 생성은 한 번의 호출이라 단계별 실제 진행을 알 수 없다. 그래서 단계는 지원서 상태로 정한다.
 * - GENERATING: 기업 정보 DONE · 자기소개서 RUNNING · 이력서 WAITING
 * - FAILED: 기업 정보 DONE · 자기소개서 FAILED · 이력서 WAITING
 * - 그 밖(DRAFT 이후): 모두 DONE
 * 기업 정보 단계의 note 는 company_info 에서 무엇을 찾았는지 알려 준다.
 */
@Service
public class ApplicationProgressService {

    static final String NOT_FOUND_NOTE = "공고 외 정보 확인 불가";
    static final String FAILED_NOTE = "서류를 만들지 못했어요";

    private final ApplicationMapper mapper;
    private final ObjectMapper objectMapper;

    public ApplicationProgressService(ApplicationMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public ApplicationProgressResponse getProgress(Long applicationId) {
        ApplicationRows.Progress row = mapper.selectProgress(applicationId);
        if (row == null) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND);
        }
        String companyNote = companyNote(mapper.selectCompanyInfo(row.getCompanyName()));
        List<ApplicationProgressResponse.Step> steps = switch (row.getStatus()) {
            case DocumentGenerationService.GENERATING -> List.of(
                    step("COMPANY_INFO", "DONE", companyNote),
                    step("COVER_LETTER", "RUNNING", null),
                    step("RESUME", "WAITING", null));
            case DocumentGenerationService.FAILED -> List.of(
                    step("COMPANY_INFO", "DONE", companyNote),
                    step("COVER_LETTER", "FAILED", FAILED_NOTE),
                    step("RESUME", "WAITING", null));
            default -> List.of(
                    step("COMPANY_INFO", "DONE", companyNote),
                    step("COVER_LETTER", "DONE", null),
                    step("RESUME", "DONE", null));
        };
        return new ApplicationProgressResponse(row.getApplicationId(), row.getStatus(), steps);
    }

    /** 예: "인재상 · 핵심 가치 · 최근 뉴스 확인", "인재상 확인 · 최근 뉴스는 찾지 못함", "공고 외 정보 확인 불가" */
    String companyNote(ApplicationRows.CompanyInfo row) {
        if (row == null || "NOT_FOUND".equals(row.getStatus())) {
            return NOT_FOUND_NOTE;
        }
        JsonNode info = readInfo(row.getInfo());
        List<String> found = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        (hasItems(info.path("talentKeywords")) ? found : missing).add("인재상");
        (hasItems(info.path("coreValues")) ? found : missing).add("핵심 가치");
        (hasText(info.path("recentIssue")) ? found : missing).add("최근 뉴스");
        if (found.isEmpty()) {
            return NOT_FOUND_NOTE;
        }
        String note = String.join(" · ", found) + " 확인";
        if (!missing.isEmpty()) {
            String last = missing.get(missing.size() - 1);
            note += " · " + String.join(" · ", missing) + (hasBatchim(last) ? "은" : "는") + " 찾지 못함";
        }
        return note;
    }

    private JsonNode readInfo(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }

    private static boolean hasItems(JsonNode node) {
        return node.isArray() && !node.isEmpty();
    }

    private static boolean hasText(JsonNode node) {
        return node.isTextual() && !node.asText().isBlank();
    }

    /** 마지막 글자에 받침이 있으면 true (은/는 고르기) */
    private static boolean hasBatchim(String word) {
        char last = word.charAt(word.length() - 1);
        return last >= '가' && last <= '힣' && (last - '가') % 28 != 0;
    }

    private static ApplicationProgressResponse.Step step(String key, String state, String note) {
        return new ApplicationProgressResponse.Step(key, state, note);
    }
}
