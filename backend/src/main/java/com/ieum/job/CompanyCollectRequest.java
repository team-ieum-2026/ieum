// 주인: BE-A
package com.ieum.job;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** POST /api/admin/companies/collect 요청. companyNames 는 job_posting.company_name 과 같은 표기로 적는다. */
public record CompanyCollectRequest(
        @NotEmpty(message = "수집할 기업명을 하나 이상 적어 주세요.") List<String> companyNames
) {
}
