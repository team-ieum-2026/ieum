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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * M8 프로필 조회와 BE-A가 호출할 프로필 변경 서비스 계약.
 * 조회는 읽기 전용이다. 서류 저장 시 프로젝트 원문을 보존하고 저장 확정 후 AI 재추출을 요청한다.
 */
@Service
public class ProfileService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ProfileMapper profileMapper;
    private final ProfileBasicWriter basic;
    private final ProfileMilitaryWriter military;
    private final ProfileSkillsWriter skills;
    private final ProfileRecordWriter records;
    private final ProfileProjectWriter projects;
    private final ApplicationEventPublisher events;

    public ProfileService(ProfileMapper profileMapper, ApplicationEventPublisher events) {
        this.profileMapper = profileMapper;
        this.basic = new ProfileBasicWriter(profileMapper);
        this.military = new ProfileMilitaryWriter(profileMapper);
        this.skills = new ProfileSkillsWriter(profileMapper);
        this.records = new ProfileRecordWriter(profileMapper);
        this.projects = new ProfileProjectWriter(profileMapper);
        this.events = events;
    }

    /**
     * M8·서류 편집에서 공통으로 사용할 프로필 항목 추가 계약(BE-B-04).
     *
     * @param studentId 수료생 번호
     * @param section API.md의 프로필 섹션 값. TRAINING은 읽기 전용이다.
     * @param item 서류 PATCH와 같은 camelCase 항목 JSON
     * @return 새로 생성한 프로필 항목의 숫자 ID. 서류 ID 접두어 처리는 호출자가 맡는다.
     * @throws BusinessException 실패 시 호출자의 트랜잭션으로 전파한다.
     * @implNote 학력·경력·자격증·프로젝트를 지원한다. 서류의 role·bullets·readme는 프로필 원문으로 저장하지 않는다.
     */
    @Transactional
    public Long addItem(Long studentId, String section, JsonNode item) {
        lockStudent(studentId);
        Long itemId;
        if (ProfileRecordWriter.supports(section)) itemId = records.add(studentId, section, item);
        else if ("PROJECTS".equals(section)) itemId = projects.add(studentId, item, false);
        else throw new BusinessException(ErrorCode.INVALID_REQUEST);
        events.publishEvent(new ProfileChangedEvent(studentId));
        return itemId;
    }

    /**
     * M8·서류 편집에서 공통으로 사용할 프로필 항목 수정 계약(BE-B-04).
     *
     * @param itemId 프로필 DB의 숫자 ID. BASIC·MILITARY·SKILLS는 studentId와 같다. 서류의 cert-2 등을 숫자로 바꾸는 것은 호출자 책임이다.
     * @throws BusinessException 수료생 없음·읽기 전용 섹션 등 실패 시 호출자에게 전파한다.
     * @implNote 기본정보·병역·기술·학력·경력·자격증, 프로젝트 메타데이터와 포트폴리오 포함 여부를 지원한다.
     * 서류의 기술 요약·프로젝트 원문·첨부 파일명은 저장하지 않는다.
     */
    @Transactional
    public void updateItem(Long studentId, String section, Long itemId, JsonNode item) {
        lockStudent(studentId);
        boolean changed;
        try {
            if (ProfileRecordWriter.supports(section)) {
                records.update(studentId, section, itemId, item);
                changed = true;
            } else if ("BASIC".equals(section)) {
                basic.update(studentId, itemId, item);
                changed = true;
            } else if ("MILITARY".equals(section)) {
                military.update(studentId, itemId, item);
                changed = true;
            } else if ("SKILLS".equals(section)) {
                changed = skills.update(studentId, itemId, item, true);
            } else if ("PROJECTS".equals(section)) changed = projects.update(studentId, itemId, item, false);
            else if ("PORTFOLIO".equals(section)) changed = projects.updateInclude(studentId, itemId, item);
            else throw new BusinessException(ErrorCode.INVALID_REQUEST);
        } catch (ProfileItemNotFoundException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, exception.getMessage());
        }
        if (changed) events.publishEvent(new ProfileChangedEvent(studentId));
    }

    /**
     * M8·서류 편집에서 공통으로 사용할 프로필 항목 삭제 계약(BE-B-04).
     *
     * @param itemId 프로필 DB의 숫자 ID
     * @throws BusinessException 수료생 없음·읽기 전용 섹션 등 실패 시 호출자에게 전파한다.
     * @implNote 학력·경력·자격증·프로젝트를 삭제한다. 포트폴리오는 포함 여부만 끄고 첨부 파일명과 원문을 보존한다.
     * 기본정보·병역·기술·교육과정·자기소개서는 항목 삭제 대상이 아니다.
     */
    @Transactional
    public void deleteItem(Long studentId, String section, Long itemId) {
        lockStudent(studentId);
        try {
            if (ProfileRecordWriter.supports(section)) records.delete(studentId, section, itemId);
            else if ("PROJECTS".equals(section)) projects.delete(studentId, itemId);
            else if ("PORTFOLIO".equals(section)) projects.excludePortfolio(studentId, itemId);
            else throw new BusinessException(ErrorCode.INVALID_REQUEST);
        } catch (ProfileItemNotFoundException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, exception.getMessage());
        }
        events.publishEvent(new ProfileChangedEvent(studentId));
    }

    private void lockStudent(Long studentId) {
        if (profileMapper.lockStudent(studentId) == null) throw new BusinessException(ErrorCode.STUDENT_NOT_FOUND);
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse getProfile(Long studentId) {
        return readProfile(studentId, false);
    }

    // 호출자 트랜잭션에 예전 조회 snapshot이 있어도 저장 후 최신 원문으로 추출한다.
    StudentProfileResponse getCurrentProfile(Long studentId) {
        return readProfile(studentId, true);
    }

    private StudentProfileResponse readProfile(Long studentId, boolean currentRead) {
        ProfileData.Student student = currentRead
                ? profileMapper.findCurrentStudent(studentId) : profileMapper.findStudent(studentId);
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
        var projects = (currentRead ? profileMapper.findCurrentProjects(studentId)
                : profileMapper.findProjects(studentId)).stream()
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
                currentRead ? profileMapper.findCurrentEducation(studentId) : profileMapper.findEducation(studentId),
                currentRead ? profileMapper.findCurrentCareers(studentId) : profileMapper.findCareers(studentId),
                currentRead ? profileMapper.findCurrentCertificates(studentId) : profileMapper.findCertificates(studentId),
                stringList(student.getSkills()), projects,
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
