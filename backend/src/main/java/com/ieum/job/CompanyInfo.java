// 주인: BE-A
package com.ieum.job;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * company_info 테이블 한 줄 (db/schema.sql). 공고와 company_name 으로 연결돼 job 패키지에 둔다.
 * info: { talentKeywords, coreValues, recentIssue, sources[{item, url, collectedAt}] } (ai/schemas/company.py)
 */
@Getter
@Setter
@NoArgsConstructor
public class CompanyInfo {

    private Long id;
    private String companyName;
    /** FOUND | PARTIAL | NOT_FOUND */
    private String status;
    private JsonNode info;
    private LocalDateTime collectedAt;
}
