// 주인: BE-B
package com.ieum.status;

import com.ieum.application.ApplicationSubmittedEvent;
import com.ieum.coordinator.ApplicationReviewChangedEvent;
import com.ieum.helprequest.HelpRequestChangedEvent;
import com.ieum.recommendation.RecommendationRespondedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 지원·패스·도움 요청·제출·검토 변경의 저장이 확정되면 원문 기준으로 상태를 다시 계산한다. */
@Component
public class StatusEventListener {

    private static final Logger log = LoggerFactory.getLogger(StatusEventListener.class);

    private final StatusService service;

    public StatusEventListener(StatusService service) {
        this.service = service;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResponded(RecommendationRespondedEvent event) {
        recalculate(event.studentId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmitted(ApplicationSubmittedEvent event) {
        recalculate(event.studentId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onHelpRequestChanged(HelpRequestChangedEvent event) {
        recalculate(event.studentId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationReviewChanged(ApplicationReviewChangedEvent event) {
        recalculate(event.studentId());
    }

    private void recalculate(Long studentId) {
        try {
            // 원문에 저장된 last_response_at을 사용한다. 코디네이터 동작은 응답 시각을 바꾸지 않는다.
            service.recalculateCommitted(studentId);
        } catch (RuntimeException exception) {
            // 원문 저장은 이미 커밋되었다. 일일 재계산에서 다시 시도한다.
            log.warn("상태 이벤트 재계산 실패: studentId={}, errorType={}",
                    studentId, exception.getClass().getSimpleName());
        }
    }
}
