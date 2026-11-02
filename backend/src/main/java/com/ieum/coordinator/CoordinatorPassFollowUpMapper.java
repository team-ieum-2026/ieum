// 주인: BE-B
package com.ieum.coordinator;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CoordinatorPassFollowUpMapper {
    List<CoordinatorPassFollowUpData> findFollowUps();
    List<CoordinatorPassFollowUpData> findStreaks(@Param("studentIds") List<Long> studentIds);
}
