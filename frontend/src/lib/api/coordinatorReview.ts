// 주인: FE-B — 코디네이터 대시보드 · 서류 검토 API (C1 · C4). 경로 · 형식은 docs/API.md 2-3
import { ApiError, apiGet, apiPatch, apiPost } from "@/lib/api/client";
import type {
  ApplicationStatusResult,
  CoordinatorApplication,
  Dashboard,
  HelpRequestStatusUpdateRequest,
  HelpRequestStatusUpdateResult,
  RejectRequest,
} from "@/types/coordinator";

/** C1 대시보드: 도움 요청, 상태별 집계, 할 일 3종 */
export const getDashboard = () => apiGet<Dashboard>("/api/coordinator/dashboard", "C1_coordinator_dashboard.json");

/** C1 도움 요청 처리 */
export const updateHelpRequestStatus = (helpRequestId: number, body: HelpRequestStatusUpdateRequest) =>
  apiPatch<HelpRequestStatusUpdateResult>(
    `/api/coordinator/help-requests/${helpRequestId}`,
    "C1_help_request_update.json",
    body,
  );

/** C4 서류 + 검증 요약 */
export const getCoordinatorApplication = async (applicationId: number): Promise<CoordinatorApplication> => {
  const result = await apiGet<CoordinatorApplication>(`/api/coordinator/applications/${applicationId}`, "C4_coordinator_application.json");
  if (process.env.NEXT_PUBLIC_USE_MOCK === "false" || result.applicationId === applicationId) return result;
  // 목업 JSON 은 서류 1건뿐이라, 다른 번호를 열면 다른 수료생의 서류를 보여 주지 않고 '없음'으로 처리한다
  throw new ApiError("APPLICATION_NOT_FOUND", "목업 데이터에 없는 지원서류예요.", 404);
};

/** C4 승인 (SUBMITTED → APPROVED) */
export const approveApplication = (applicationId: number) =>
  apiPost<ApplicationStatusResult>(`/api/coordinator/applications/${applicationId}/approve`, "C4_approve.json");

/** C4 반려 (→ REJECTED) */
export const rejectApplication = (applicationId: number, body: RejectRequest) =>
  apiPost<ApplicationStatusResult>(`/api/coordinator/applications/${applicationId}/reject`, "C4_reject.json", body);

/** C4 전달 완료 (APPROVED → DELIVERED) */
export const deliverApplication = (applicationId: number) =>
  apiPost<ApplicationStatusResult>(`/api/coordinator/applications/${applicationId}/deliver`, "C4_deliver.json");
