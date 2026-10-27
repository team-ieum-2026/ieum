// 주인: BE-B
package com.ieum.status;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 각 판정은 수료생 잠금을 잡은 같은 트랜잭션에서 현재 원문을 조회한다. */
@Mapper
public interface StatusMapper {

    StatusData.Student lockStudent(@Param("studentId") Long studentId);

    int updateLastResponseAt(@Param("studentId") Long studentId,
                             @Param("respondedAt") LocalDateTime respondedAt);

    /** 응답 시각 내림차순, 같은 시각이면 항목 번호 내림차순이다. */
    List<StatusData.Response> findResponses(@Param("studentId") Long studentId);

    List<Long> findOpenHelpRequestIds(@Param("studentId") Long studentId);

    List<Long> findActiveApplicationIds(@Param("studentId") Long studentId);

    /** 가장 최근 저장된 추천 묶음. 추천 이력이 없으면 null이다. */
    Long findLatestRecommendationId(@Param("studentId") Long studentId);

    /** 비활성 공고도 포함한다. 저장 당시 0건과 공고 비활성화를 구분한다. */
    List<Long> findRecommendationItemIds(@Param("recommendationId") Long recommendationId);

    // REQUIRES_NEW + READ_COMMITTED의 이벤트·배치 전용. 원문 행을 잠그지 않는다.
    List<StatusData.Response> findResponsesUnlocked(@Param("studentId") Long studentId);

    List<Long> findOpenHelpRequestIdsUnlocked(@Param("studentId") Long studentId);

    List<Long> findActiveApplicationIdsUnlocked(@Param("studentId") Long studentId);

    Long findLatestRecommendationIdUnlocked(@Param("studentId") Long studentId);

    List<Long> findRecommendationItemIdsUnlocked(@Param("recommendationId") Long recommendationId);

    /** 하루 한 번 응답 이력과 최신 추천 신호를 다시 판정할 수료생 번호. */
    List<Long> findStudentIds();

    int upsertStatus(@Param("studentId") Long studentId, @Param("decision") StatusDecision decision);
}
