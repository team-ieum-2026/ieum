// 주인: BE-B
package com.ieum.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 등록 자료와 이미 저장된 역량 프로필을 읽는다. 조회 중 AI 추출이나 DB 수정을 하지 않는다. */
@Service
public class ProfileService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ProfileMapper profileMapper;

    public ProfileService(ProfileMapper profileMapper) {
        this.profileMapper = profileMapper;
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse getProfile(Long studentId) {
        ProfileData.Student student = profileMapper.findStudent(studentId);
        if (student == null) {
            throw new BusinessException(ErrorCode.STUDENT_NOT_FOUND);
        }

        var basic = new StudentProfileResponse.Basic(
                student.getName(), student.getEmail(), student.getGithubUrl(),
                student.getDesiredJob(), student.getPreferredRegion(), student.getMilitaryStatus(),
                student.getCompletedOn());
        var course = new StudentProfileResponse.Course(
                student.getCourseId(), student.getCourseName(), student.getCohort(), student.getCampus(),
                student.getStartedOn(), student.getEndedOn());
        var projects = profileMapper.findProjects(studentId).stream()
                .map(project -> new StudentProfileResponse.Project(
                        project.getItemId(), project.getTitle(), project.getPeriodText(), project.getTeamSize(),
                        stringList(project.getTechStack()), project.getPortfolioFileName(), project.isIncludeInApply()))
                .toList();
        var coverLetter = student.getCoverLetterContent() == null
                ? null
                : new StudentProfileResponse.CoverLetter(
                        student.getCoverLetterContent(), seoulTime(student.getCoverLetterUpdatedAt()));

        return new StudentProfileResponse(
                student.getStudentId(), seoulTime(student.getProfileUpdatedAt()), basic, course,
                profileMapper.findEducation(studentId), profileMapper.findCareers(studentId),
                profileMapper.findCertificates(studentId), stringList(student.getSkills()), projects,
                coverLetter, student.getCompetencyProfile());
    }

    private static OffsetDateTime seoulTime(LocalDateTime value) {
        return value == null ? null : value.atZone(SEOUL).toOffsetDateTime();
    }

    private static List<String> stringList(JsonNode value) {
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            throw new IllegalStateException("프로필의 기술 목록은 JSON 배열이어야 합니다.");
        }
        List<String> items = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                throw new IllegalStateException("프로필의 기술 목록에는 문자열만 들어갈 수 있습니다.");
            }
            items.add(item.textValue());
        }
        return List.copyOf(items);
    }
}
