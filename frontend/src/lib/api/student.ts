// 주인: FE-A — 수료생 흐름 API (M1 ~ M5, M7). 경로 · 형식은 docs/API.md 2-1 · 2-2
import { ApiError, apiGet, apiPatch, apiPost, DEMO_STUDENT_ID } from "@/lib/api/client";
import { getProfile } from "@/lib/api/profile";
import type {
  ApplicationProgress,
  ApplicationListItem,
  ApplicationStatus,
  HelpRequestCreateRequest,
  HelpRequestCreateResult,
  HelpRequestUpdateRequest,
  HelpRequestUpdateResult,
  ItemResponseRequest,
  ItemResponseResult,
  RecommendationItemDetail,
  Recommendations,
  ResponseType,
  CoordinatorStudentList,
  StudentApplications,
} from "@/types/student";

/** 기존 프로필 API로 이름 조회. 목업에서 다른 학생의 고정 이름을 재사용하지 않는다. */
export async function getStudentName(studentId: number): Promise<string> {
  const profile = await getProfile(studentId);
  if (profile.studentId === studentId) return profile.basic.name;
  if (process.env.NEXT_PUBLIC_USE_MOCK !== "false") {
    const list = await apiGet<CoordinatorStudentList>("", "C2_coordinator_students.json");
    const student = list.items.find((item) => item.studentId === studentId);
    if (student) return student.name;
  }
  throw new ApiError("STUDENT_NOT_FOUND", "수료생 정보를 찾을 수 없어요. ID를 확인해 주세요.", 404);
}

const mockApplicationsKey = "ieum:mock-applications:v1";
const mockPassesKey = "ieum:mock-passes:v1";
const mockRecommendationsKey = (studentId: number) => `ieum:mock-recommendations:${studentId}`;
const applicationsChanged = "ieum:applications-changed";
type MockApplication = ApplicationListItem & { studentId: number; itemId: number };
type MockPass = { studentId: number; itemId: number; passReason: Extract<ItemResponseRequest, { response: "PASS" }>["passReason"] };

function readMockPasses(): MockPass[] {
  return JSON.parse(localStorage.getItem(mockPassesKey) ?? "[]") as MockPass[];
}

function readMockApplications(): MockApplication[] {
  const value = localStorage.getItem(mockApplicationsKey);
  return value ? JSON.parse(value) as MockApplication[] : [];
}

/** 실제 API 모드에서는 서버가 내려준 응답 상태를 그대로 사용한다. */
export function getMockRecommendationResponse(studentId: number, itemId: number, fallback: ResponseType): ResponseType {
  if (process.env.NEXT_PUBLIC_USE_MOCK === "false") return fallback;
  if (readMockApplications().some((item) => item.studentId === studentId && item.itemId === itemId)) return "APPLY";
  if (readMockPasses().some((item) => item.studentId === studentId && item.itemId === itemId)) return "PASS";
  return fallback;
}

function writeMockApplications(applications: MockApplication[]) {
  localStorage.setItem(mockApplicationsKey, JSON.stringify(applications));
  window.dispatchEvent(new Event(applicationsChanged));
}

/** 목업에서 M6 제출 · C4 승인/반려/전달 성공 후 호출하는 공통 상태 갱신 함수. */
export function updateMockApplicationStatus(applicationId: number, status: ApplicationStatus) {
  if (process.env.NEXT_PUBLIC_USE_MOCK === "false") return;
  const applications = readMockApplications();
  const application = applications.find((item) => item.applicationId === applicationId);
  if (!application) return;
  application.status = status;
  if (status === "SUBMITTED" && !application.submittedAt) application.submittedAt = new Date().toISOString();
  writeMockApplications(applications);
}

/** 같은 화면·다른 탭에서 상태가 바뀌거나 화면으로 복귀하면 목록을 다시 조회한다. */
export function subscribeApplicationChanges(listener: () => void) {
  const storageListener = (event: StorageEvent) => {
    if (event.key === mockApplicationsKey || event.key === mockPassesKey || event.key?.startsWith("ieum:mock-recommendations:") || event.key === null) listener();
  };
  const visibilityListener = () => {
    if (document.visibilityState === "visible") listener();
  };
  window.addEventListener(applicationsChanged, listener);
  window.addEventListener("storage", storageListener);
  window.addEventListener("focus", listener);
  document.addEventListener("visibilitychange", visibilityListener);
  return () => {
    window.removeEventListener(applicationsChanged, listener);
    window.removeEventListener("storage", storageListener);
    window.removeEventListener("focus", listener);
    document.removeEventListener("visibilitychange", visibilityListener);
  };
}

