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
            /** 판정에 쓰지 않는다 (추천 0건은 후속 확인 사유가 아님). 호출 호환을 위해 남겨 둔다 */
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
        // 후속 확인 필요(CHECK_REQUIRED)는 7일 무응답만 뜻한다. 추천 0건(맞는 공고 없음)은 상태를 바꾸지 않는다
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
