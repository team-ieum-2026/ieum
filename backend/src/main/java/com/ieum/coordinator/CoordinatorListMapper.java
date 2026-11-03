// 주인: BE-B
package com.ieum.coordinator;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CoordinatorListMapper {
    /** 검토 요청 이후 지원서. statuses 가 비면 쓰지 않는다 (서비스가 항상 채운다) */
    List<CoordinatorListData.ApplicationRow> findApplications(@Param("statuses") List<String> statuses);

    /** status 가 null 이면 전체 */
    List<CoordinatorListData.HelpRequestRow> findHelpRequests(@Param("status") String status);
}
