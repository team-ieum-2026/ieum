// 주인: BE-A
package com.ieum.application;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 서류 조회 · 수정 · 제출 (BE-A-08). SQL은 resources/mapper/application/ApplicationDocumentMapper.xml */
@Mapper
public interface ApplicationDocumentMapper {

    /** 지원서 + 공고 요약 + 서류 JSON 문자열 (없으면 null) */
    Row selectDetail(@Param("applicationId") Long applicationId);

    /** 수정 전에 잠그고 읽는다 (같은 서류를 동시에 고쳐도 한쪽 수정이 사라지지 않게) */
    Row selectDetailForUpdate(@Param("applicationId") Long applicationId);

    int updateDocument(@Param("applicationId") Long applicationId, @Param("document") String document);

    /** DRAFT 일 때만 SUBMITTED 로. 0 이면 상태가 맞지 않음 */
    int submit(@Param("applicationId") Long applicationId);

    int touchLastResponse(@Param("studentId") Long studentId);

    @Getter
    @Setter
    @NoArgsConstructor
    class Row {
        private Long applicationId;
        private Long studentId;
        private String status;
        private Long jobPostingId;
        private String companyName;
        private String title;
        private String document;
    }
}
