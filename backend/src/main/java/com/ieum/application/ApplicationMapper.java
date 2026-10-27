// 주인: BE-A
package com.ieum.application;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** application · application_document 매퍼. SQL은 resources/mapper/application/ApplicationMapper.xml */
@Mapper
public interface ApplicationMapper {

    /** 수료생이 있으면 1, 없으면 0 */
    int countStudent(@Param("studentId") Long studentId);

    /** 수료생의 지원서 전체 + 서류 JSON, 최근 생성 순 */
    List<ApplicationRows.ListItem> selectByStudent(@Param("studentId") Long studentId);

    /** 수료생의 가장 최근 추천 묶음에서 지원서가 없는 항목, rank 순 */
    List<ApplicationRows.OtherItem> selectLatestOtherItems(@Param("studentId") Long studentId);

    /** 서류 생성 대상 지원서 + 공고 (없으면 null) */
    ApplicationRows.DraftTarget selectDraftTarget(@Param("applicationId") Long applicationId);

    /** 서류 생성용 프로젝트 (README · 역할 기록 포함), id 순 */
    List<ApplicationRows.DraftProject> selectDraftProjects(@Param("studentId") Long studentId);

    /** 회사 이름으로 기업 정보 (없으면 null) */
    ApplicationRows.CompanyInfo selectCompanyInfo(@Param("companyName") String companyName);

    /** M5 진행 상태 (없으면 null) */
    ApplicationRows.Progress selectProgress(@Param("applicationId") Long applicationId);

    /** 서류 JSON 저장 (있으면 덮어씀) */
    int upsertDocument(@Param("applicationId") Long applicationId, @Param("document") String document);

    /** 상태가 from 일 때만 to 로 바꾼다. 0 이면 이미 다른 상태 */
    int updateStatus(@Param("applicationId") Long applicationId, @Param("from") String from, @Param("to") String to);
}
