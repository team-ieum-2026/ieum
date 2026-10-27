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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추천 공고 하나 (BE-A-05 · 06, M2 · M3).
 * - 공고 상세 + 추천 근거 전체. 근거의 label · quote 는 수료생 프로필(BE-B 서비스)에서 채운다
 * - 공고 요약(summary)은 LLM 없이 본문 [내용]의 앞 2줄로 만든다
 * - 지원 · 패스 응답: 한 번만 받는다. 지원이면 application(GENERATING)을 만들고,
 *   저장 뒤 RecommendationRespondedEvent 로 AI 서류 생성(BE-A-07) · 상태 재계산(BE-B-06)에 알린다
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

    private static final Set<String> PASS_REASONS = Set.of(
            "ALREADY_EMPLOYED", "JOB_MISMATCH", "CONDITION_MISMATCH", "SKILL_GAP", "TAKING_BREAK", "OTHER");
    private static final int PASS_NOTE_MAX = 1000;

    private final RecommendationMapper mapper;
    private final ProfileService profileService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    public RecommendationItemService(RecommendationMapper mapper, ProfileService profileService,
                                     ObjectMapper objectMapper, ApplicationEventPublisher events) {
        this.mapper = mapper;
        this.profileService = profileService;
        this.objectMapper = objectMapper;
        this.events = events;
    }

    /**
     * 지원(APPLY) · 패스(PASS) 저장. 도움 요청은 BE-B POST /api/help-requests 를 쓴다.
     * PASS 인데 사유가 없으면 400 PASS_REASON_REQUIRED, 이미 응답한 항목이면 409 ALREADY_RESPONDED.
     */
    @Transactional
    public ItemResponseResult respond(Long itemId, ItemResponseRequest request) {
        String response = request == null ? null : request.response();
        if (!"APPLY".equals(response) && !"PASS".equals(response)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "response는 APPLY 또는 PASS여야 해요.");
        }
        String passReason = null;
        String passNote = null;
        if ("PASS".equals(response)) {
            passReason = blankToNull(request.passReason());
            if (passReason == null) {
                throw new BusinessException(ErrorCode.PASS_REASON_REQUIRED);
            }
            if (!PASS_REASONS.contains(passReason)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "알 수 없는 패스 사유예요.");
            }
            passNote = blankToNull(request.passNote());
            if (passNote != null && passNote.length() > PASS_NOTE_MAX) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "기타 입력은 1,000자까지 쓸 수 있어요.");
            }
        }

        RecommendationRows.ItemOwner item = mapper.selectItemOwner(itemId);
        if (item == null) {
            throw new BusinessException(ErrorCode.RECOMMENDATION_ITEM_NOT_FOUND);
        }
        // 응답이 NONE 일 때만 바뀐다. 두 번 눌러도 지원서가 두 개 생기지 않게 조건부 UPDATE 로 막는다
        if (mapper.updateItemResponse(itemId, response, passReason, passNote) == 0) {
            throw new BusinessException(ErrorCode.ALREADY_RESPONDED);
        }
        mapper.touchLastResponse(item.getStudentId());

        Long applicationId = null;
        if ("APPLY".equals(response)) {
            var application = new RecommendationRows.NewApplication(item.getStudentId(), itemId, item.getJobPostingId());
            mapper.insertApplication(application);
            applicationId = application.getId();
        }
        events.publishEvent(new RecommendationRespondedEvent(
                item.getStudentId(), itemId, item.getJobPostingId(), response, applicationId));
        return new ItemResponseResult(itemId, response, applicationId);
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
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
