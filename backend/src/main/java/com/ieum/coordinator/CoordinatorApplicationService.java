// 주인: BE-B
package com.ieum.coordinator;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** C4 문서 스냅샷 조회와 검토 처리. AI 호출이나 실제 발송은 하지 않는다. */
@Service
public class CoordinatorApplicationService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Set<String> NO_DOCUMENT = Set.of("GENERATING", "FAILED");
    private static final int MAX_NOTE_BYTES = 65535;
    private final CoordinatorApplicationMapper mapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Autowired
    public CoordinatorApplicationService(CoordinatorApplicationMapper mapper, ApplicationEventPublisher events) {
        this(mapper, events, Clock.system(SEOUL));
    }

    CoordinatorApplicationService(CoordinatorApplicationMapper mapper, ApplicationEventPublisher events, Clock clock) {
        this.mapper = mapper;
        this.events = events;
        this.clock = clock.withZone(SEOUL);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CoordinatorApplicationResponse detail(Long applicationId) {
        positiveId(applicationId);
        var row = mapper.findDetail(applicationId);
        if (row == null) throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND);
        JsonNode document = NO_DOCUMENT.contains(row.getStatus()) ? null : row.getDocument();
        if (document != null && document.isNull()) document = null;
        if (document != null && !document.isObject()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "서류 형식이 올바르지 않아요.");
        }
        return new CoordinatorApplicationResponse(row.getApplicationId(), row.getStatus(),
                new CoordinatorApplicationResponse.Student(row.getStudentId(), row.getStudentName()),
                new CoordinatorApplicationResponse.Job(row.getJobPostingId(), row.getCompanyName(), row.getTitle()),
                document, CoordinatorDocumentVerification.summarize(document));
    }

    @Transactional
    public CoordinatorApplicationResponse.StatusResult approve(Long applicationId) {
        return change(applicationId, "APPROVED", null);
    }

    @Transactional
    public CoordinatorApplicationResponse.StatusResult reject(Long applicationId,
                                                              CoordinatorApplicationRejectRequest request) {
        if (request == null || request.note() == null || request.note().isBlank()
                || request.note().getBytes(StandardCharsets.UTF_8).length > MAX_NOTE_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return change(applicationId, "REJECTED", request.note());
    }

    @Transactional
    public CoordinatorApplicationResponse.StatusResult deliver(Long applicationId) {
        return change(applicationId, "DELIVERED", null);
    }

    private CoordinatorApplicationResponse.StatusResult change(Long applicationId, String to, String note) {
        positiveId(applicationId);
        // 지원서 한 행만 잠근다. BE-A의 제출·서류 수정과 같은 원문을 직렬로 처리한다.
        var row = mapper.lockApplication(applicationId);
        if (row == null) throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND);
        String from = "DELIVERED".equals(to) ? "APPROVED" : "SUBMITTED";
        if (!from.equals(row.getStatus())) throw new BusinessException(ErrorCode.INVALID_STATUS);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        int changed = switch (to) {
            case "APPROVED" -> mapper.approve(applicationId, now);
            case "REJECTED" -> mapper.reject(applicationId, note, now);
            case "DELIVERED" -> mapper.deliver(applicationId, now);
            default -> throw new IllegalStateException("지원하지 않는 검토 처리예요.");
        };
        if (changed != 1) throw new BusinessException(ErrorCode.INVALID_STATUS);
        if ("REJECTED".equals(to) && mapper.insertRejectContact(row.getStudentId(), note, now) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        // 학생 응답 시각은 보존한다. 상태 재계산은 저장 확정 후 기존 리스너에서 수행한다.
        events.publishEvent(new ApplicationReviewChangedEvent(row.getStudentId(), applicationId));
        return new CoordinatorApplicationResponse.StatusResult(applicationId, to);
    }

    private static void positiveId(Long applicationId) {
        if (applicationId == null || applicationId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
}
