// 주인: FE-A — 수료생 흐름 API (M1 ~ M5, M7). 경로 · 형식은 docs/API.md 2-1 · 2-2
import { apiGet, apiPatch, apiPost } from "@/lib/api/client";
import type {
  ApplicationProgress,
  HelpRequestCreateRequest,
  HelpRequestCreateResult,
  HelpRequestUpdateRequest,
  HelpRequestUpdateResult,
  ItemResponseRequest,
  ItemResponseResult,
  RecommendationItemDetail,
  Recommendations,
  StudentApplications,
} from "@/types/student";

/** M1 현재 추천 3건 (없으면 서버가 새로 만듦) */
export const getCurrentRecommendations = (studentId: number) =>
  apiGet<Recommendations>(`/api/students/${studentId}/recommendations/current`, "M1_recommendations_current.json");

/** M1 새로 추천받기 */
export const refreshRecommendations = (studentId: number) =>
  apiPost<Recommendations>(`/api/students/${studentId}/recommendations/refresh`, "M1_recommendations_refresh.json");

/** M2 공고 상세 + 추천 근거 */
export const getRecommendationItem = (itemId: number) =>
  apiGet<RecommendationItemDetail>(`/api/recommendation-items/${itemId}`, "M2_recommendation_item_detail.json");

/** M2 지원 · M3 패스 응답 */
export const respondToItem = (itemId: number, body: ItemResponseRequest) =>
  apiPost<ItemResponseResult>(
    `/api/recommendation-items/${itemId}/response`,
    body.response === "APPLY" ? "M2_response_apply.json" : "M3_response_pass.json",
    body,
  );

/** M2 도움 요청 생성 → M4 */
export const createHelpRequest = (body: HelpRequestCreateRequest) =>
  apiPost<HelpRequestCreateResult>("/api/help-requests", "M4_help_request_create.json", body);

/** M4 연락 방법 · 메모 추가 */
export const updateHelpRequest = (helpRequestId: number, body: HelpRequestUpdateRequest) =>
  apiPatch<HelpRequestUpdateResult>(`/api/help-requests/${helpRequestId}`, "M4_help_request_update.json", body);

/** M5 서류 생성 진행 상태 (2초마다 조회) */
export const getApplicationProgress = (applicationId: number) =>
  apiGet<ApplicationProgress>(`/api/applications/${applicationId}/progress`, "M5_application_progress_done.json");

/** M7 지원 현황 */
export const getStudentApplications = (studentId: number) =>
  apiGet<StudentApplications>(`/api/students/${studentId}/applications`, "M7_student_applications.json");
