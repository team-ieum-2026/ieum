// 주인: FE-B — 내 프로필 API (M8). 경로 · 형식은 docs/API.md 2-2
import { ApiError, apiDelete, apiGet, apiPatch, apiPost, apiPut } from "@/lib/api/client";
import type { SectionName } from "@/types/resume";
import type { CoverLetterUpdateRequest, ProfileItemRequest, SelectableStudentStatus, StudentCurrentStatus, StudentProfile } from "@/types/profile";

/** M8 등록 자료 전체 + 역량 프로필 */
export const getProfile = (studentId: number) =>
  apiGet<StudentProfile>(`/api/students/${studentId}/profile`, "M8_student_profile.json");

/** M8 프로필 섹션 항목 추가 */
export const addProfileItem = (studentId: number, body: ProfileItemRequest) =>
  apiPost<StudentProfile>(`/api/students/${studentId}/profile/items`, "M8_student_profile.json", body);

/** M8 프로필 섹션 항목 수정 */
export const updateProfileItem = (studentId: number, itemId: number, body: ProfileItemRequest) =>
  apiPatch<StudentProfile>(`/api/students/${studentId}/profile/items/${itemId}`, "M8_student_profile.json", body);

/** M8 프로필 섹션 항목 삭제 */
export const deleteProfileItem = (studentId: number, itemId: number, section: SectionName) =>
  apiDelete<StudentProfile>(
    `/api/students/${studentId}/profile/items/${itemId}?section=${section}`,
    "M8_student_profile.json",
  );

/** M8 자기소개서 원본 수정 */
export const updateCoverLetter = (studentId: number, body: CoverLetterUpdateRequest) =>
  apiPut<StudentProfile>(`/api/students/${studentId}/cover-letter`, "M8_student_profile.json", body);

/** 기존 상태 API의 실제 저장값. 상태 미리보기나 로컬 캐시를 사용하지 않는다. */
function requireLiveStatusApi() {
  if (process.env.NEXT_PUBLIC_USE_MOCK !== "false") {
    throw new ApiError("REAL_API_REQUIRED", "현재 상태를 확인하려면 실제 서버에 연결해주세요.", 0);
  }
}

export async function getStudentStatus(studentId: number) {
  requireLiveStatusApi();
  return apiGet<StudentCurrentStatus>(`/api/students/${studentId}/status`, "");
}

export async function selectStudentStatus(studentId: number, status: SelectableStudentStatus) {
  requireLiveStatusApi();
  return apiPut<StudentCurrentStatus>(`/api/students/${studentId}/status`, "", { status });
}
