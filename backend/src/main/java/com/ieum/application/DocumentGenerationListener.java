// 주인: BE-A
package com.ieum.application;

import com.ieum.recommendation.RecommendationRespondedEvent;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 지원(APPLY) 응답이 저장 확정되면 서류 생성을 백그라운드로 시작한다 (BE-A-07, S7).
 * AI 서류 생성은 20~40초 걸려 응답 API 를 막지 않게 별도 스레드에서 돈다. 동시에 2건까지, 나머지는 줄을 선다.
 * 실패 서류 다시 생성(POST /api/applications/{id}/regenerate)도 start 로 같은 줄에 넣는다.
 * 서버가 생성 도중 꺼지면 그 지원서는 GENERATING 으로 남고, 다시 켜질 때 DocumentGenerationRecovery 가 FAILED 로 바꾼다.
 */
@Component
public class DocumentGenerationListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentGenerationListener.class);
    private static final int THREADS = 2;

    private final DocumentGenerationService generationService;
    private final AtomicInteger threadNo = new AtomicInteger();
    private final Set<Long> running = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newFixedThreadPool(THREADS, r -> {
        Thread t = new Thread(r, "document-draft-" + threadNo.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    public DocumentGenerationListener(DocumentGenerationService generationService) {
        this.generationService = generationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResponded(RecommendationRespondedEvent event) {
        if (!"APPLY".equals(event.response()) || event.applicationId() == null) {
            return;
        }
        start(event.applicationId());
    }

    /** 생성을 줄 세운다. 이 서버에서 이미 줄 서 있거나 도는 지원서면 다시 넣지 않고 false */
    public boolean start(Long applicationId) {
        if (!running.add(applicationId)) {
            log.info("지원서 {}: 이미 서류 생성 중이라 다시 시작하지 않음", applicationId);
            return false;
        }
        try {
            executor.submit(() -> {
                try {
                    generationService.generate(applicationId);
                } finally {
                    running.remove(applicationId);
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            // 서버 종료 중. GENERATING 으로 남은 지원서는 다음 기동 때 DocumentGenerationRecovery 가 FAILED 로 바꾼다
            running.remove(applicationId);
            log.warn("지원서 {}: 서버 종료 중이라 서류 생성을 시작하지 못함", applicationId);
            return false;
        }
    }

    /** 이 서버에서 줄 서 있거나 생성 중인 지원서 */
    public boolean isRunning(Long applicationId) {
        return running.contains(applicationId);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
