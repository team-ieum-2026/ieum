// 주인: BE-A
package com.ieum.job;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml 의 app.seoul.* (서울 열린데이터광장 채용정보 API 설정) */
@ConfigurationProperties(prefix = "app.seoul")
public record SeoulJobProperties(
        String openapiKey,
        String baseUrl,
        String jobService,
        int pageSize,
        String syncCron,
        List<String> itJobCodes,
        List<String> allowedRegions
) {

    public SeoulJobProperties {
        itJobCodes = itJobCodes == null ? List.of() : List.copyOf(itJobCodes);
        allowedRegions = allowedRegions == null ? List.of() : List.copyOf(allowedRegions);
        if (pageSize <= 0) {
            pageSize = 1000;
        }
    }

    public boolean hasKey() {
        return openapiKey != null && !openapiKey.isBlank();
    }
}
