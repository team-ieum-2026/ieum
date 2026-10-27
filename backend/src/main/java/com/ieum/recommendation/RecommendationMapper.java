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

    /** M2 상세용 추천 항목 하나 (없으면 null) */
    RecommendationRows.ItemDetail selectItemDetail(@Param("itemId") Long itemId);

    /** 응답 저장 전 확인용 (없으면 null) */
    RecommendationRows.ItemOwner selectItemOwner(@Param("itemId") Long itemId);

    /** 아직 NONE 인 항목만 응답으로 바꾼다. 0 이면 이미 응답한 항목 */
    int updateItemResponse(@Param("itemId") Long itemId, @Param("response") String response,
                           @Param("passReason") String passReason, @Param("passNote") String passNote);

    /** student.last_response_at = 지금 (1주 무응답 판정 기준) */
    int touchLastResponse(@Param("studentId") Long studentId);

    /** 지원 시 application 생성 (GENERATING). AI 서류 생성은 BE-A-07 이 이어서 한다 */
    int insertApplication(RecommendationRows.NewApplication application);

    /** 수료생이 이미 응답(APPLY · PASS · HELP)한 공고 id. 새로 추천받기에서 제외 */
    List<Long> selectRespondedJobIds(@Param("studentId") Long studentId);

    /** 주어진 공고 중 실제로 있고 활성인 것만 */
    List<Long> selectActiveJobIds(@Param("jobPostingIds") List<Long> jobPostingIds);

    /** AI 고정 응답용: 제외 목록 밖의 활성 공고를 최근 등록 순으로 limit 개 */
    List<Long> selectRecentActiveJobIds(@Param("excludeIds") List<Long> excludeIds, @Param("limit") int limit);

    /** 전체 수료생 id. 공고 배치 뒤 추천 미리 만들기용 */
    List<Long> selectStudentIds();

    int insertRecommendation(RecommendationRows.NewRecommendation recommendation);

    int insertItems(@Param("items") List<RecommendationRows.NewItem> items);
}
