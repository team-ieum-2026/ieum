// 주인: BE-A
package com.ieum.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ieum.ai.AiClient;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공고 일 배치 (BE-A-02, S3) + 배치 뒤 AI 색인 갱신 (BE-A-03).
 * 규칙: docs/JOB_API.md "배치 규칙"
 *  1) 전체 페이지 수집 → IT 필터 → (source, external_id) upsert
 *  2) 이번 목록에 없거나 마감일이 지난 공고는 is_active = 0 (삭제하지 않음)
 *  3) 새로 들어오거나 content_hash 가 바뀐 공고만 AI POST /ai/matching/index
 *  4) 첫 성공 시 DEV_SEED 공고를 끔
 *  5) API 실패면 그날은 건너뛰고 기존 데이터 유지
 */
@Service
public class JobSyncService {

    private static final Logger log = LoggerFactory.getLogger(JobSyncService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SeoulJobClient seoulJobClient;
    private final SeoulJobProperties props;
    private final JobPostingMapper jobPostingMapper;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public JobSyncService(SeoulJobClient seoulJobClient, SeoulJobProperties props, JobPostingMapper jobPostingMapper,
                          AiClient aiClient, ObjectMapper objectMapper, TransactionTemplate tx) {
        this.seoulJobClient = seoulJobClient;
        this.props = props;
        this.jobPostingMapper = jobPostingMapper;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.tx = tx;
    }

    public JobSyncResult sync() {
        String now = OffsetDateTime.now(KST).truncatedTo(ChronoUnit.SECONDS).toString();
        if (!running.compareAndSet(false, true)) {
            return JobSyncResult.skipped("이미 공고 동기화가 실행 중이에요.", now);
        }
        try {
            return doSync(now);
        } finally {
            running.set(false);
        }
    }

    private JobSyncResult doSync(String now) {
        // 1) 수집 (DB 트랜잭션 밖)
        List<JsonNode> rows;
        try {
            rows = seoulJobClient.fetchAll();
        } catch (SeoulApiException e) {
            log.warn("공고 동기화 건너뜀: {}", e.getMessage());
            return JobSyncResult.skipped("공고 API 호출에 실패해 기존 공고를 그대로 써요. (" + e.getMessage() + ")", now);
        }
        if (rows.isEmpty()) {
            return JobSyncResult.skipped("공고 API가 0건을 돌려줘 기존 공고를 그대로 써요.", now);
        }

        // 2) IT 필터 + 변환. 같은 external_id 가 두 번 오면 마지막 것만
        LocalDate today = LocalDate.now(KST);
        Map<String, JobPosting> parsed = new LinkedHashMap<>();
        for (JsonNode row : rows) {
            if (SeoulJobParser.isItJob(row, props.itJobCodes())) {
                JobPosting p = SeoulJobParser.parse(row, today);
                parsed.put(p.getExternalId(), p);
            }
        }
        if (parsed.isEmpty()) {
            return JobSyncResult.skipped("IT 직종 공고가 0건이라 기존 공고를 그대로 써요. it-job-codes를 확인해 주세요.", now);
        }

        // 3) DB 반영 (한 트랜잭션)
        SyncCounts counts = tx.execute(status -> upsertAll(parsed, today));

        // 4) AI 색인 갱신: 새로 들어오거나 본문이 바뀐 활성 공고만. 실패해도 DB 반영은 유지
        JsonNode indexResult = null;
        List<JobPosting> toIndex = counts.changed().stream().filter(JobPosting::isActive).toList();
        if (!toIndex.isEmpty()) {
            try {
                indexResult = aiClient.indexJobs(indexRequest(toIndex));
            } catch (RuntimeException e) {
                log.warn("AI 색인 호출 실패 (다음 배치에서 다시 시도되지 않으니 수동 sync로 재시도): {}", e.getMessage());
                ObjectNode err = objectMapper.createObjectNode();
                err.put("error", "AI_SERVER_ERROR");
                indexResult = err;
            }
        }

        log.info("공고 동기화 완료: 수집 {} · IT {} · 신규 {} · 변경 {} · 동일 {} · 비활성 {} · 색인 요청 {}",
                rows.size(), parsed.size(), counts.inserted(), counts.updated(), counts.unchanged(),
                counts.deactivated(), toIndex.size());
        return new JobSyncResult("DONE", "공고 동기화를 마쳤어요.", rows.size(), parsed.size(),
                counts.inserted(), counts.updated(), counts.unchanged(), counts.deactivated(),
                toIndex.size(), indexResult, now);
    }

    private SyncCounts upsertAll(Map<String, JobPosting> parsed, LocalDate today) {
        Map<String, JobPosting> before = byExternalId(jobPostingMapper.selectKeysBySource(SeoulJobParser.SOURCE));

        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        List<JobPosting> changed = new ArrayList<>();
        for (JobPosting p : parsed.values()) {
            JobPosting old = before.get(p.getExternalId());
            jobPostingMapper.upsert(p);
            if (old == null) {
                inserted++;
                changed.add(p);
            } else if (!p.getContentHash().equals(old.getContentHash())) {
                updated++;
                changed.add(p);
            } else {
                unchanged++;
            }
        }

        int deactivated = jobPostingMapper.deactivateMissing(SeoulJobParser.SOURCE, new ArrayList<>(parsed.keySet()))
                + jobPostingMapper.deactivateExpired(SeoulJobParser.SOURCE, today);
        deactivated += jobPostingMapper.deactivateDevSeed();

        // 색인 요청에 쓸 id 채우기
        Map<String, JobPosting> after = byExternalId(jobPostingMapper.selectKeysBySource(SeoulJobParser.SOURCE));
        changed.forEach(p -> p.setId(after.get(p.getExternalId()).getId()));

        return new SyncCounts(inserted, updated, unchanged, deactivated, changed);
    }

    /**
     * POST /ai/matching/index 요청 본문 (BE-A-03).
     * 형식은 docs/API.md 4장에 아직 없어 BE-A 초안: { "jobs": [ { jobPostingId, contentHash, companyName, title,
     * jobCategory, location, career, description } ] } · AI-B와 확정 필요
     */
    private JsonNode indexRequest(List<JobPosting> jobs) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode arr = body.putArray("jobs");
        for (JobPosting p : jobs) {
            ObjectNode j = arr.addObject();
            j.put("jobPostingId", p.getId());
            j.put("contentHash", p.getContentHash());
            j.put("companyName", p.getCompanyName());
            j.put("title", p.getTitle());
            j.put("jobCategory", p.getJobCategory());
            j.put("location", p.getLocation());
            j.put("career", p.getCareer());
            j.put("description", p.getDescription());
        }
        return body;
    }

    private static Map<String, JobPosting> byExternalId(List<JobPosting> list) {
        return list.stream().collect(Collectors.toMap(JobPosting::getExternalId, Function.identity(), (a, b) -> a));
    }

    private record SyncCounts(int inserted, int updated, int unchanged, int deactivated, List<JobPosting> changed) {
    }
}
