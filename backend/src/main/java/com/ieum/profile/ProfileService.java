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

/**
 * M8 프로필 조회와 BE-A가 호출할 프로필 변경 서비스 계약.
 * 조회 중 AI 추출이나 DB 수정을 하지 않는다. 변경 메서드는 아직 계약만 제공한다.
 */
@Service
public class ProfileService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ProfileMapper profileMapper;

    public ProfileService(ProfileMapper profileMapper) {
        this.profileMapper = profileMapper;
    }

    /**
     * M8·서류 편집에서 공통으로 사용할 프로필 항목 추가 계약(BE-B-04).
     *
     * @param studentId 수료생 번호
     * @param section API.md의 프로필 섹션 값. TRAINING은 읽기 전용이다.
     * @param item 서류 PATCH와 같은 camelCase 항목 JSON
     * @return 새로 생성한 프로필 항목의 숫자 ID. 서류 ID 접두어 처리는 호출자가 맡는다.
     * @throws BusinessException 실패 시 호출자의 트랜잭션으로 전파한다.
     * @implNote 본문 구현 전이다. 현재는 INTERNAL_ERROR로 실패하며 저장·AI 호출을 하지 않는다.
     * 구현 시 항목 저장과 AI 프로필 재추출·저장을 이 서비스에서 함께 처리한다.
     */
    @Transactional
    public Long addItem(Long studentId, String section, JsonNode item) {
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "프로필 항목 추가 서비스는 아직 구현되지 않았어요.");
    }

    /**
     * M8·서류 편집에서 공통으로 사용할 프로필 항목 수정 계약(BE-B-04).
     *
     * @param itemId 프로필 DB의 숫자 ID. 서류의 cert-2 등을 숫자로 바꾸는 것은 호출자 책임이다.
     * @throws BusinessException 수료생 없음·읽기 전용 섹션 등 실패 시 호출자에게 전파한다.
     * @implNote 아직 계약만 제공한다. 현재는 INTERNAL_ERROR로 실패하며 기존 M8 구현은 유지한다.
     */
    @Transactional
    public void updateItem(Long studentId, String section, Long itemId, JsonNode item) {
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "프로필 항목 수정 서비스는 아직 구현되지 않았어요.");
    }

    /**
     * M8·서류 편집에서 공통으로 사용할 프로필 항목 삭제 계약(BE-B-04).
     *
     * @param itemId 프로필 DB의 숫자 ID
     * @throws BusinessException 수료생 없음·읽기 전용 섹션 등 실패 시 호출자에게 전파한다.
     * @implNote 아직 계약만 제공한다. 현재는 INTERNAL_ERROR로 실패하며 기존 M8 구현은 유지한다.
     */
    @Transactional
    public void deleteItem(Long studentId, String section, Long itemId) {
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "프로필 항목 삭제 서비스는 아직 구현되지 않았어요.");
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
