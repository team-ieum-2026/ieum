// 주인: BE-A
package com.ieum.application;

import java.util.List;

/**
 * M5 서류 생성 진행 상태 응답의 data. 형식은 docs/API.md 2-1 · frontend/public/mock/M5_application_progress_*.json.
 * steps 는 항상 COMPANY_INFO · COVER_LETTER · RESUME 순서 3개.
 */
public record ApplicationProgressResponse(
        Long applicationId,
        String status,
        List<Step> steps
) {

    /** state: WAITING · RUNNING · DONE · FAILED. note 는 없으면 null (FE 기본 문구) */
    public record Step(
            String key,
            String state,
            String note
    ) {
    }
}
