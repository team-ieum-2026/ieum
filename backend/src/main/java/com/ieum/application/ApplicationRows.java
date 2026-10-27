// 주인: BE-A
package com.ieum.application;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** ApplicationMapper 가 읽고 쓰는 DB 행. API 응답은 화면별 Response 로 따로 만든다. */
public final class ApplicationRows {

    private ApplicationRows() {
    }

    /** M7 지원서 한 줄: application + job_posting 요약 + 서류 JSON(없으면 null) */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ListItem {
        private Long applicationId;
        private String companyName;
        private String title;
        private String status;
        private LocalDateTime submittedAt;
        private String document;
    }

    /** M7 "이번 추천의 다른 공고": 가장 최근 추천 묶음에서 지원서가 없는 항목 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class OtherItem {
        private Long itemId;
        private String companyName;
        private String title;
        private String response;
        private String passReason;
    }

    /** 서류 생성 대상: application + 지원한 공고 (AI 요청의 job) */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class DraftTarget {
        private Long applicationId;
        private Long studentId;
        private String status;
        private Long jobPostingId;
        private String companyName;
        private String title;
        private String jobCategory;
        private String location;
        private String employmentType;
        private String career;
        private String education;
        private String description;
    }

    /** 서류 생성용 프로젝트. M8 응답과 달리 README 원문 · 역할 기록까지 담는다 (서류 근거의 핵심) */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class DraftProject {
        private Long itemId;
        private String title;
        private String periodText;
        private Integer teamSize;
        /** JSON 문자열 ["Spring Boot", ...] 또는 null */
        private String techStack;
        private String portfolioFileName;
        private boolean includeInApply;
        private String roleNote;
        private String readme;
        private String source;
    }

    /** company_info 한 줄. info 는 JSON 문자열 {talentKeywords, coreValues, recentIssue, sources} */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class CompanyInfo {
        private Long id;
        private String companyName;
        private String status;
        private String info;
        private LocalDateTime collectedAt;
    }

    /** M5 진행 상태: application 상태 + 회사 이름(기업 정보 단계 안내용) */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Progress {
        private Long applicationId;
        private String status;
        private String companyName;
    }
}
