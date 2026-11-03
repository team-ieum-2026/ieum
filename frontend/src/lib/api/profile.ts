// 주인: FE-B — 내 프로필 API (M8). 경로 · 형식은 docs/API.md 2-2
import { ApiError, apiDelete, apiGet, apiPatch, apiPost, apiPut } from "@/lib/api/client";
import type { SectionName } from "@/types/resume";
import type { CoordinatorStudentList } from "@/types/student";
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

const mockStatusKey = (studentId: number) => `ieum:mock-student-status:${studentId}`;

export async function getStudentStatus(studentId: number) {
  if (process.env.NEXT_PUBLIC_USE_MOCK !== "false") {
    const saved = localStorage.getItem(mockStatusKey(studentId));
    if (saved) return JSON.parse(saved) as StudentCurrentStatus;
    const list = await apiGet<CoordinatorStudentList>("", "C2_coordinator_students.json");
    const student = list.items.find(item => item.studentId === studentId);
    // 이전 목업의 NEED_HELP는 현재 상태와 분리된 도움 요청 표시였다.
    const status = student?.status === "NEED_HELP" ? "SEEKING" : student?.status ?? "CHECK_REQUIRED";
    const result: StudentCurrentStatus = { studentId, status, subStatus: status === "SEEKING" ? student?.subStatus ?? "WAITING" : null,
      updatedAt: student?.lastResponseAt ?? null };
    return result;
  }
  return apiGet<StudentCurrentStatus>(`/api/students/${studentId}/status`, "");
}

export async function selectStudentStatus(studentId: number, status: SelectableStudentStatus) {
  if (process.env.NEXT_PUBLIC_USE_MOCK !== "false") {
    if (!["EMPLOYED", "SEEKING", "RESTING"].includes(status)) {
      throw new ApiError("INVALID_REQUEST", "현재 상태를 확인해주세요.", 400);
    }
    const current = await getStudentStatus(studentId);
    const result: StudentCurrentStatus = { studentId, status,
      subStatus: status === "SEEKING" ? current.subStatus ?? "WAITING" : null, updatedAt: new Date().toISOString() };
    localStorage.setItem(mockStatusKey(studentId), JSON.stringify(result));
    return result;
  }
  return apiPut<StudentCurrentStatus>(`/api/students/${studentId}/status`, "", { status });
}
