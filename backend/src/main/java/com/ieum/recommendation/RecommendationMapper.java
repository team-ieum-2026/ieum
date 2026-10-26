// 주인: BE-A
package com.ieum.recommendation;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** recommendation · recommendation_item 매퍼. SQL은 resources/mapper/recommendation/RecommendationMapper.xml */
@Mapper
public interface RecommendationMapper {

    /** 수료생의 가장 최근 추천 묶음 (없으면 null) */
    RecommendationRows.Header selectLatest(@Param("studentId") Long studentId);

    /** 추천 묶음의 항목 + 공고 요약, rank 순 */
    List<RecommendationRows.Item> selectItems(@Param("recommendationId") Long recommendationId);

    /** 수료생이 이미 응답(APPLY · PASS · HELP)한 공고 id. 새로 추천받기에서 제외 */
    List<Long> selectRespondedJobIds(@Param("studentId") Long studentId);

    /** 주어진 공고 중 실제로 있고 활성인 것만 */
    List<Long> selectActiveJobIds(@Param("jobPostingIds") List<Long> jobPostingIds);

    /** AI 고정 응답용: 제외 목록 밖의 활성 공고를 최근 등록 순으로 limit 개 */
    List<Long> selectRecentActiveJobIds(@Param("excludeIds") List<Long> excludeIds, @Param("limit") int limit);

    int insertRecommendation(RecommendationRows.NewRecommendation recommendation);

    int insertItems(@Param("items") List<RecommendationRows.NewItem> items);
}
