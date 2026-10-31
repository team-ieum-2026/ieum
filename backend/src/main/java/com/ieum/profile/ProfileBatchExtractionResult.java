// 주인: BE-B
package com.ieum.profile;

import java.time.OffsetDateTime;
import java.util.List;

/** 관리용 프로필 추출 결과. targetCount는 실행 시작 시 프로필이 없던 수료생 수다. */
public record ProfileBatchExtractionResult(
        int targetCount,
        int extractedCount,
        List<Long> failedStudentIds,
        OffsetDateTime extractedAt) {}
