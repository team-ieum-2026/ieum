// 주인: BE-B
package com.ieum.profile;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 시드처럼 프로필 변경 이벤트가 없었던 수료생의 역량 프로필을 순서대로 채운다. */
@Service
public class ProfileBatchExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ProfileBatchExtractionService.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ProfileMapper mapper;
    private final ProfileExtractionWorker worker;

    public ProfileBatchExtractionService(ProfileMapper mapper, ProfileExtractionWorker worker) {
        this.mapper = mapper;
        this.worker = worker;
    }

    // 단일 BE 서버에서 겹친 관리 요청도 직렬화한다. 전체 배치에는 DB 트랜잭션을 걸지 않는다.
    public synchronized ProfileBatchExtractionResult extractMissing() {
        List<Long> targets = mapper.findStudentIdsWithoutCompetencyProfile();
        int extractedCount = 0;
        List<Long> failedStudentIds = new ArrayList<>();
        for (Long studentId : targets) {
            try {
                if (worker.extractIfMissing(studentId)) extractedCount++;
            } catch (RuntimeException failure) {
                failedStudentIds.add(studentId);
                // 개인정보나 AI 응답 본문은 로그에 남기지 않는다.
                log.warn("관리용 프로필 추출 실패: studentId={}, errorType={}",
                        studentId, failure.getClass().getSimpleName());
            }
        }
        return new ProfileBatchExtractionResult(targets.size(), extractedCount,
                List.copyOf(failedStudentIds), OffsetDateTime.now(SEOUL));
    }
}
