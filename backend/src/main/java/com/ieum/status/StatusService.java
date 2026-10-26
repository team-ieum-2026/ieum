// 주인: BE-B
package com.ieum.status;

import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BE-A·도움 요청·코디네이터·일일 배치가 사용할 상태 재계산 계약(BE-B-06).
 * 본문은 아직 구현 전이며 실행 시 BusinessException으로 실패한다.
 * 두 메서드 모두 기본 REQUIRED 전파로 호출자의 트랜잭션에 참여한다.
 */
@Service
public class StatusService {

    /**
     * 지원·패스 응답 또는 서류 제출 저장 직후 호출하는 계약.
     * 구현 시 student.last_response_at 갱신과 상태 재계산을 함께 처리한다.
     *
     * @param studentId 수료생 번호
     * @param respondedAt 마지막 응답 시각(프로젝트의 서울 현지 시각)
     * @throws BusinessException 구현 시 없는 수료생은 STUDENT_NOT_FOUND로 전파한다.
     * @implNote 현재는 INTERNAL_ERROR로 실패하며 DB를 변경하지 않는다.
     */
    @Transactional
    public void recordResponse(Long studentId, LocalDateTime respondedAt) {
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "응답 기록·상태 재계산 서비스는 아직 구현되지 않았어요.");
    }

    /**
     * last_response_at을 변경하지 않고 상태만 재계산하는 계약.
     * 판정 순서는 schema.sql·DATA_SPEC.md의 EMPLOYED → NEED_HELP → CHECK_REQUIRED → SEEKING이다.
     *
     * @param studentId 수료생 번호
     * @throws BusinessException 구현 시 없는 수료생은 STUDENT_NOT_FOUND로 전파한다.
     * @implNote 현재는 INTERNAL_ERROR로 실패하며 DB를 변경하지 않는다.
     */
    @Transactional
    public void recalculate(Long studentId) {
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "상태 재계산 서비스는 아직 구현되지 않았어요.");
    }
}
