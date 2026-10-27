// 주인: BE-A
package com.ieum.recommendation;

import com.ieum.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** M2 · M3 추천 공고 하나 (BE-A-05 · 06). itemId 는 M1 응답의 items[].itemId */
@Tag(name = "추천 공고", description = "M2 공고 상세 · 지원 · M3 패스")
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

    @Operation(summary = "지원 · 패스 응답", description = "APPLY면 지원서를 만들고 applicationId를 돌려준다(M5 이동). PASS면 passReason 필수. 이미 응답한 항목이면 409.")
    @PostMapping("/response")
    public ApiResponse<ItemResponseResult> respond(
            @Parameter(description = "추천 항목 번호", example = "91") @PathVariable("itemId") Long itemId,
            @RequestBody ItemResponseRequest request) {
        return ApiResponse.of(recommendationItemService.respond(itemId, request));
    }
}
