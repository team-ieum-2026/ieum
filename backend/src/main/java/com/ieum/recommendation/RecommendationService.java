// 주인: BE-A
package com.ieum.recommendation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ieum.ai.AiClient;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import com.ieum.profile.ProfileService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * M1 맞춤 공고 (BE-A-04, S3 · S4).
 * - 현재 추천: 가장 최근 묶음에 공고가 있으면 그대로, 없으면 AI 매칭을 불러 새로 만든다 (trigger VISIT)
 * - 새로 추천받기: 이미 응답한 공고를 빼고 AI 매칭을 다시 부른다 (trigger REFRESH)
 * AI가 준 공고 중 DB에 없거나 꺼진 공고는 버리고, 남는 게 없으면 저장하지 않고 items: [] 로 응답한다.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String BASIS = "PROFILE_LATEST";
    private static final int TOP_K = 3;

    private final RecommendationMapper mapper;
    private final ProfileService profileService;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public RecommendationService(RecommendationMapper mapper, ProfileService profileService, AiClient aiClient,
                                 ObjectMapper objectMapper, TransactionTemplate tx) {
        this.mapper = mapper;
        this.profileService = profileService;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.tx = tx;
    }

    public RecommendationResponse getCurrent(Long studentId) {
        RecommendationRows.Header latest = mapper.selectLatest(studentId);
        if (latest != null && latest.getItemCount() > 0) {
            return toResponse(latest.getId(), latest.getCreatedAt());
        }
        return generate(studentId, "VISIT");
    }

    public RecommendationResponse refresh(Long studentId) {
        return generate(studentId, "REFRESH");
    }

    private RecommendationResponse generate(Long studentId, String triggerType) {
        // 프로필은 BE-B 서비스로 읽는다. 없는 수료생이면 여기서 STUDENT_NOT_FOUND
        JsonNode profile = profileService.getProfile(studentId).competencyProfile();
        List<Long> excluded = mapper.selectRespondedJobIds(studentId);

        ObjectNode request = objectMapper.createObjectNode();
        request.put("studentId", studentId);
        request.set("profile", profile == null || profile.isNull() ? objectMapper.createObjectNode() : profile);
        request.set("excludeJobPostingIds", objectMapper.valueToTree(excluded));
        request.put("topK", TOP_K);

        JsonNode aiData = aiClient.recommend(request);
        Map<Long, JsonNode> picked = pickValidItems(aiData, new HashSet<>(excluded));
        if (picked.isEmpty()) {
            log.info("수료생 {}: 맞는 공고 없음 (AI 응답 {}건)", studentId, aiData.path("items").size());
            return new RecommendationResponse(null, now(), BASIS, List.of());
        }

        Long recommendationId = tx.execute(status -> save(studentId, triggerType, picked));
        RecommendationRows.Header saved = mapper.selectLatest(studentId);
        return toResponse(recommendationId, saved.getCreatedAt());
    }

    /** AI 응답 items 를 rank 순으로 보고, 실제로 있는 활성 공고 · 제외 목록 밖 · 중복 아닌 것만 최대 3개 */
    private Map<Long, JsonNode> pickValidItems(JsonNode aiData, Set<Long> excluded) {
        List<JsonNode> items = new ArrayList<>();
        aiData.path("items").forEach(items::add);
        items.sort((a, b) -> Integer.compare(a.path("rank").asInt(99), b.path("rank").asInt(99)));

        List<Long> ids = items.stream().map(i -> i.path("jobPostingId").asLong(0)).filter(id -> id > 0).toList();
        Set<Long> active = ids.isEmpty() ? Set.of() : new HashSet<>(mapper.selectActiveJobIds(ids));

        Map<Long, JsonNode> picked = new LinkedHashMap<>();
        for (JsonNode item : items) {
            long id = item.path("jobPostingId").asLong(0);
            if (active.contains(id) && !excluded.contains(id) && !picked.containsKey(id)) {
                picked.put(id, item.path("reasons"));
            }
            if (picked.size() == TOP_K) {
                break;
            }
        }
        return picked;
    }

    private Long save(Long studentId, String triggerType, Map<Long, JsonNode> picked) {
        var rec = new RecommendationRows.NewRecommendation(studentId, triggerType);
        mapper.insertRecommendation(rec);
        List<RecommendationRows.NewItem> rows = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<Long, JsonNode> e : picked.entrySet()) {
            rows.add(new RecommendationRows.NewItem(rec.getId(), e.getKey(), rank++, toJson(e.getValue())));
        }
        mapper.insertItems(rows);
        return rec.getId();
    }

    private RecommendationResponse toResponse(Long recommendationId, LocalDateTime createdAt) {
        LocalDate today = LocalDate.now(KST);
        List<RecommendationResponse.Item> items = mapper.selectItems(recommendationId).stream()
                .map(row -> new RecommendationResponse.Item(
                        row.getItemId(),
                        row.getRankNo(),
                        row.getResponse(),
                        new RecommendationResponse.Job(
                                row.getJobPostingId(), row.getCompanyName(), row.getTitle(), row.getLocation(),
                                row.getEmploymentType(), row.getCareer(), row.getDeadlineOn(), row.isUntilFilled(),
                                row.getDeadlineOn() == null ? null
                                        : (int) ChronoUnit.DAYS.between(today, row.getDeadlineOn())),
                        reasonTexts(row.getReasons())))
                .toList();
        return new RecommendationResponse(recommendationId, toKst(createdAt), BASIS, items);
    }

    /** reasons JSON 에서 카드용 문장(text)만 */
    private List<String> reasonTexts(String reasonsJson) {
        if (reasonsJson == null || reasonsJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> texts = new ArrayList<>();
            objectMapper.readTree(reasonsJson).forEach(r -> {
                String text = r.path("text").asText("");
                if (!text.isBlank()) {
                    texts.add(text);
                }
            });
            return texts;
        } catch (JsonProcessingException e) {
            log.warn("추천 근거 JSON 파싱 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private String toJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node == null || node.isMissingNode() ? objectMapper.createArrayNode() : node);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private static OffsetDateTime toKst(LocalDateTime value) {
        return value == null ? null : value.atZone(KST).toOffsetDateTime();
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(KST).truncatedTo(ChronoUnit.SECONDS);
    }
}
