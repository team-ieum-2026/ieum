// 주인: BE-A
package com.ieum.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 중단된 서류 생성 복구. 서버가 생성 도중 꺼지면 지원서가 GENERATING 으로 남아 다시 생성할 수 없다.
 * 서버가 켜질 때와 그 뒤 1분마다, STALE_MINUTES 분 넘게 GENERATING 인데 이 서버에서 돌고 있지 않은 지원서를 FAILED 로 바꾼다.
 * 수료생은 FAILED 서류를 POST /api/applications/{id}/regenerate 로 다시 만든다.
 * AI 서류 생성 읽기 제한이 2분(RestAiClient)이라 5분이 지나도 GENERATING 이면 정상 생성이 아니다.
 * 같은 DB 를 쓰는 다른 서버(테스트 등)의 정상 생성은 5분 안에 끝나므로 건드리지 않는다.
 */
@Component
public class DocumentGenerationRecovery {

    private static final Logger log = LoggerFactory.getLogger(DocumentGenerationRecovery.class);
    static final int STALE_MINUTES = 5;

    private final ApplicationMapper mapper;
    private final DocumentGenerationListener generation;

    public DocumentGenerationRecovery(ApplicationMapper mapper, DocumentGenerationListener generation) {
        this.mapper = mapper;
        this.generation = generation;
    }

    /** 바꾼 건수. 예외를 밖으로 던지지 않는다 (DB 가 잠시 안 돼도 다음 주기에 다시 본다) */
    @Scheduled(fixedDelayString = "${app.document.recover-delay-ms:60000}")
    public int recover() {
        try {
            int failed = 0;
            for (Long applicationId : mapper.selectStaleGenerating(STALE_MINUTES)) {
                if (generation.isRunning(applicationId)) {
                    continue;
                }
                if (mapper.failStaleGenerating(applicationId, STALE_MINUTES) > 0) {
                    failed++;
                    log.warn("지원서 {}: 중단된 서류 생성 → FAILED (다시 생성 가능)", applicationId);
                }
            }
            return failed;
        } catch (RuntimeException e) {
            log.warn("중단된 서류 생성 복구 실패 ({})", e.getMessage());
            return 0;
        }
    }
}