/** M1 현재 추천 3건 (없으면 서버가 새로 만듦) */
export const getCurrentRecommendations = async (studentId: number) => {
  if (process.env.NEXT_PUBLIC_USE_MOCK === "false") {
    return apiGet<Recommendations>(`/api/students/${studentId}/recommendations/current`, "M1_recommendations_current.json");
  }
  const saved = localStorage.getItem(mockRecommendationsKey(studentId));
  const result: Recommendations = saved ? JSON.parse(saved) as Recommendations
    : await apiGet<Recommendations>(`/api/students/${studentId}/recommendations/current`, "M1_recommendations_current.json");
  return { ...result, items: result.items.map((item) => ({ ...item, response: getMockRecommendationResponse(studentId, item.itemId, item.response) })) };
};

/** M1 새로 추천받기 */
export const refreshRecommendations = async (studentId: number) => {
  const result = await apiPost<Recommendations>(`/api/students/${studentId}/recommendations/refresh`, "M1_recommendations_refresh.json");
  if (process.env.NEXT_PUBLIC_USE_MOCK !== "false") {
    // 목업 후보가 한정되어 있으므로 응답한 공고를 다시 넣어 3개를 채우지 않는다.
    const original = await apiGet<Recommendations>("", "M1_recommendations_current.json");
    const answeredJobs = new Set([...original.items, ...result.items]
      .filter((item) => getMockRecommendationResponse(studentId, item.itemId, item.response) !== "NONE")
      .map((item) => item.job.jobPostingId));
    const filtered = { ...result, items: result.items
      .filter((item) => !answeredJobs.has(item.job.jobPostingId))
      .map((item, index) => ({ ...item, rank: index + 1 })) };
    localStorage.setItem(mockRecommendationsKey(studentId), JSON.stringify(filtered));
    window.dispatchEvent(new Event(applicationsChanged));
    return filtered;
  }
  return result;
};

/** M2 공고 상세 + 추천 근거 */
export const getRecommendationItem = async (itemId: number): Promise<RecommendationItemDetail> => {
  const result = await apiGet<RecommendationItemDetail>(`/api/recommendation-items/${itemId}`, "M2_recommendation_item_detail.json");
  if (process.env.NEXT_PUBLIC_USE_MOCK === "false" || result.itemId === itemId) return result;
  const recommendations = await apiGet<Recommendations>("", "M1_recommendations_current.json");
  let item = recommendations.items.find((item) => item.itemId === itemId);
  if (!item) {
    const refreshed = await apiGet<Recommendations>("", "M1_recommendations_refresh.json");
    item = refreshed.items.find((item) => item.itemId === itemId);
  }
  if (!item) throw new ApiError("NOT_FOUND", "추천 공고를 찾을 수 없어요.", 404);
  // 다른 공고의 상세 내용을 복사하지 않고 기존 추천 JSON의 정보만 표시한다.
  return {
    ...item,
    job: { ...item.job, jobCategory: "", workAddress: item.job.location, education: "", salaryText: "", postedOn: "", description: "", homepageUrl: null, source: result.job.source, sourceLabel: result.job.sourceLabel },
    summary: item.reasonSummary,
    reasons: item.reasonSummary.map((text) => ({ text, evidence: [] })),
  };
};

