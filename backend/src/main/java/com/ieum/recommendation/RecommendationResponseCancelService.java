// 주인: BE-A (통합 기간 BE-B 작성)
package com.ieum.recommendation;

import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import com.ieum.helprequest.HelpRequestService;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추천 공고 응답 취소 (M1 · M2, frontend/RESPONSE_CANCEL_PLAN.md).
 * - 추천 항목 → 지원서 · 도움 요청 순서로 잠근다 (지원 · 도움 요청 저장과 같은 순서)
 * - PASS: 사유 · 메모를 지우고 NONE
 * - APPLY: 검토 요청 전(GENERATING · DRAFT · FAILED) 지원서만 지우고 NONE. 생성 중이던 서류는 저장 직전 상태 확인으로 버려진다
 * - HELP: 접수(OPEN) 요청만 본인 종료로 닫아 이력을 남기고 NONE
 * - 이미 NONE 이면 그대로 성공. last_response_at 은 바꾸지 않는다 (취소도 수료생 활동이다)
 */
@Service
public class RecommendationResponseCancelService {

    private static final Set<String> CANCELLABLE = Set.of("APPLY", "PASS", "HELP");

    private final RecommendationMapper mapper;
    private final HelpRequestService helpRequests;
    private final ApplicationEventPublisher events;

    public RecommendationResponseCancelService(RecommendationMapper mapper, HelpRequestService helpRequests,
                                               ApplicationEventPublisher events) {
        this.mapper = mapper;
        this.helpRequests = helpRequests;
        this.events = events;
    }

    @Transactional
    public ItemResponseResult cancel(Long itemId, String expectedResponse) {
        if (expectedResponse == null || !CANCELLABLE.contains(expectedResponse)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "expectedResponse는 APPLY, PASS, HELP 중 하나여야 해요.");
        }
        RecommendationRows.ItemOwner item = mapper.selectItemOwnerForUpdate(itemId);
        if (item == null) {
            throw new BusinessException(ErrorCode.RECOMMENDATION_ITEM_NOT_FOUND);
        }
        if ("NONE".equals(item.getResponse())) {
            return new ItemResponseResult(itemId, "NONE", null);
        }
        if (!expectedResponse.equals(item.getResponse())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "공고 응답이 바뀌었어요. 새로고침 후 다시 확인해주세요.");
        }

        if ("APPLY".equals(expectedResponse)) {
            RecommendationRows.LockedApplication application = mapper.selectApplicationForUpdate(itemId);
            if (application != null && mapper.deleteApplicationBeforeReview(application.getId()) == 0) {
                throw new BusinessException(ErrorCode.INVALID_STATUS,
                        "이미 검토를 요청한 지원서는 직접 취소할 수 없어요. 담당자에게 문의해주세요.");
            }
        } else if ("HELP".equals(expectedResponse)) {
            helpRequests.closeForCancelledResponse(item.getStudentId(), itemId);
        }
        if (mapper.resetItemResponse(itemId, expectedResponse) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        events.publishEvent(new RecommendationResponseCancelledEvent(item.getStudentId(), itemId, expectedResponse));
        return new ItemResponseResult(itemId, "NONE", null);
    }
}
