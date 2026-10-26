// 주인: BE-A
package com.ieum.recommendation;

import com.ieum.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** M1 맞춤 공고 (BE-A-04). 수료생은 항상 경로의 studentId 로 구분한다 (BE-A-01). */
@Tag(name = "수료생 추천", description = "M1 맞춤 공고 · 새로 추천받기")
@RestController
@RequestMapping("/api/students/{studentId}/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @Operation(summary = "현재 추천 3건", description = "최근 추천이 있으면 그대로, 없으면 AI 매칭을 불러 새로 만든다. 맞는 공고가 없으면 items: [].")
    @GetMapping("/current")
    public ApiResponse<RecommendationResponse> current(
            @Parameter(description = "수료생 번호", example = "21") @PathVariable("studentId") Long studentId) {
        return ApiResponse.of(recommendationService.getCurrent(studentId));
    }

    @Operation(summary = "새로 추천받기", description = "이미 응답한 공고를 빼고 AI 매칭을 다시 불러 새 추천을 만든다.")
    @PostMapping("/refresh")
    public ApiResponse<RecommendationResponse> refresh(
            @Parameter(description = "수료생 번호", example = "21") @PathVariable("studentId") Long studentId) {
        return ApiResponse.of(recommendationService.refresh(studentId));
    }
}
