// 주인: BE-B
package com.ieum.profile;

import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 저장이 커밋된 후에만 호출하며 대기 중인 동일 학생의 변경은 한 번으로 합친다. */
@Component
public class ProfileExtractionListener {

    private static final Logger log = LoggerFactory.getLogger(ProfileExtractionListener.class);

    private final ProfileExtractionWorker worker;
    private final ExecutorService executor;
    private final Set<Long> pending = ConcurrentHashMap.newKeySet();

    @Autowired
    public ProfileExtractionListener(ProfileExtractionWorker worker) {
        this(worker, Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "profile-extraction");
            thread.setDaemon(true);
            return thread;
        }));
    }

    ProfileExtractionListener(ProfileExtractionWorker worker, ExecutorService executor) {
        this.worker = worker;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChanged(ProfileChangedEvent event) {
        Long studentId = event.studentId();
        if (!pending.add(studentId)) return;
        try {
            executor.execute(() -> {
                pending.remove(studentId);
                try {
                    worker.refresh(studentId);
                } catch (RuntimeException exception) {
                    // 원문이나 서버 응답을 로그에 남기지 않는다. 다음 자료 변경 때 다시 시도한다.
                    log.warn("프로필 재추출 실패: studentId={}, errorType={}",
                            studentId, exception.getClass().getSimpleName());
                }
            });
        } catch (RejectedExecutionException exception) {
            pending.remove(studentId);
            log.warn("프로필 재추출 시작 실패: studentId={}, errorType={}",
                    studentId, exception.getClass().getSimpleName());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
