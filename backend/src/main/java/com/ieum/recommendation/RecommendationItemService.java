// 주인: BE-A
package com.ieum.recommendation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import com.ieum.profile.ProfileService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 추천 공고 하나 (BE-A-05, M2).
 * - 공고 상세 + 추천 근거 전체. 근거의 label · quote 는 수료생 프로필(BE-B 서비스)에서 채운다
 * - 공고 요약(summary)은 LLM 없이 본문 [내용]의 앞 2줄로 만든다
 */
@Service
public class RecommendationItemService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationItemService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final Map<String, String> SOURCE_LABEL = Map.of(
            "SEOUL_API", "서울 열린데이터광장 · 서울시 일자리포털 채용정보",
            "DEV_SEED", "개발용 임시 공고",
            "EVAL_SEED", "평가용 모의 공고");

    /** SeoulJobParser.description 이 붙이는 칸 이름. [내용] 칸이 어디서 끝나는지 찾는 데 쓴다 */
    private static final Set<String> DESCRIPTION_LABELS = Set.of(
            "[직무]", "[내용]", "[필요 역량]", "[자격증]", "[전공]", "[우대 조건]", "[근무 시간]", "[전형]");
    private static final int SUMMARY_LINES = 2;
    private static final int SUMMARY_MAX = 60;
    private static final Pattern LEADING_BULLET = Pattern.compile("^[\\s\\-*•·○◦▶►■□※?？]+");
    private static final Pattern HEADER_LINE = Pattern.compile("^[\\[<(【].*[\\]>)】]$");

    private final RecommendationMapper mapper;
    private final ProfileService profileService;
    private final ObjectMapper objectMapper;

    public RecommendationItemService(RecommendationMapper mapper, ProfileService profileService,
                                     ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.profileService = profileService;
        this.objectMapper = objectMapper;
    }

    public RecommendationItemDetailResponse getDetail(Long itemId) {
        RecommendationRows.ItemDetail row = mapper.selectItemDetail(itemId);
        if (row == null) {
            throw new BusinessException(ErrorCode.RECOMMENDATION_ITEM_NOT_FOUND);
        }
        var resolver = new RecommendationEvidenceResolver(profileService.getProfile(row.getStudentId()));
        LocalDate today = LocalDate.now(KST);
        var job = new RecommendationItemDetailResponse.Job(
                row.getJobPostingId(), row.getCompanyName(), row.getTitle(), row.getJobCategory(),
                row.getLocation(), row.getWorkAddress(), row.getEmploymentType(), row.getCareer(),
                row.getEducation(), row.getSalaryText(), row.getDeadlineOn(), row.isUntilFilled(),
                row.getDeadlineOn() == null ? null : (int) ChronoUnit.DAYS.between(today, row.getDeadlineOn()),
                row.getPostedOn(), row.getDescription(), row.getHomepageUrl(), row.getSource(),
                SOURCE_LABEL.getOrDefault(row.getSource(), row.getSource()));
        return new RecommendationItemDetailResponse(row.getItemId(), row.getRankNo(), row.getResponse(), job,
                summary(row.getDescription()), resolver.reasons(readReasons(row.getReasons())));
    }

    /** 본문 [내용] 칸에서 제목 · 빈 줄 · 링크를 빼고 앞 2줄. 길면 60자에서 자른다 */
    static List<String> summary(String description) {
        List<String> lines = new ArrayList<>();
        if (description == null) {
            return lines;
        }
        boolean inContent = false;
        for (String raw : description.split("\n")) {
            String line = raw.strip();
            String label = DESCRIPTION_LABELS.stream().filter(line::startsWith).findFirst().orElse(null);
            if (label != null) {
                inContent = "[내용]".equals(label);
                line = line.substring(label.length()).strip();
            }
            if (!inContent) {
                continue;
            }
            line = LEADING_BULLET.matcher(line).replaceFirst("").strip();
            if (line.length() < 6 || HEADER_LINE.matcher(line).matches() || line.contains("http")) {
                continue;
            }
            lines.add(line.length() <= SUMMARY_MAX ? line : line.substring(0, SUMMARY_MAX) + "…");
            if (lines.size() == SUMMARY_LINES) {
                break;
            }
        }
        return lines;
    }

    private JsonNode readReasons(String reasonsJson) {
        if (reasonsJson == null || reasonsJson.isBlank()) {
            return objectMapper.createArrayNode();
        }
        try {
            return objectMapper.readTree(reasonsJson);
        } catch (JsonProcessingException e) {
            log.warn("추천 근거 JSON 파싱 실패: {}", e.getMessage());
            return objectMapper.createArrayNode();
        }
    }
}
