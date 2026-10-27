// 주인: BE-A
package com.ieum.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * M7 지원 현황 (BE-A-09, S9 · S10).
 * - applications: 수료생의 지원서 전체(생성 중 · 초안 · 실패 포함), 최근 생성 순
 * - otherItems: 가장 최근 추천 묶음에서 지원서가 없는 항목(NONE · PASS · HELP), rank 순
 * userEditedCount 는 서류 JSON 에서 source 가 USER 인 문장 · 항목 · 섹션 수다. 근거(evidence) 안은 세지 않는다.
 */
@Service
public class StudentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(StudentApplicationService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ApplicationMapper mapper;
    private final ObjectMapper objectMapper;

    public StudentApplicationService(ApplicationMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public StudentApplicationsResponse list(Long studentId) {
        if (mapper.countStudent(studentId) == 0) {
            throw new BusinessException(ErrorCode.STUDENT_NOT_FOUND);
        }
        List<StudentApplicationsResponse.Application> applications = mapper.selectByStudent(studentId).stream()
                .map(row -> new StudentApplicationsResponse.Application(
                        row.getApplicationId(), row.getCompanyName(), row.getTitle(), row.getStatus(),
                        toKst(row.getSubmittedAt()), countUserEdits(row.getApplicationId(), row.getDocument())))
                .toList();
        List<StudentApplicationsResponse.OtherItem> otherItems = mapper.selectLatestOtherItems(studentId).stream()
                .map(row -> new StudentApplicationsResponse.OtherItem(
                        row.getItemId(), row.getCompanyName(), row.getTitle(), row.getResponse(),
                        "PASS".equals(row.getResponse()) ? row.getPassReason() : null))
                .toList();
        return new StudentApplicationsResponse(applications, otherItems);
    }

    private int countUserEdits(Long applicationId, String document) {
        if (document == null || document.isBlank()) {
            return 0;
        }
        try {
            return countUserSource(objectMapper.readTree(document));
        } catch (JsonProcessingException e) {
            // 서류 JSON 이 깨져도 목록은 보여 준다
            log.warn("지원서 {}: 서류 JSON 을 읽지 못해 직접 수정 수를 0으로 표시", applicationId);
            return 0;
        }
    }

    /** source 가 USER 인 객체 수 (evidence 아래는 건너뜀) */
    static int countUserSource(JsonNode node) {
        int count = 0;
        if (node.isObject()) {
            if ("USER".equals(node.path("source").asText(null))) {
                count++;
            }
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                if (!"evidence".equals(field.getKey())) {
                    count += countUserSource(field.getValue());
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                count += countUserSource(child);
            }
        }
        return count;
    }

    private static OffsetDateTime toKst(LocalDateTime value) {
        return value == null ? null : value.atZone(KST).toOffsetDateTime();
    }
}
