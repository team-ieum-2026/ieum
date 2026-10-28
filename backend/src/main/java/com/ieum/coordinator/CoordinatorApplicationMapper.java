// 주인: BE-B
package com.ieum.coordinator;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** C4 서류 조회·상태 변경과 반려 사유의 상담 기록 저장만 담당한다. */
@Mapper
public interface CoordinatorApplicationMapper {

    CoordinatorApplicationData.Detail findDetail(@Param("applicationId") Long applicationId);

    CoordinatorApplicationData.ReviewTarget lockApplication(@Param("applicationId") Long applicationId);

    int approve(@Param("applicationId") Long applicationId, @Param("reviewedAt") LocalDateTime reviewedAt);

    int reject(@Param("applicationId") Long applicationId, @Param("note") String note,
               @Param("reviewedAt") LocalDateTime reviewedAt);

    int deliver(@Param("applicationId") Long applicationId, @Param("deliveredAt") LocalDateTime deliveredAt);

    int insertRejectContact(@Param("studentId") Long studentId, @Param("note") String note,
                            @Param("createdAt") LocalDateTime createdAt);
}
