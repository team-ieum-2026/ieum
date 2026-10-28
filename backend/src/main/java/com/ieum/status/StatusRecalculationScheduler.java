// 주인: BE-B
package com.ieum.status;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 서울 시각 자정마다 무응답 기간과 저장된 추천 0건 신호를 재확인한다. */
@Component
public class StatusRecalculationScheduler {

    private static final Logger log = LoggerFactory.getLogger(StatusRecalculationScheduler.class);

    private final StatusMapper mapper;
    private final StatusService service;

    public StatusRecalculationScheduler(StatusMapper mapper, StatusService service) {
        this.mapper = mapper;
        this.service = service;
    }

    @Scheduled(cron = "${app.status.recalculate-cron:0 0 0 * * *}", zone = "Asia/Seoul")
    public void recalculateAll() {
        List<Long> studentIds;
        try {
            studentIds = mapper.findStudentIds();
        } catch (RuntimeException exception) {
            log.warn("일일 상태 대상 조회 실패: errorType={}", exception.getClass().getSimpleName());
            return;
        }
        for (Long studentId : studentIds) {
            try {
                // 한 수료생의 실패가 다른 수료생의 상태 저장을 되돌리지 않는다.
                service.recalculateCommitted(studentId);
            } catch (RuntimeException exception) {
                log.warn("일일 상태 재계산 실패: studentId={}, errorType={}",
                        studentId, exception.getClass().getSimpleName());
            }
        }
    }
}
