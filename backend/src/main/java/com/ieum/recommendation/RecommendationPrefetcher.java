// 주인: BE-A
package com.ieum.recommendation;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 공고 배치가 끝난 뒤 수료생 추천을 미리 만든다 (S3).
 * AI 리랭커가 1명당 30~60초 걸려 첫 M1 방문이 느리지 않게 하려는 것. 배치 응답을 막지 않게 백그라운드 한 줄로 돈다.
 * 대상은 RecommendationService.prefetchIfNeeded 기준 (추천 없음 · 0건 · 공고가 모두 꺼짐).
 */
@Component
public class RecommendationPrefetcher {

    private static final Logger log = LoggerFactory.getLogger(RecommendationPrefetcher.class);

    private final RecommendationService recommendationService;
    private final RecommendationMapper mapper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "recommendation-prefetch");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);

    public RecommendationPrefetcher(RecommendationService recommendationService, RecommendationMapper mapper) {
        this.recommendationService = recommendationService;
        this.mapper = mapper;
    }

    /** 이미 돌고 있으면 건너뛴다 */
    public void prefetchAllAsync() {
        if (!running.compareAndSet(false, true)) {
            log.info("추천 미리 만들기가 이미 실행 중이라 건너뜀");
            return;
        }
        executor.submit(() -> {
            try {
                prefetchAll();
            } finally {
                running.set(false);
            }
        });
    }

    private void prefetchAll() {
        List<Long> studentIds = mapper.selectStudentIds();
        int made = 0;
        int failed = 0;
        for (Long studentId : studentIds) {
            try {
                if (recommendationService.prefetchIfNeeded(studentId)) {
                    made++;
                }
            } catch (RuntimeException e) {
                // 한 명이 실패해도 나머지는 계속. 실패한 수료생은 M1 방문 때 다시 만든다
                log.warn("수료생 {} 추천 미리 만들기 실패: {}", studentId, e.getMessage());
                failed++;
            }
        }
        log.info("추천 미리 만들기 완료: 수료생 {} · 새로 만듦 {} · 실패 {}", studentIds.size(), made, failed);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
