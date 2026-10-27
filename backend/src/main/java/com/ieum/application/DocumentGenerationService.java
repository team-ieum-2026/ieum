// 주인: BE-A
package com.ieum.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ieum.ai.AiClient;
import com.ieum.profile.ProfileService;
import com.ieum.profile.StudentProfileResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 지원서 서류 생성 (BE-A-07, S7). DocumentGenerationListener 가 백그라운드 스레드에서 부른다.
 * GENERATING 인 지원서만 처리한다: AI POST /ai/document/draft → application_document 저장 + DRAFT. 실패하면 FAILED.
 * AI 요청 형식은 ai/schemas/draft.py (student 는 프로필 추출과 같은 등록 자료 + 프로젝트 README · 역할 기록).
 */
@Service
public class DocumentGenerationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentGenerationService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    static final String GENERATING = "GENERATING";
    static final String DRAFT = "DRAFT";
    static final String FAILED = "FAILED";

    private final ApplicationMapper mapper;
    private final ProfileService profileService;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public DocumentGenerationService(ApplicationMapper mapper, ProfileService profileService, AiClient aiClient,
                                     ObjectMapper objectMapper, TransactionTemplate tx) {
        this.mapper = mapper;
        this.profileService = profileService;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.tx = tx;
    }

    /** 서류를 만들어 DRAFT 로 바꾼다. 어떤 이유로든 실패하면 FAILED. 예외를 밖으로 던지지 않는다 */
    public void generate(Long applicationId) {
        ApplicationRows.DraftTarget target = mapper.selectDraftTarget(applicationId);
        if (target == null || !GENERATING.equals(target.getStatus())) {
            log.info("지원서 {}: 생성 중이 아니라 서류 생성을 건너뜀", applicationId);
            return;
        }
        long startedAt = System.currentTimeMillis();
        try {
            JsonNode data = aiClient.draftDocument(buildRequest(target));
            JsonNode document = data == null ? null : data.get("document");
            if (document == null || !document.isObject() || !document.path("sections").isArray()) {
                throw new IllegalStateException("AI 응답에 서류 JSON(document.sections)이 없어요");
            }
            String documentJson = objectMapper.writeValueAsString(document);
            Boolean saved = tx.execute(status -> {
                // 저장 직전에 상태를 다시 확인한다. 그새 GENERATING 이 아니면 서류도 저장하지 않는다
                if (mapper.updateStatus(applicationId, GENERATING, DRAFT) == 0) {
                    status.setRollbackOnly();
                    return false;
                }
                mapper.upsertDocument(applicationId, documentJson);
                return true;
            });
            log.info("지원서 {}: 서류 생성 {} ({}ms)", applicationId,
                    Boolean.TRUE.equals(saved) ? "완료 → DRAFT" : "결과 버림 (이미 다른 상태)",
                    System.currentTimeMillis() - startedAt);
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("지원서 {}: 서류 생성 실패 → FAILED ({})", applicationId, e.getMessage());
            mapper.updateStatus(applicationId, GENERATING, FAILED);
        }
    }

    /** { applicationId, student, profile, job, companyInfo } */
    ObjectNode buildRequest(ApplicationRows.DraftTarget target) {
        StudentProfileResponse registered = profileService.getProfile(target.getStudentId());

        ObjectNode student = objectMapper.valueToTree(registered);
        student.remove("competencyProfile");
        student.remove("profileUpdatedAt");
        ArrayNode projects = student.putArray("projects");
        for (ApplicationRows.DraftProject project : mapper.selectDraftProjects(target.getStudentId())) {
            ObjectNode node = projects.addObject();
            node.put("itemId", project.getItemId());
            node.put("title", project.getTitle());
            node.put("periodText", project.getPeriodText());
            node.put("teamSize", project.getTeamSize());
            node.set("techStack", readJson(project.getTechStack()));
            node.put("portfolioFileName", project.getPortfolioFileName());
            node.put("includeInApply", project.isIncludeInApply());
            node.put("roleNote", project.getRoleNote());
            node.put("readme", project.getReadme());
            node.put("source", project.getSource());
        }

        JsonNode profile = registered.competencyProfile();
        boolean hasProfile = profile != null && profile.isObject() && !profile.isEmpty();

        ObjectNode job = objectMapper.createObjectNode();
        job.put("jobPostingId", target.getJobPostingId());
        job.put("companyName", target.getCompanyName());
        job.put("title", target.getTitle());
        job.put("jobCategory", target.getJobCategory());
        job.put("location", target.getLocation());
        job.put("employmentType", target.getEmploymentType());
        job.put("career", target.getCareer());
        job.put("education", target.getEducation());
        job.put("description", target.getDescription());

        ObjectNode request = objectMapper.createObjectNode();
        request.put("applicationId", target.getApplicationId());
        request.set("student", student);
        request.set("profile", hasProfile ? profile : NullNode.getInstance());
        request.set("job", job);
        request.set("companyInfo", companyInfo(mapper.selectCompanyInfo(target.getCompanyName())));
        return request;
    }

    /** company_info 한 줄을 AI 형식 { companyInfoId, companyName, status, talentKeywords, …, collectedAt } 으로 편다 */
    private JsonNode companyInfo(ApplicationRows.CompanyInfo row) {
        if (row == null) {
            return NullNode.getInstance();
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.put("companyInfoId", row.getId());
        node.put("companyName", row.getCompanyName());
        node.put("status", row.getStatus());
        JsonNode info = readJson(row.getInfo());
        if (info.isObject()) {
            info.properties().forEach(field -> node.set(field.getKey(), field.getValue()));
        }
        node.put("collectedAt", toKst(row.getCollectedAt()));
        return node;
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return NullNode.getInstance();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            log.warn("서류 생성 요청: JSON 컬럼을 읽지 못해 null 로 보냄 ({})", e.getMessage());
            return NullNode.getInstance();
        }
    }

    private static String toKst(LocalDateTime value) {
        return value == null ? null : value.atZone(KST).toOffsetDateTime().toString();
    }
}
