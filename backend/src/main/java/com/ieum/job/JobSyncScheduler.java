// 주인: BE-A
package com.ieum.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 공고 일 배치. 시각은 app.seoul.sync-cron (기본 매일 06:00 KST). 키가 없으면 건너뛴다. */
@Component
public class JobSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobSyncScheduler.class);

    private final JobSyncService jobSyncService;
    private final SeoulJobProperties props;

    public JobSyncScheduler(JobSyncService jobSyncService, SeoulJobProperties props) {
        this.jobSyncService = jobSyncService;
        this.props = props;
    }

    @Scheduled(cron = "${app.seoul.sync-cron}", zone = "Asia/Seoul")
    public void dailySync() {
        if (!props.hasKey()) {
            log.info("SEOUL_OPENAPI_KEY가 없어 공고 일 배치를 건너뜀");
            return;
        }
        JobSyncResult result = jobSyncService.sync();
        log.info("공고 일 배치 결과: {} · {}", result.status(), result.message());
    }
}
