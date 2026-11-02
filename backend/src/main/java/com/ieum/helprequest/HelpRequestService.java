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

/** 도움 요청과 원 응답은 함께 커밋하고, 상태는 AFTER_COMMIT 이벤트에서 재계산한다. */
@Service
public class HelpRequestService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Set<String> STATUSES = Set.of("OPEN", "CONTACTED", "CLOSED");
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
                        seoulTime(row.getClosedAt()), row.getCloseReason(), row.getJobTitle()))
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
        if ("OPEN".equals(row.getStatus())) {
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
    public HelpRequestResult updateStatus(Long helpRequestId, HelpRequestStatusUpdateRequest request) {
        if (!positive(helpRequestId) || request == null
                || !("OPEN".equals(request.status()) || "CONTACTED".equals(request.status()))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        var row = requireEditable(helpRequestId);
        if (!request.status().equals(row.getStatus())) {
            if (mapper.updateStatus(helpRequestId, request.status()) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR);
            }
            row.setStatus(request.status());
        }
        // 코디네이터 처리는 수료생 응답 시각과 closed_at을 변경하지 않는다.
        publish(row);
        return new HelpRequestResult(helpRequestId, row.getStatus());
    }

    private HelpRequestData.Row requireEditable(Long helpRequestId) {
        var row = mapper.findForUpdate(helpRequestId);
        if (row == null) throw new HelpRequestNotFoundException();
        if (!("OPEN".equals(row.getStatus()) || "CONTACTED".equals(row.getStatus()))) {
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
