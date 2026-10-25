// 주인: BE-B
package com.ieum.profile;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** M8 프로필 조회 응답. 형식은 M8_student_profile.json과 FE의 StudentProfile 타입을 따른다. */
public record StudentProfileResponse(
        Long studentId,
        OffsetDateTime profileUpdatedAt,
        Basic basic,
        Course course,
        List<Education> education,
        List<Career> careers,
        List<Certificate> certificates,
        List<String> skills,
        List<Project> projects,
        CoverLetter coverLetter,
        JsonNode competencyProfile) {

    public record Basic(
            String name,
            String email,
            String githubUrl,
            String desiredJob,
            String preferredRegion,
            String militaryStatus,
            LocalDate completedOn) {}

    public record Course(
            Long courseId,
            String name,
            String cohort,
            String campus,
            LocalDate startedOn,
            LocalDate endedOn) {}

    public record Education(
            Long itemId,
            String source,
            String schoolName,
            String major,
            String degree,
            String status,
            LocalDate graduatedOn) {}

    public record Career(
            Long itemId,
            String source,
            String companyName,
            String position,
            LocalDate startedOn,
            LocalDate endedOn,
            String description) {}

    public record Certificate(
            Long itemId,
            String source,
            String name,
            String issuer,
            LocalDate acquiredOn) {}

    public record Project(
            Long itemId,
            String title,
            String periodText,
            Integer teamSize,
            List<String> techStack,
            String portfolioFileName,
            boolean includeInApply) {}

    public record CoverLetter(String content, OffsetDateTime updatedAt) {}
}
