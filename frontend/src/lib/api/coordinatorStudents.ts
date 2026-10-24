// 주인: FE-A — 코디네이터 수료생 목록 · 상세 API (C2 · C3). 경로 · 형식은 docs/API.md 2-3
import { apiGet, apiPost } from "@/lib/api/client";
import type {
  ContactLog,
  ContactLogCreateRequest,
  CoordinatorStudentDetail,
  CoordinatorStudentList,
  CoordinatorStudentListQuery,
} from "@/types/student";

function toQueryString(query: CoordinatorStudentListQuery): string {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== "") params.set(key, String(value));
  });
  const qs = params.toString();
  return qs ? `?${qs}` : "";
}

/** C2 수료생 목록 (status · subStatus · q · page · size) */
export const getCoordinatorStudents = (query: CoordinatorStudentListQuery = {}) =>
  apiGet<CoordinatorStudentList>(`/api/coordinator/students${toQueryString(query)}`, "C2_coordinator_students.json");

/** C3 수료생 상세 */
export const getCoordinatorStudent = (studentId: number) =>
  apiGet<CoordinatorStudentDetail>(`/api/coordinator/students/${studentId}`, "C3_coordinator_student_detail.json");

/** C1 · C3 통화 · 상담 기록 추가 */
export const addContactLog = (studentId: number, body: ContactLogCreateRequest) =>
  apiPost<ContactLog>(`/api/coordinator/students/${studentId}/contact-logs`, "C3_contact_log_create.json", body);
