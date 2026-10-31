// 주인: BE-B
package com.ieum.profile;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 프로필 조회·저장 SQL은 resources/mapper/profile/ProfileMapper.xml에 둔다. */
@Mapper
public interface ProfileMapper {

    List<Long> findStudentIdsWithoutCompetencyProfile();

    ProfileData.Student findStudent(@Param("studentId") Long studentId);

    ProfileData.Student findCurrentStudent(@Param("studentId") Long studentId);

    int updateBasic(ProfileData.Student basic);

    int updateMilitary(@Param("studentId") Long studentId, @Param("militaryStatus") String militaryStatus);

    int updateSkills(@Param("studentId") Long studentId, @Param("skills") JsonNode skills);

    List<StudentProfileResponse.Education> findEducation(@Param("studentId") Long studentId);

    List<StudentProfileResponse.Education> findCurrentEducation(@Param("studentId") Long studentId);

    StudentProfileResponse.Education findEducationItem(
            @Param("studentId") Long studentId, @Param("itemId") Long itemId);

    int insertEducation(ProfileData.Education education);

    int updateEducation(ProfileData.Education education);

    int deleteEducation(@Param("studentId") Long studentId, @Param("itemId") Long itemId);

    List<StudentProfileResponse.Career> findCareers(@Param("studentId") Long studentId);

    List<StudentProfileResponse.Career> findCurrentCareers(@Param("studentId") Long studentId);

    StudentProfileResponse.Career findCareerItem(@Param("studentId") Long studentId, @Param("itemId") Long itemId);

    int insertCareer(ProfileData.Career career);

    int updateCareer(ProfileData.Career career);

    int deleteCareer(@Param("studentId") Long studentId, @Param("itemId") Long itemId);

    List<StudentProfileResponse.Certificate> findCertificates(@Param("studentId") Long studentId);

    List<StudentProfileResponse.Certificate> findCurrentCertificates(@Param("studentId") Long studentId);

    List<ProfileData.Project> findProjects(@Param("studentId") Long studentId);

    List<ProfileData.Project> findCurrentProjects(@Param("studentId") Long studentId);

    ProfileData.Project findProjectItem(@Param("studentId") Long studentId, @Param("itemId") Long itemId);

    int insertProject(@Param("studentId") Long studentId, @Param("project") ProfileData.Project project);

    int updateProjectMetadata(@Param("studentId") Long studentId, @Param("project") ProfileData.Project project);

    int updateProjectOriginal(@Param("studentId") Long studentId, @Param("project") ProfileData.Project project);

    int updateProjectInclude(@Param("studentId") Long studentId, @Param("itemId") Long itemId,
                             @Param("include") boolean include);

    int updatePortfolio(@Param("studentId") Long studentId, @Param("itemId") Long itemId,
                        @Param("fileName") String fileName, @Param("include") boolean include);

    int deleteProject(@Param("studentId") Long studentId, @Param("itemId") Long itemId);

    Long lockStudent(@Param("studentId") Long studentId);

    int insertCertificate(ProfileData.Certificate certificate);

    StudentProfileResponse.Certificate findCertificate(
            @Param("studentId") Long studentId, @Param("itemId") Long itemId);

    int updateCertificate(ProfileData.Certificate certificate);

    int deleteCertificate(@Param("studentId") Long studentId, @Param("itemId") Long itemId);

    int upsertCoverLetter(@Param("studentId") Long studentId, @Param("content") String content);

    int upsertCompetencyProfile(
            @Param("studentId") Long studentId,
            @Param("profile") JsonNode profile,
            @Param("sourceHash") String sourceHash);
}
