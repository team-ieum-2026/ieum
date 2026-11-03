// 주인: BE-B
package com.ieum.coordinator;

import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * C 서류 검토 · 도움 요청 상태별 목록 (조회만).
 * - 서류: 검토 요청 이후 단계만 보여 준다. 수료생이 작성 중인 GENERATING · DRAFT · FAILED 는 코디네이터 목록에 없다
 * - 도움 요청: OPEN · IN_PROGRESS · CONSULTATION_SCHEDULED · CONTACTED · CLOSED. 생략하거나 ALL 이면 전체
 */
@Service
public class CoordinatorListService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final List<String> REVIEW_STAGES = List.of("SUBMITTED", "REJECTED", "APPROVED", "DELIVERED");
    private static final Set<String> HELP_STATUSES = Set.of("OPEN", "IN_PROGRESS", "CONSULTATION_SCHEDULED", "CONTACTED", "CLOSED");

    private final CoordinatorListMapper mapper;

    public CoordinatorListService(CoordinatorListMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<CoordinatorListResponse.Application> applications(String status) {
        List<String> statuses;
        if (status == null || "ALL".equals(status)) {
            statuses = REVIEW_STAGES;
        } else if (REVIEW_STAGES.contains(status)) {
            statuses = List.of(status);
        } else {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "status는 ALL, SUBMITTED, REJECTED, APPROVED, DELIVERED 중 하나여야 해요.");
        }
        return mapper.findApplications(statuses).stream()
                .map(row -> new CoordinatorListResponse.Application(row.getApplicationId(), row.getStudentId(),
                        row.getStudentName(), row.getJobTitle(), row.getStatus(), seoulTime(row.getSubmittedAt()),
                        seoulTime(row.getReviewedAt()), seoulTime(row.getDeliveredAt()), row.getDeadlineOn(),
                        row.isUntilFilled()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CoordinatorListResponse.HelpRequest> helpRequests(String status) {
        String filter = status == null || "ALL".equals(status) ? null : status;
        if (filter != null && !HELP_STATUSES.contains(filter)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "status는 ALL, OPEN, IN_PROGRESS, CONSULTATION_SCHEDULED, CONTACTED, CLOSED 중 하나여야 해요.");
        }
        return mapper.findHelpRequests(filter).stream()
                .map(row -> new CoordinatorListResponse.HelpRequest(row.getHelpRequestId(), row.getStudentId(),
                        row.getStudentName(), row.getCourseLabel(), seoulTime(row.getCreatedAt()), row.getJobTitle(),
                        row.getContactMethod(), row.getMemo(), row.getHelpType(), row.getStatus(),
                        seoulTime(row.getClosedAt()), row.getCloseReason(), seoulTime(row.getConsultationAt())))
                .toList();
    }

    private static OffsetDateTime seoulTime(LocalDateTime value) {
        return value == null ? null : value.atZone(SEOUL).toOffsetDateTime();
    }
}
