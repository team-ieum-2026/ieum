// 주인: FE-B — 코디네이터 대시보드 · 서류 검토 API (C1 · C4). 경로 · 형식은 docs/API.md 2-3
import { ApiError, apiGet, apiPatch, apiPost } from "@/lib/api/client";
import type {
  ApplicationStatusResult,
  CoordinatorApplication,
  CoordinatorApplicationFilter,
  CoordinatorApplicationListItem,
  CoordinatorHelpRequest,
  CoordinatorHelpRequestFilter,
  Dashboard,
  HelpRequestStatusUpdateRequest,
  HelpRequestStatusUpdateResult,
  RejectRequest,
} from "@/types/coordinator";

/** C1 대시보드: 도움 요청, 상태별 집계, 할 일 3종 */
export const getDashboard = () => apiGet<Dashboard>("/api/coordinator/dashboard", "C1_coordinator_dashboard.json");

const USE_MOCK = process.env.NEXT_PUBLIC_USE_MOCK !== "false";

/**
 * C4 서류 상태별 목록 (제출 순). 생략 · ALL 이면 SUBMITTED · REJECTED · APPROVED · DELIVERED 전체.
 * 목업 모드에는 이 목록 JSON 이 없어 대시보드 reviewQueue(검토 대기)만 같은 모양으로 바꿔 쓴다
 */
export const getCoordinatorApplications = async (
  status: CoordinatorApplicationFilter = "ALL",
): Promise<CoordinatorApplicationListItem[]> => {
  if (!USE_MOCK) return apiGet<CoordinatorApplicationListItem[]>(`/api/coordinator/applications?status=${status}`, "");
  const { reviewQueue } = await getDashboard();
  return reviewQueue
    .map((r) => ({
      applicationId: r.applicationId, studentId: 0, studentName: r.studentName, jobTitle: r.jobTitle,
      status: "SUBMITTED" as const, submittedAt: r.submittedAt, reviewedAt: null, deliveredAt: null,
      deadlineOn: r.deadlineOn ?? null, untilFilled: r.untilFilled ?? false,
    }))
    .filter((r) => status === "ALL" || r.status === status);
};

/**
 * 도움 요청 상태별 목록 (최근 순). 생략 · ALL 이면 OPEN · CONTACTED · CLOSED 전체.
 * 목업 모드에는 이 목록 JSON 이 없어 대시보드 helpRequests(접수)만 같은 모양으로 바꿔 쓴다
 */
export const getCoordinatorHelpRequests = async (
  status: CoordinatorHelpRequestFilter = "ALL",
): Promise<CoordinatorHelpRequest[]> => {
  if (!USE_MOCK) return apiGet<CoordinatorHelpRequest[]>(`/api/coordinator/help-requests?status=${status}`, "");
  const { helpRequests } = await getDashboard();
  return helpRequests
    .map((r) => ({ ...r, helpType: r.helpType ?? null, status: "OPEN" as const, closedAt: null, closeReason: null, consultationAt: null }))
    .filter((r) => status === "ALL" || r.status === status);
};

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
