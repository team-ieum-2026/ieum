// 주인: BE-A
package com.ieum.job;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** job_posting 매퍼. SQL은 resources/mapper/job/JobPostingMapper.xml */
@Mapper
public interface JobPostingMapper {

    /** 출처별 현재 공고의 (id, externalId, contentHash). upsert 전후 비교용 */
    List<JobPosting> selectKeysBySource(@Param("source") String source);

    /** 지금 활성인 공고 id (출처 무관). 배치 전후 비교로 AI 색인에서 지울 공고를 찾는다 */
    List<Long> selectActiveIds();

    /** 지금 꺼진 공고 id (출처 무관). 전체 재색인 때 AI 색인에서 지울 목록 */
    List<Long> selectInactiveIds();

    /** 활성 공고 전체의 색인 필드 (출처 무관). 전체 재색인용 */
    List<JobPosting> selectActiveForIndex();

    /** (source, external_id) 기준 insert 또는 update */
    int upsert(JobPosting posting);

    /** 이번 수집에 없는 공고를 끈다 */
    int deactivateMissing(@Param("source") String source, @Param("externalIds") List<String> externalIds);

    /** 마감일이 지난 공고를 끈다 */
    int deactivateExpired(@Param("source") String source, @Param("today") LocalDate today);

    /** 첫 배치 성공 뒤 개발용 임시 공고(DEV_SEED)를 끈다 */
    int deactivateDevSeed();
}
