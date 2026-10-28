// 주인: BE-B
package com.ieum.helprequest;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 도움 요청 저장·잠금 조회 결과. */
public final class HelpRequestData {
    private HelpRequestData() {}

    @Getter
    @Setter
    public static class Row {
        private Long helpRequestId;
        private Long studentId;
        private Long recommendationItemId;
        private String contactMethod;
        private String memo;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime closedAt;
    }
}
