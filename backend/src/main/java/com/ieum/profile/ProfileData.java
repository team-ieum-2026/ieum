// 주인: BE-B
package com.ieum.profile;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** MyBatis 조회 결과. DB의 JSON·시각을 서비스에서 M8 응답 타입으로 변환한다. */
public final class ProfileData {

    private ProfileData() {}

    @Getter
    @Setter
    public static class Student {
        private Long studentId;
        private String name;
        private String email;
        private String githubUrl;
        private String desiredJob;
        private String preferredRegion;
        private String militaryStatus;
        private LocalDate completedOn;
        private JsonNode skills;
        private Long courseId;
        private String courseName;
        private String cohort;
        private String campus;
        private LocalDate startedOn;
        private LocalDate endedOn;
        private String coverLetterContent;
        private LocalDateTime coverLetterUpdatedAt;
        private LocalDateTime profileUpdatedAt;
        private JsonNode competencyProfile;
    }

    @Getter
    @Setter
    public static class Project {
        private Long itemId;
        private String title;
        private String periodText;
        private Integer teamSize;
        private JsonNode techStack;
        private String portfolioFileName;
        private boolean includeInApply;
    }
}
