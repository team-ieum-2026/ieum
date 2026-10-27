// 주인: BE-A
package com.ieum.job;

import com.ieum.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 시연 기업 정보 수집 (시연 전 1회 · 개발용, P0 인증 없음). docs/API.md 2-1 */
@Tag(name = "admin-companies", description = "시연 기업 정보 수집 (BE-A-10)")
@RestController
@RequestMapping("/api/admin/companies")
public class CompanyInfoController {

    private final CompanyInfoService companyInfoService;

    public CompanyInfoController(CompanyInfoService companyInfoService) {
        this.companyInfoService = companyInfoService;
    }

    @Operation(summary = "시연 기업 정보 수집",
            description = "기업마다 AI POST /ai/company/collect 를 불러 company_info 에 저장한다. 같은 기업은 덮어쓰고, 한 기업이 실패해도 나머지는 계속한다.")
    @PostMapping("/collect")
    public ApiResponse<CompanyCollectResult> collect(@Valid @RequestBody CompanyCollectRequest request) {
        return ApiResponse.of(companyInfoService.collect(request.companyNames()));
    }
}
