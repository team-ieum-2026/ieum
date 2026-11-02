// 주인: BE-B
package com.ieum.helprequest;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HelpRequestMapper {
    Long findStudentId(@Param("studentId") Long studentId);

    int touchLastResponse(@Param("studentId") Long studentId,
                          @Param("respondedAt") LocalDateTime respondedAt);

    int insert(HelpRequestData.Row row);

    HelpRequestData.Row findForUpdate(@Param("helpRequestId") Long helpRequestId);

    HelpRequestData.Row findLatestByItemForUpdate(@Param("recommendationItemId") Long recommendationItemId);

    List<HelpRequestData.Row> findByStudent(@Param("studentId") Long studentId,
                                          @Param("status") String status);

    int updateDetails(HelpRequestData.Row row);

    int updateStatus(@Param("helpRequestId") Long helpRequestId, @Param("status") String status);

    int close(HelpRequestData.Row row);
}
