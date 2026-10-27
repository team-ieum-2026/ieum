// 주인: BE-B
package com.ieum.profile;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 프로필 조회 SQL은 resources/mapper/profile/ProfileMapper.xml에 둔다. */
@Mapper
public interface ProfileMapper {

    ProfileData.Student findStudent(@Param("studentId") Long studentId);

    List<StudentProfileResponse.Education> findEducation(@Param("studentId") Long studentId);

    List<StudentProfileResponse.Career> findCareers(@Param("studentId") Long studentId);

    List<StudentProfileResponse.Certificate> findCertificates(@Param("studentId") Long studentId);

    List<ProfileData.Project> findProjects(@Param("studentId") Long studentId);
}
