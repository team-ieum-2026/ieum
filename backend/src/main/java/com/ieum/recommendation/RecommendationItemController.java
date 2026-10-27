// 주인: BE-A
package com.ieum.recommendation;

import com.ieum.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** M2 추천 공고 하나 (BE-A-05). itemId 는 M1 응답의 items[].itemId */
@Tag(name = "추천 공고", description = "M2 공고 상세")
@RestController
@RequestMapping("/api/recommendation-items/{itemId}")
public class RecommendationItemController {

    private final RecommendationItemService recommendationItemService;

    public RecommendationItemController(RecommendationItemService recommendationItemService) {
        this.recommendationItemService = recommendationItemService;
    }

    @Operation(summary = "추천 공고 상세", description = "공고 상세 + 추천 근거 전체. 근거는 수료생 등록 자료에서 출처 · 원문을 채운다. 없는 항목이면 404.")
    @GetMapping
    public ApiResponse<RecommendationItemDetailResponse> detail(
            @Parameter(description = "추천 항목 번호", example = "91") @PathVariable("itemId") Long itemId) {
        return ApiResponse.of(recommendationItemService.getDetail(itemId));
    }
}
