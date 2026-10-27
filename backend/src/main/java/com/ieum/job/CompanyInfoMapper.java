// 주인: BE-A
package com.ieum.job;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** company_info 매퍼. SQL은 resources/mapper/job/CompanyInfoMapper.xml */
@Mapper
public interface CompanyInfoMapper {

    /** company_name 기준 insert 또는 update (다시 수집하면 덮어쓰고 collected_at 갱신) */
    int upsert(CompanyInfo companyInfo);

    /** 기업명이 정확히 같은 한 건. 없으면 null (서류 생성 때 companyInfo: null) */
    CompanyInfo selectByCompanyName(@Param("companyName") String companyName);
}
