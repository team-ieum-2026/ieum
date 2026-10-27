// 주인: BE-A
package com.ieum.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ieum.ai.AiClient;
import com.ieum.recommendation.RecommendationPrefetcher;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *  3) 새로 들어오거나 content_hash 가 바뀌거나 다시 켜진 공고 + 꺼진 공고 id 를 AI POST /ai/matching/index (20건씩)
 *  4) 첫 성공 시 DEV_SEED 공고를 끔
 *  5) API 실패면 그날은 건너뛰고 기존 데이터 유지
 *  6) 끝나면 추천이 없거나 비어 있는 수료생의 추천을 백그라운드로 미리 만든다
 * 전체 재색인(reindex): 배치는 바뀐 공고만 보내므로, AI 색인이 비었을 때(AI 서버 처음 연결 · 색인 파일 삭제)
 * 활성 공고 전체 + 꺼진 공고 id 를 다시 보낸다.
 */
@Service
public class JobSyncService {

    private static final Logger log = LoggerFactory.getLogger(JobSyncService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int INDEX_BATCH_SIZE = 20;

    private final SeoulJobClient seoulJobClient;
    private final SeoulJobProperties props;
    private final JobPostingMapper jobPostingMapper;
    private final AiClient aiClient;
    private final RecommendationPrefetcher recommendationPrefetcher;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public JobSyncService(SeoulJobClient seoulJobClient, SeoulJobProperties props, JobPostingMapper jobPostingMapper,
                          AiClient aiClient, RecommendationPrefetcher recommendationPrefetcher,
                          ObjectMapper objectMapper, TransactionTemplate tx) {
        this.seoulJobClient = seoulJobClient;
        this.props = props;
        this.jobPostingMapper = jobPostingMapper;
        this.aiClient = aiClient;
        this.recommendationPrefetcher = recommendationPrefetcher;
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

    /** 활성 공고 전체를 AI 색인에 다시 보낸다. AI는 contentHash 가 같으면 건너뛰므로 여러 번 불러도 된다 */
    public JobReindexResult reindex() {
        String now = OffsetDateTime.now(KST).truncatedTo(ChronoUnit.SECONDS).toString();
        if (!running.compareAndSet(false, true)) {
            return JobReindexResult.skipped("공고 동기화나 재색인이 실행 중이에요.", now);
        }
        try {
            List<JobPosting> active = jobPostingMapper.selectActiveForIndex();
            List<Long> inactiveIds = jobPostingMapper.selectInactiveIds();
            if (active.isEmpty()) {
                return JobReindexResult.skipped("활성 공고가 없어요. 공고 배치(sync)를 먼저 실행해 주세요.", now);
            }
            JsonNode indexResult = indexInBatches(active, inactiveIds);
            log.info("AI 전체 재색인 완료: 요청 {} · 제거 요청 {} · 결과 {}", active.size(), inactiveIds.size(), indexResult);
            recommendationPrefetcher.prefetchAllAsync();
            return new JobReindexResult("DONE", "AI 색인을 다시 만들었어요.", active.size(), inactiveIds.size(),
                    indexResult, now);
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

        // 2) IT 필터 + 변환 + 지역 필터(서울 · 경기 · 인천). 같은 external_id 가 두 번 오면 마지막 것만
        LocalDate today = LocalDate.now(KST);
        Map<String, JobPosting> parsed = new LinkedHashMap<>();
        int outOfRegion = 0;
        for (JsonNode row : rows) {
            if (!SeoulJobParser.isItJob(row, props.itJobCodes())) {
                continue;
            }
            JobPosting p = SeoulJobParser.parse(row, today);
            if (!SeoulJobParser.isAllowedRegion(p.getLocation(), props.allowedRegions())) {
                outOfRegion++;
                continue;
            }
            parsed.put(p.getExternalId(), p);
        }
        if (parsed.isEmpty()) {
            return JobSyncResult.skipped("IT 직종 · 수도권 공고가 0건이라 기존 공고를 그대로 써요. "
                    + "it-job-codes · allowed-regions를 확인해 주세요.", now);
        }

        // 3) DB 반영 (한 트랜잭션)
        SyncCounts counts = tx.execute(status -> upsertAll(parsed, today));

        // 4) AI 색인 갱신: 새로 들어오거나 본문이 바뀌거나 다시 켜진 활성 공고 + 꺼진 공고 id. 실패해도 DB 반영은 유지
        List<JobPosting> toIndex = counts.toIndex();
        JsonNode indexResult = toIndex.isEmpty() && counts.removedIds().isEmpty()
                ? null : indexInBatches(toIndex, counts.removedIds());

        log.info("공고 동기화 완료: 수집 {} · IT · 수도권 {} (지역 제외 {}) · 신규 {} · 변경 {} · 동일 {} · 비활성 {} · 색인 요청 {} · 색인 제거 {}",
                rows.size(), parsed.size(), outOfRegion, counts.inserted(), counts.updated(), counts.unchanged(),
                counts.deactivated(), toIndex.size(), counts.removedIds().size());
        recommendationPrefetcher.prefetchAllAsync();
        return new JobSyncResult("DONE", "공고 동기화를 마쳤어요.", rows.size(), parsed.size(),
                counts.inserted(), counts.updated(), counts.unchanged(), counts.deactivated(),
                toIndex.size(), indexResult, now);
    }

    /**
     * 임베딩 API 분당 한도 때문에 INDEX_BATCH_SIZE 건씩 나눠 보낸다. 꺼진 공고 id 는 첫 요청에만 싣는다.
     * 응답 { indexedCount, skippedCount, removedCount } 를 더하고, 실패한 묶음 수를 failedBatchCount 로 남긴다.
     */
    private JsonNode indexInBatches(List<JobPosting> jobs, List<Long> removedIds) {
        int indexed = 0;
        int skipped = 0;
        int removed = 0;
        int failed = 0;
        int batches = Math.max(1, (jobs.size() + INDEX_BATCH_SIZE - 1) / INDEX_BATCH_SIZE);
        for (int b = 0; b < batches; b++) {
            List<JobPosting> chunk = jobs.subList(Math.min(b * INDEX_BATCH_SIZE, jobs.size()),
                    Math.min((b + 1) * INDEX_BATCH_SIZE, jobs.size()));
            try {
                JsonNode res = aiClient.indexJobs(indexRequest(chunk, b == 0 ? removedIds : List.of()));
                indexed += res.path("indexedCount").asInt(0);
                skipped += res.path("skippedCount").asInt(0);
                removed += res.path("removedCount").asInt(0);
            } catch (RuntimeException e) {
                // 다음 배치에서 다시 보내지 않으니 POST /api/admin/jobs/reindex 로 재시도
                log.warn("AI 색인 호출 실패 ({}/{}번째 묶음): {}", b + 1, batches, e.getMessage());
                failed++;
            }
        }
        ObjectNode result = objectMapper.createObjectNode();
        result.put("indexedCount", indexed);
        result.put("skippedCount", skipped);
        result.put("removedCount", removed);
        result.put("failedBatchCount", failed);
        return result;
    }

    private SyncCounts upsertAll(Map<String, JobPosting> parsed, LocalDate today) {
        Set<Long> activeBefore = new HashSet<>(jobPostingMapper.selectActiveIds());
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
        parsed.values().forEach(p -> p.setId(after.get(p.getExternalId()).getId()));

        // 꺼진 공고는 AI 색인에서 지우고, 내용은 같아도 다시 켜진 공고는 다시 색인한다
        Set<Long> activeAfter = new HashSet<>(jobPostingMapper.selectActiveIds());
        List<Long> removedIds = activeBefore.stream().filter(id -> !activeAfter.contains(id)).sorted().toList();
        Set<JobPosting> toIndex = new LinkedHashSet<>(changed);
        parsed.values().stream().filter(p -> !activeBefore.contains(p.getId())).forEach(toIndex::add);
        List<JobPosting> activeToIndex = toIndex.stream().filter(p -> activeAfter.contains(p.getId())).toList();

        return new SyncCounts(inserted, updated, unchanged, deactivated, activeToIndex, removedIds);
    }

    /**
     * POST /ai/matching/index 요청 본문 (BE-A-03, docs/API.md 4장):
     * { "jobs": [ { jobPostingId, contentHash, companyName, title, jobCategory, location, career, education,
     * description } ], "removedJobPostingIds": [ … ] }
     */
    private JsonNode indexRequest(List<JobPosting> jobs, List<Long> removedIds) {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("removedJobPostingIds", objectMapper.valueToTree(removedIds));
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
            j.put("education", p.getEducation());
            j.put("description", p.getDescription());
        }
        return body;
    }

    private static Map<String, JobPosting> byExternalId(List<JobPosting> list) {
        return list.stream().collect(Collectors.toMap(JobPosting::getExternalId, Function.identity(), (a, b) -> a));
    }

    private record SyncCounts(int inserted, int updated, int unchanged, int deactivated,
                              List<JobPosting> toIndex, List<Long> removedIds) {
    }
}
