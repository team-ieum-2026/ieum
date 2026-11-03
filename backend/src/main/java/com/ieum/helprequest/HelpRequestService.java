// 주인: BE-B
package com.ieum.helprequest;

import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import com.ieum.recommendation.RecommendationMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 도움 요청과 원 응답은 함께 커밋하고, 상태는 AFTER_COMMIT 이벤트에서 재계산한다.
 * 처리 단계: OPEN(접수) → IN_PROGRESS(처리 중) · CONSULTATION_SCHEDULED(상담 예정, 상담 일시 필수) → CONTACTED(연락 완료).
 * 수료생은 OPEN · IN_PROGRESS · CONSULTATION_SCHEDULED 요청을 직접 종료(CLOSED)할 수 있고, CONTACTED 는 409.
 */
@Service
public class HelpRequestService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Set<String> STATUSES = Set.of("OPEN", "IN_PROGRESS", "CONSULTATION_SCHEDULED", "CONTACTED", "CLOSED");
    /** 코디네이터가 바꿀 수 있는 상태 */
    private static final Set<String> COORDINATOR_STATUSES = Set.of("OPEN", "IN_PROGRESS", "CONSULTATION_SCHEDULED", "CONTACTED");
    /** 수료생이 직접 종료할 수 있는 상태 (담당자가 연락을 마치기 전) */
    private static final Set<String> CLOSABLE = Set.of("OPEN", "IN_PROGRESS", "CONSULTATION_SCHEDULED");
    private static final Set<String> CLOSE_REASONS = Set.of("EMPLOYED", "NO_LONGER_NEEDED", "OTHER");
    private final HelpRequestMapper mapper;
    private final RecommendationMapper recommendations;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Autowired
    public HelpRequestService(HelpRequestMapper mapper, RecommendationMapper recommendations,
                              ApplicationEventPublisher events) {
        this(mapper, recommendations, events, Clock.system(SEOUL));
    }

    HelpRequestService(HelpRequestMapper mapper, RecommendationMapper recommendations,
                       ApplicationEventPublisher events, Clock clock) {
        this.mapper = mapper;
        this.recommendations = recommendations;
        this.events = events;
        this.clock = clock.withZone(SEOUL);
    }

    @Transactional
    public HelpRequestResult create(HelpRequestCreateRequest request) {
        if (request == null || !request.isValid()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (mapper.findStudentId(request.studentId()) == null) {
            throw new BusinessException(ErrorCode.STUDENT_NOT_FOUND);
        }
        if (request.recommendationItemId() != null) {
            var item = recommendations.selectItemOwner(request.recommendationItemId());
            if (item == null) throw new BusinessException(ErrorCode.RECOMMENDATION_ITEM_NOT_FOUND);
            if (!request.studentId().equals(item.getStudentId())) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "본인의 추천 공고에서 도움을 요청해 주세요.");
            }
            // BE-A의 지원·패스와 같은 item → student 순서로 잠근다. NONE 조건이 중복·경쟁을 막는다.
            if (recommendations.updateItemResponse(request.recommendationItemId(), "HELP", null, null) == 0) {
                throw new BusinessException(ErrorCode.ALREADY_RESPONDED);
            }
        }
        LocalDateTime respondedAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        mapper.touchLastResponse(request.studentId(), respondedAt);
        var row = new HelpRequestData.Row();
        row.setStudentId(request.studentId());
        row.setRecommendationItemId(request.recommendationItemId());
        row.setHelpType(request.helpType());
        row.setStatus("OPEN");
        row.setCreatedAt(respondedAt);
        if (mapper.insert(row) != 1 || row.getHelpRequestId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        publish(row);
        return new HelpRequestResult(row.getHelpRequestId(), row.getStatus());
    }

    @Transactional(readOnly = true)
    public List<HelpRequestListItem> list(Long studentId, String status) {
        if (!positive(studentId) || (status != null && !STATUSES.contains(status))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (mapper.findStudentId(studentId) == null) {
            throw new BusinessException(ErrorCode.STUDENT_NOT_FOUND);
        }
        return mapper.findByStudent(studentId, status).stream()
                .map(row -> new HelpRequestListItem(row.getHelpRequestId(), row.getStudentId(),
                        row.getRecommendationItemId(), row.getHelpType(), row.getStatus(),
                        row.getContactMethod(), row.getMemo(), seoulTime(row.getCreatedAt()),
                        seoulTime(row.getClosedAt()), row.getCloseReason(), row.getJobTitle(),
                        seoulTime(row.getConsultationAt())))
                .toList();
    }

    @Transactional
    public HelpRequestCloseResult close(Long helpRequestId, HelpRequestCloseRequest request) {
        if (!positive(helpRequestId) || request == null || !positive(request.studentId())
                || request.closeReason() == null || !CLOSE_REASONS.contains(request.closeReason())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        // 요청 행만 잠근다. 학생 상태는 이 트랜잭션 커밋 후 이벤트에서 다시 계산한다.
        var row = mapper.findForUpdate(helpRequestId);
        if (row == null) throw new HelpRequestNotFoundException();
        if (!request.studentId().equals(row.getStudentId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "본인의 도움 요청만 종료할 수 있어요.");
        }
        if (CLOSABLE.contains(row.getStatus())) {
            row.setStatus("CLOSED");
            row.setClosedAt(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
            row.setCloseReason(request.closeReason());
            if (mapper.close(row) != 1) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        } else if (!"CLOSED".equals(row.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        // 종료는 수료생의 추가 응답으로 계산하지 않으며, 다른 요청에는 영향을 주지 않는다.
        publish(row);
        return new HelpRequestCloseResult(row.getHelpRequestId(), row.getStatus(),
                seoulTime(row.getClosedAt()), row.getCloseReason());
    }

    /**
     * 추천 응답 HELP 취소. 호출자가 추천 항목을 먼저 잠근 같은 트랜잭션에서 부른다.
     * 연락 완료 전(OPEN · IN_PROGRESS · CONSULTATION_SCHEDULED) 요청만 본인 종료로 닫아 이력을 남기고,
     * 연락 완료·종료된 요청은 409로 유지한다.
     */
    @Transactional
    public void closeForCancelledResponse(Long studentId, Long recommendationItemId) {
        var row = mapper.findLatestByItemForUpdate(recommendationItemId);
        if (row == null || !studentId.equals(row.getStudentId())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "도움 요청 기록을 찾지 못했어요. 새로고침 후 다시 확인해주세요.");
        }
        if ("CLOSED".equals(row.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS,
                    "이미 종료한 도움 요청이에요. 새로고침 후 다시 확인해주세요.");
        }
        if (!CLOSABLE.contains(row.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS,
                    "담당자가 이미 연락한 도움 요청은 직접 취소할 수 없어요. 담당자에게 문의해주세요.");
        }
        row.setStatus("CLOSED");
        row.setClosedAt(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
        row.setCloseReason("NO_LONGER_NEEDED");
        if (mapper.close(row) != 1) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        publish(row);
    }

    @Transactional
    public HelpRequestUpdateResult update(Long helpRequestId, HelpRequestUpdateRequest request) {
        if (!positive(helpRequestId) || request == null || !request.isValid()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        var row = requireEditable(helpRequestId);
        String method = request.hasContactMethod() ? request.getContactMethod() : row.getContactMethod();
        String memo = request.hasMemo() ? request.getMemo() : row.getMemo();
        if (!Objects.equals(method, row.getContactMethod()) || !Objects.equals(memo, row.getMemo())) {
            row.setContactMethod(method);
            row.setMemo(memo);
            if (mapper.updateDetails(row) != 1) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        // 연락 방법·메모 수정은 추가 응답으로 간주하지 않는다.
        publish(row);
        return new HelpRequestUpdateResult(row.getHelpRequestId(), row.getStatus(),
                row.getContactMethod(), row.getMemo());
    }

    @Transactional
    public HelpRequestStatusResult updateStatus(Long helpRequestId, HelpRequestStatusUpdateRequest request) {
        if (!positive(helpRequestId) || request == null || !COORDINATOR_STATUSES.contains(request.status())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        boolean scheduling = "CONSULTATION_SCHEDULED".equals(request.status());
        if (scheduling && request.consultationAt() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "상담 일시를 입력해 주세요.");
        }
        if (!scheduling && request.consultationAt() != null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "상담 일시는 '상담 예정'일 때만 입력해요.");
        }
        var row = requireEditable(helpRequestId);
        // 상담 일시는 서울 시각 초 단위로 저장한다. 다른 상태로 바꿔도 지난 상담 일시는 이력으로 남긴다
        LocalDateTime consultationAt = scheduling
                ? request.consultationAt().atZoneSameInstant(SEOUL).toLocalDateTime().truncatedTo(ChronoUnit.SECONDS)
                : row.getConsultationAt();
        if (!request.status().equals(row.getStatus()) || !Objects.equals(consultationAt, row.getConsultationAt())) {
            if (mapper.updateStatus(helpRequestId, request.status(), consultationAt) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR);
            }
            row.setStatus(request.status());
            row.setConsultationAt(consultationAt);
        }
        // 코디네이터 처리는 수료생 응답 시각과 closed_at을 변경하지 않는다.
        publish(row);
        return new HelpRequestStatusResult(helpRequestId, row.getStatus(), seoulTime(row.getConsultationAt()));
    }

    /** 종료(CLOSED)되지 않은 요청만 연락 방법 · 메모 · 처리 상태를 바꿀 수 있다 */
    private HelpRequestData.Row requireEditable(Long helpRequestId) {
        var row = mapper.findForUpdate(helpRequestId);
        if (row == null) throw new HelpRequestNotFoundException();
        if (!COORDINATOR_STATUSES.contains(row.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        return row;
    }

    private void publish(HelpRequestData.Row row) {
        events.publishEvent(new HelpRequestChangedEvent(row.getStudentId(), row.getHelpRequestId()));
    }

    private static boolean positive(Long value) {
        return value != null && value > 0;
    }

    private static OffsetDateTime seoulTime(LocalDateTime value) {
        return value == null ? null : value.atZone(SEOUL).toOffsetDateTime();
    }
}
