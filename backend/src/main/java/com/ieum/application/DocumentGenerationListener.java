// 주인: BE-A
package com.ieum.application;

import com.ieum.recommendation.RecommendationRespondedEvent;
import jakarta.annotation.PreDestroy;
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
 * 서버가 생성 도중 꺼지면 그 지원서는 GENERATING 으로 남는다.
 */
@Component
public class DocumentGenerationListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentGenerationListener.class);
    private static final int THREADS = 2;

    private final DocumentGenerationService generationService;
    private final AtomicInteger threadNo = new AtomicInteger();
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
        try {
            executor.submit(() -> generationService.generate(event.applicationId()));
        } catch (RejectedExecutionException e) {
            // 서버 종료 중
            log.warn("지원서 {}: 서버 종료 중이라 서류 생성을 시작하지 못함", event.applicationId());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
