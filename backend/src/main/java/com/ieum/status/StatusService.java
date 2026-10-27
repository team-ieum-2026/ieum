// 주인: BE-B
package com.ieum.status;

import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    private StatusData.Student requireStudent(Long studentId) {
        StatusData.Student student = mapper.lockStudent(studentId);
        if (student == null) throw new BusinessException(ErrorCode.STUDENT_NOT_FOUND);
        return student;
    }

    private void recalculateLocked(StatusData.Student student, boolean lockingReads) {
        Long studentId = student.getStudentId();
        boolean employed = false;
        boolean foundPass = false;
        String latestPassReason = null;
        var responses = lockingReads ? mapper.findResponses(studentId) : mapper.findResponsesUnlocked(studentId);
        for (StatusData.Response response : responses) {
            if (!"PASS".equals(response.getResponse())) continue;
            if ("ALREADY_EMPLOYED".equals(response.getPassReason())) employed = true;
            if (!foundPass) {
                latestPassReason = response.getPassReason();
                foundPass = true;
            }
        }
        var openHelp = lockingReads ? mapper.findOpenHelpRequestIds(studentId)
                : mapper.findOpenHelpRequestIdsUnlocked(studentId);
        var activeApplications = lockingReads ? mapper.findActiveApplicationIds(studentId)
                : mapper.findActiveApplicationIdsUnlocked(studentId);
        var facts = new StatusRules.Facts(
                employed, !openHelp.isEmpty(), !activeApplications.isEmpty(),
                student.getInactivitySince(), latestPassReason, latestRecommendationEmpty(studentId, lockingReads));
        StatusDecision decision = StatusRules.decide(facts, LocalDateTime.now(clock));
        mapper.upsertStatus(studentId, decision);
    }

    private boolean latestRecommendationEmpty(Long studentId, boolean lockingReads) {
        Long recommendationId = lockingReads ? mapper.findLatestRecommendationId(studentId)
                : mapper.findLatestRecommendationIdUnlocked(studentId);
        if (recommendationId == null) return false;
        return (lockingReads ? mapper.findRecommendationItemIds(recommendationId)
                : mapper.findRecommendationItemIdsUnlocked(recommendationId)).isEmpty();
    }
}
