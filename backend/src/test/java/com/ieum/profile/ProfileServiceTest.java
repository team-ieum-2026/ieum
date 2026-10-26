// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private ProfileMapper profileMapper;

    @InjectMocks
    private ProfileService profileService;

    @Test
    void missingStudentReturnsExistingNotFoundErrorWithoutReadingSections() {
        when(profileMapper.findStudent(9999L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class, () -> profileService.getProfile(9999L));

        assertEquals(ErrorCode.STUDENT_NOT_FOUND, error.getErrorCode());
        verify(profileMapper).findStudent(9999L);
        verifyNoMoreInteractions(profileMapper);
    }

    @Test
    void assemblesStoredProfileAndUsesExtractionTimeInSeoul() {
        ProfileData.Student student = student(21L);
        student.setSkills(JSON.createArrayNode().add("Java").add("MySQL"));
        student.setProfileUpdatedAt(LocalDateTime.of(2026, 10, 26, 11, 20));
        student.setCoverLetterContent("모의 자기소개서");
        student.setCoverLetterUpdatedAt(LocalDateTime.of(2026, 10, 25, 9, 10));
        JsonNode competency = JSON.createObjectNode().set("roles", JSON.createArrayNode().add("백엔드 개발"));
        student.setCompetencyProfile(competency);

        var education = new StudentProfileResponse.Education(
                3L, "REGISTERED", "모의대학교", null, "BACHELOR", "GRADUATED", LocalDate.of(2026, 2, 28));
        var career = new StudentProfileResponse.Career(
                4L, "USER", "모의 회사", "개발", LocalDate.of(2026, 3, 1), null, null);
        var certificate = new StudentProfileResponse.Certificate(
                5L, "REGISTERED", "모의 자격증", null, LocalDate.of(2026, 4, 1));
        ProfileData.Project project = new ProfileData.Project();
        project.setItemId(6L);
        project.setTitle("모의 프로젝트");
        project.setTeamSize(3);
        project.setTechStack(JSON.createArrayNode().add("Spring Boot").add("MySQL"));
        project.setPortfolioFileName("mock-portfolio.pdf");
        project.setIncludeInApply(true);

        when(profileMapper.findStudent(21L)).thenReturn(student);
        when(profileMapper.findEducation(21L)).thenReturn(List.of(education));
        when(profileMapper.findCareers(21L)).thenReturn(List.of(career));
        when(profileMapper.findCertificates(21L)).thenReturn(List.of(certificate));
        when(profileMapper.findProjects(21L)).thenReturn(List.of(project));

        StudentProfileResponse response = profileService.getProfile(21L);

        assertAll(
                () -> assertEquals(21L, response.studentId()),
                () -> assertEquals("모의 수료생", response.basic().name()),
                () -> assertEquals("모의 과정", response.course().name()),
                () -> assertEquals(List.of("Java", "MySQL"), response.skills()),
                () -> assertEquals(List.of(education), response.education()),
                () -> assertEquals(List.of(career), response.careers()),
                () -> assertEquals(List.of(certificate), response.certificates()),
                () -> assertSame(competency, response.competencyProfile()),
                () -> assertEquals(student.getProfileUpdatedAt(), response.profileUpdatedAt().toLocalDateTime()),
                () -> assertEquals(ZoneOffset.ofHours(9), response.profileUpdatedAt().getOffset()),
                () -> assertEquals(student.getCoverLetterUpdatedAt(), response.coverLetter().updatedAt().toLocalDateTime()),
                () -> assertEquals(ZoneOffset.ofHours(9), response.coverLetter().updatedAt().getOffset()),
                () -> assertEquals("모의 자기소개서", response.coverLetter().content()));
        assertEquals(new StudentProfileResponse.Project(
                6L, "모의 프로젝트", null, 3, List.of("Spring Boot", "MySQL"), "mock-portfolio.pdf", true),
                response.projects().get(0));
    }

    @ParameterizedTest(name = "없는 JSON 목록을 빈 배열로 반환: {index}")
    @MethodSource("missingJsonValues")
    void preservesAbsentOptionalDataAndReturnsEmptyLists(JsonNode missingJson) {
        ProfileData.Student student = student(1L);
        student.setSkills(missingJson);
        ProfileData.Project project = new ProfileData.Project();
        project.setItemId(11L);
        project.setTitle("모의 프로젝트");
        project.setTechStack(missingJson);
        when(profileMapper.findStudent(1L)).thenReturn(student);
        when(profileMapper.findEducation(1L)).thenReturn(List.of());
        when(profileMapper.findCareers(1L)).thenReturn(List.of());
        when(profileMapper.findCertificates(1L)).thenReturn(List.of());
        when(profileMapper.findProjects(1L)).thenReturn(List.of(project));

        StudentProfileResponse response = profileService.getProfile(1L);

        assertAll(
                () -> assertTrue(response.education().isEmpty()),
                () -> assertTrue(response.careers().isEmpty()),
                () -> assertTrue(response.certificates().isEmpty()),
                () -> assertTrue(response.skills().isEmpty()),
                () -> assertTrue(response.projects().get(0).techStack().isEmpty()),
                () -> assertNull(response.projects().get(0).teamSize()),
                () -> assertNull(response.projects().get(0).portfolioFileName()),
                () -> assertNull(response.basic().email()),
                () -> assertNull(response.profileUpdatedAt()),
                () -> assertNull(response.coverLetter()),
                () -> assertNull(response.competencyProfile()));
    }

    private static Stream<JsonNode> missingJsonValues() {
        return Stream.of(null, NullNode.getInstance());
    }

    private static ProfileData.Student student(Long studentId) {
        ProfileData.Student student = new ProfileData.Student();
        student.setStudentId(studentId);
        student.setName("모의 수료생");
        student.setCompletedOn(LocalDate.of(2026, 8, 29));
        student.setCourseId(2L);
        student.setCourseName("모의 과정");
        student.setCohort("1기");
        student.setCampus("모의 캠퍼스");
        student.setStartedOn(LocalDate.of(2026, 3, 2));
        student.setEndedOn(LocalDate.of(2026, 8, 29));
        return student;
    }
}