/** M2 지원 · M3 패스 응답 */
export const respondToItem = async (itemId: number, body: ItemResponseRequest, studentId = DEMO_STUDENT_ID) => {
  if (process.env.NEXT_PUBLIC_USE_MOCK === "false") {
    return apiPost<ItemResponseResult>(`/api/recommendation-items/${itemId}/response`,
      body.response === "APPLY" ? "M2_response_apply.json" : "M3_response_pass.json", body);
  }
  if (getMockRecommendationResponse(studentId, itemId, "NONE") !== "NONE") {
    throw new ApiError("ALREADY_RESPONDED", "이미 응답한 공고예요.", 409);
  }
  // 목업도 선택한 공고의 정보를 저장한다. 실패한 요청은 지원 목록에 추가하지 않는다.
  const detail = process.env.NEXT_PUBLIC_USE_MOCK !== "false" && body.response === "APPLY"
    ? await getRecommendationItem(itemId) : null;
  const result = await apiPost<ItemResponseResult>(
    `/api/recommendation-items/${itemId}/response`,
    body.response === "APPLY" ? "M2_response_apply.json" : "M3_response_pass.json",
    body,
  );
  if (process.env.NEXT_PUBLIC_USE_MOCK !== "false" && body.response === "PASS") {
    const passes = readMockPasses().filter((item) => item.studentId !== studentId || item.itemId !== itemId);
    passes.push({ studentId, itemId, passReason: body.passReason });
    localStorage.setItem(mockPassesKey, JSON.stringify(passes));
    window.dispatchEvent(new Event(applicationsChanged));
  }
  if (!detail || !result.applicationId) return result;
  const applications = readMockApplications();
  const existing = applications.find((item) => item.studentId === studentId && item.itemId === itemId);
  if (existing) return { ...result, itemId, applicationId: existing.applicationId };
  const applicationId = Math.max(result.applicationId - 1, ...applications.map((item) => item.applicationId)) + 1;
  applications.push({
    studentId, itemId, applicationId,
    companyName: detail.job.companyName, title: detail.job.title,
    status: "GENERATING", submittedAt: null, userEditedCount: 0,
  });
  writeMockApplications(applications);
  return { ...result, itemId, applicationId };
};

/** M2 도움 요청 생성 → M4 */
export const createHelpRequest = (body: HelpRequestCreateRequest) =>
  apiPost<HelpRequestCreateResult>("/api/help-requests", "M4_help_request_create.json", body);

/** M4 연락 방법 · 메모 추가 */
export const updateHelpRequest = (helpRequestId: number, body: HelpRequestUpdateRequest) =>
  apiPatch<HelpRequestUpdateResult>(`/api/help-requests/${helpRequestId}`, "M4_help_request_update.json", body);

/** M5 서류 생성 진행 상태 (2초마다 조회) */
const mockProgressStartedAt = new Map<number, number>();
export const getApplicationProgress = async (applicationId: number) => {
  if (process.env.NEXT_PUBLIC_USE_MOCK === "false") {
    return apiGet<ApplicationProgress>(`/api/applications/${applicationId}/progress`, "M5_application_progress_done.json");
  }
  const startedAt = mockProgressStartedAt.get(applicationId) ?? Date.now();
  mockProgressStartedAt.set(applicationId, startedAt);
  const result = await apiGet<ApplicationProgress>(`/api/applications/${applicationId}/progress`, Date.now() - startedAt < 1800 ? "M5_application_progress_generating.json" : "M5_application_progress_done.json");
  const application = readMockApplications().find((item) => item.applicationId === applicationId);
  if (application?.status === "GENERATING" && result.status !== "GENERATING") updateMockApplicationStatus(applicationId, result.status);
  return { ...result, applicationId };
};

/** M7 지원 현황 */
export const getStudentApplications = async (studentId: number): Promise<StudentApplications> => {
  if (process.env.NEXT_PUBLIC_USE_MOCK === "false") {
    return apiGet<StudentApplications>(`/api/students/${studentId}/applications`, "M7_student_applications.json");
  }
  const applications = readMockApplications().filter((item) => item.studentId === studentId);
  const passes = readMockPasses().filter((item) => item.studentId === studentId);
  const recommendations = await getCurrentRecommendations(studentId);
  return {
    applications: applications.map(({ applicationId, companyName, title, status, submittedAt, userEditedCount }) => ({ applicationId, companyName, title, status, submittedAt, userEditedCount })),
    otherItems: recommendations.items
      .filter((item) => item.response !== "APPLY" && !applications.some((application) => application.itemId === item.itemId))
      .map((item) => {
        const pass = passes.find((pass) => pass.itemId === item.itemId);
        return {
          itemId: item.itemId, companyName: item.job.companyName, title: item.job.title,
          response: pass ? "PASS" : item.response, passReason: pass?.passReason ?? null,
        };
      }),
  };
};
