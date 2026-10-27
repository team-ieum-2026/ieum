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
}
