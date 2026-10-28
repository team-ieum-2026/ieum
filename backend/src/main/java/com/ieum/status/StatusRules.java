// 주인: BE-B
package com.ieum.status;

import java.time.LocalDateTime;

/** DB 조회·저장 없이 상태를 판정한다. 취업·도움 요청은 추천 0건 신호보다 우선한다. */
final class StatusRules {

    private StatusRules() {}

    record Facts(
            boolean employed,
            boolean openHelp,
            boolean activeApplication,
            LocalDateTime inactivitySince,
            String latestPassReason,
            boolean latestRecommendationEmpty) {

        Facts(boolean employed, boolean openHelp, boolean activeApplication,
              LocalDateTime inactivitySince, String latestPassReason) {
            this(employed, openHelp, activeApplication, inactivitySince, latestPassReason, false);
        }
    }

    static StatusDecision decide(Facts facts, LocalDateTime now) {
        if (facts.employed()) {
            return new StatusDecision("EMPLOYED", null, "패스 사유 ALREADY_EMPLOYED");
        }
        if (facts.openHelp()) {
            return new StatusDecision("NEED_HELP", null, "열린 도움 요청 있음");
        }
        if (facts.latestRecommendationEmpty()) {
            return new StatusDecision("CHECK_REQUIRED", null, "맞는 공고 없음");
        }
        if (!facts.inactivitySince().isAfter(now.minusDays(7))) {
            return new StatusDecision("CHECK_REQUIRED", null, "마지막 응답 또는 수료 후 7일 이상 경과");
        }
        if (facts.activeApplication()) {
            return new StatusDecision("SEEKING", "APPLYING", "제출·승인·전달된 지원서 있음");
        }
        if ("OTHER".equals(facts.latestPassReason())) {
            return new StatusDecision("SEEKING", "OTHER", "최근 패스 사유 OTHER");
        }
        return new StatusDecision("SEEKING", "WAITING", "다음 추천 대기");
    }
}
