// 주인: BE-B
package com.ieum.coordinator;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/** 대시보드는 상태를 갱신하거나 행을 잠그지 않고 저장된 결과를 읽는다. */
@Mapper
public interface CoordinatorDashboardMapper {
    List<CoordinatorDashboardData.StudentState> findStudentStates();
    List<CoordinatorDashboardData.HelpRequest> findOpenHelpRequests();
    List<CoordinatorDashboardData.ReviewQueueItem> findReviewQueue();
    List<CoordinatorDashboardData.NoteToCheck> findLatestPassNotes();
}
