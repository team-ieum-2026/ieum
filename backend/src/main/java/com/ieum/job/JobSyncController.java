// 주인: BE-A
package com.ieum.job;

import com.ieum.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 공고 배치 수동 실행 (시연 · 개발용, P0 인증 없음). docs/API.md 2-1 */
@Tag(name = "admin-jobs", description = "공고 배치 (BE-A-02)")
@RestController
@RequestMapping("/api/admin/jobs")
public class JobSyncController {

    private final JobSyncService jobSyncService;

    public JobSyncController(JobSyncService jobSyncService) {
        this.jobSyncService = jobSyncService;
    }

    @Operation(summary = "공고 배치 수동 실행", description = "서울 열린데이터광장 recMntList 전체를 받아 IT 직종만 저장하고, 바뀐 공고만 AI 색인을 갱신한다. 1~2분 걸릴 수 있다.")
    @PostMapping("/sync")
    public ApiResponse<JobSyncResult> sync() {
        return ApiResponse.of(jobSyncService.sync());
    }

    @Operation(summary = "AI 전체 재색인", description = "활성 공고 전체와 꺼진 공고 id를 AI 색인에 다시 보내고, 0건 추천을 다시 만든다. AI 서버를 처음 연결할 때 한 번 실행한다.")
    @PostMapping("/reindex")
    public ApiResponse<JobReindexResult> reindex() {
        return ApiResponse.of(jobSyncService.reindex());
    }
}
