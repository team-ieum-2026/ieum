// 주인: BE-B
package com.ieum.status;

import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * BE-A·도움 요청·코디네이터가 호출할 상태 재계산 서비스(BE-B-06).
 * 동기 호출은 호출자와 같은 REQUIRED 트랜잭션을 사용하며 AI를 호출하지 않는다.
 * 동기 호출자는 학생 행을 먼저 잠근 뒤 관련 원문을 변경해야 한다.
 * 커밋 후 이벤트·배치는 READ_COMMITTED에서 학생 행만 잠그고 관련 원문을 비잠금 조회한다.
 * 응답 이력이 없으면 수료일부터 7일을 계산한다.
 */
@Service
public class StatusService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Set<String> SELECTABLE = Set.of("EMPLOYED", "SEEKING", "RESTING");

    private final StatusMapper mapper;
    private final Clock clock;

    @Autowired
    public StatusService(StatusMapper mapper) {
        this(mapper, Clock.system(SEOUL));
    }

    StatusService(StatusMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock.withZone(SEOUL);
    }

    /**
     * 지원·패스·도움 요청·서류 제출 저장 직후 응답 시각과 상태를 함께 반영한다.
     * 뒤늦게 도착한 과거 응답은 마지막 응답 시각을 되돌리지 않는다.
     * @param respondedAt 서울 현지 시각의 실제 응답 시각
     */
    @Transactional
    public void recordResponse(Long studentId, LocalDateTime respondedAt) {
        if (respondedAt == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        requireStudent(studentId);
        mapper.updateLastResponseAt(studentId, respondedAt);
        // DB에 실제 저장된 최종 시각으로 판정한다. DATETIME 정밀도와 이전 값도 반영한다.
        recalculateLocked(requireStudent(studentId), true);
    }

    /**
     * 마지막 응답 시각을 변경하지 않고 상태를 재계산한다.
     * 없는 수료생은 STUDENT_NOT_FOUND이며, 판정 실패는 호출자의 트랜잭션으로 전파한다.
     */
    @Transactional
    public void recalculate(Long studentId) {
        recalculateLocked(requireStudent(studentId), true);
    }

    /** 커밋 후 이벤트와 일일 배치는 완료된 호출자 트랜잭션과 분리해 저장한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void recalculateCommitted(Long studentId) {
        // BE-A 응답은 항목 → 학생 순서로 잠근다. 여기서 항목까지 잠그면 서로 기다릴 수 있다.
        recalculateLocked(requireStudent(studentId), false);
    }

    @Transactional(readOnly = true)
    public StudentStatusResult getStatus(Long studentId) {
        if (studentId == null || studentId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var row = mapper.findStudentStatus(studentId);
        if (row == null) throw new BusinessException(ErrorCode.STUDENT_NOT_FOUND);
        return new StudentStatusResult(row.getStudentId(), row.getStatus(), row.getSubStatus(),
                seoulTime(row.getUpdatedAt()));
    }

    /** 본인 의사를 이력으로 남긴다. 도움 요청의 종료·연락 완료 여부는 별도 API에서 선택한다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StudentStatusResult selectStatus(Long studentId, StudentStatusRequest request) {
        if (studentId == null || studentId <= 0 || request == null || request.status() == null
                || !SELECTABLE.contains(request.status())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        requireStudent(studentId);
        LocalDateTime selectedAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        var intent = new StatusData.Intent();
        intent.setStudentId(studentId);
        intent.setStatus(request.status());
        intent.setSelectedAt(selectedAt);
        if (mapper.insertIntent(intent) != 1) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        mapper.updateLastResponseAt(studentId, selectedAt);
        // 지원 저장은 항목 → 학생 순서다. 여기서는 학생만 잠그고 원문은 현재 커밋값으로 읽어 교착을 피한다.
        recalculateLocked(requireStudent(studentId), false);
        // 재선택으로 판정 값이 같으면 DB updated_at도 그대로다. GET과 같은 저장 값을 반환한다.
        return getStatus(studentId);
    }

    private StatusData.Student requireStudent(Long studentId) {
        StatusData.Student student = mapper.lockStudent(studentId);
        if (student == null) throw new BusinessException(ErrorCode.STUDENT_NOT_FOUND);
        return student;
    }

    private StatusDecision recalculateLocked(StatusData.Student student, boolean lockingReads) {
        Long studentId = student.getStudentId();
        String intent = null;
        LocalDateTime intentAt = null;
        boolean foundPass = false;
        String latestPassReason = null;
        var responses = lockingReads ? mapper.findResponses(studentId) : mapper.findResponsesUnlocked(studentId);
        for (StatusData.Response response : responses) {
            // 매퍼의 응답 시각 DESC · 항목 번호 DESC 순서에서 가장 최근의 명확한 의사를 쓴다.
            // APPLY 저장 성공이 구직 재개의 근거이며, 이후 AI 생성·서류 검토 단계와는 구분한다.
            if (intent == null && "APPLY".equals(response.getResponse())
                    && response.getRespondedAt() != null) {
                intent = "SEEKING";
                intentAt = response.getRespondedAt();
            }
            if (!"PASS".equals(response.getResponse())) continue;
            if (intent == null && ("ALREADY_EMPLOYED".equals(response.getPassReason())
                    || "TAKING_BREAK".equals(response.getPassReason()))) {
                intent = "ALREADY_EMPLOYED".equals(response.getPassReason()) ? "EMPLOYED" : "RESTING";
                intentAt = response.getRespondedAt();
            }
            if (!foundPass) {
                latestPassReason = response.getPassReason();
                foundPass = true;
            }
        }
        var explicit = lockingReads ? mapper.findLatestIntent(studentId) : mapper.findLatestIntentUnlocked(studentId);
        if (explicit != null && (intentAt == null || !explicit.getSelectedAt().isBefore(intentAt))) {
            intent = explicit.getStatus();
        }
        var openHelp = lockingReads ? mapper.findOpenHelpRequestIds(studentId)
                : mapper.findOpenHelpRequestIdsUnlocked(studentId);
        var activeApplications = lockingReads ? mapper.findActiveApplicationIds(studentId)
                : mapper.findActiveApplicationIdsUnlocked(studentId);
        var facts = new StatusRules.Facts(
                "EMPLOYED".equals(intent), !openHelp.isEmpty(), !activeApplications.isEmpty(),
                student.getInactivitySince(), latestPassReason, latestRecommendationEmpty(studentId, lockingReads), intent);
        StatusDecision decision = StatusRules.decide(facts, LocalDateTime.now(clock));
        mapper.upsertStatus(studentId, decision);
        return decision;
    }

    private static OffsetDateTime seoulTime(LocalDateTime time) {
        return time == null ? null : time.atZone(SEOUL).toOffsetDateTime();
    }

    private boolean latestRecommendationEmpty(Long studentId, boolean lockingReads) {
        Long recommendationId = lockingReads ? mapper.findLatestRecommendationId(studentId)
                : mapper.findLatestRecommendationIdUnlocked(studentId);
        if (recommendationId == null) return false;
        return (lockingReads ? mapper.findRecommendationItemIds(recommendationId)
                : mapper.findRecommendationItemIdsUnlocked(recommendationId)).isEmpty();
    }
}
