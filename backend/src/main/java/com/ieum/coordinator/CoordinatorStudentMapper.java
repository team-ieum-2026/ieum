// 주인: BE-B
package com.ieum.coordinator;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 수료생 목록·상세 조회와 상담 기록 INSERT만 수행한다. 상태나 수료생 응답은 변경하지 않는다. */
@Mapper
public interface CoordinatorStudentMapper {

    long countStudents(@Param("status") String status, @Param("subStatus") String subStatus,
                       @Param("namePattern") String namePattern);

    List<CoordinatorStudentData.Student> findStudents(
            @Param("status") String status, @Param("subStatus") String subStatus,
            @Param("namePattern") String namePattern, @Param("offset") long offset, @Param("size") int size);

    CoordinatorStudentData.Student findStudent(@Param("studentId") Long studentId);

    CoordinatorStudentData.RegisteredSummary findRegisteredSummary(@Param("studentId") Long studentId);

    List<CoordinatorStudentData.Recommendation> findRecommendations(@Param("studentId") Long studentId);

    List<CoordinatorStudentData.HistoryItem> findHistoryItems(@Param("studentId") Long studentId);

    List<CoordinatorStudentData.ContactLog> findContactLogs(@Param("studentId") Long studentId);

    int insertContactLog(CoordinatorStudentData.ContactLog row);
}
