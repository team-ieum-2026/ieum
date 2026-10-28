// 주인: BE-B
package com.ieum.status;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 상태 판정에 필요한 원문만 읽는다. 이름·연락처·AI 프로필은 사용하지 않는다. */
public final class StatusData {

    private StatusData() {}

    @Getter
    @Setter
    public static class Student {
        private Long studentId;
        private LocalDateTime lastResponseAt;
        /** 마지막 응답 시각, 응답 이력이 없으면 수료일 00:00:00(서울 시각). */
        private LocalDateTime inactivitySince;
    }

    @Getter
    @Setter
    public static class Response {
        private Long itemId;
        private String response;
        private String passReason;
        private LocalDateTime respondedAt;
    }
}
