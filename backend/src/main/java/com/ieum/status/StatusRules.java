// 주인: BE-B
package com.ieum.status;

import java.time.LocalDateTime;

/** 최근 취업·구직·휴식 의사를 판정한다. 도움 요청은 별도 처리 목록에서 관리한다. */
final class StatusRules {

    private StatusRules() {}

    record Facts(
            boolean employed,
            boolean openHelp,
            boolean activeApplication,
            LocalDateTime inactivitySince,
            String latestPassReason,
            boolean latestRecommendationEmpty,
            String intent) {

        Facts(boolean employed, boolean openHelp, boolean activeApplication,
              LocalDateTime inactivitySince, String latestPassReason, boolean latestRecommendationEmpty) {
            this(employed, openHelp, activeApplication, inactivitySince, latestPassReason,
                    latestRecommendationEmpty, null);
        }

        Facts(boolean employed, boolean openHelp, boolean activeApplication,
              LocalDateTime inactivitySince, String latestPassReason) {
            this(employed, openHelp, activeApplication, inactivitySince, latestPassReason, false, null);
        }
    }

    static StatusDecision decide(Facts facts, LocalDateTime now) {
        if (facts.employed() || "EMPLOYED".equals(facts.intent())) {
            return new StatusDecision("EMPLOYED", null, "최근 취업 의사 확인");
        }
        if ("RESTING".equals(facts.intent())) {
            return new StatusDecision("RESTING", null, "최근 휴식 의사 확인");
        }
        if (facts.latestRecommendationEmpty() && !"SEEKING".equals(facts.intent())) {
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
