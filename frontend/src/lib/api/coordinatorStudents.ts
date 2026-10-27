// 주인: FE-A — 코디네이터 수료생 목록 · 상세 API (C2 · C3). 경로 · 형식은 docs/API.md 2-3
import { ApiError, apiGet, apiPost } from "@/lib/api/client";
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
export const getCoordinatorStudents = async (query: CoordinatorStudentListQuery = {}) => {
  const result = await apiGet<CoordinatorStudentList>(`/api/coordinator/students${toQueryString(query)}`, "C2_coordinator_students.json");
  if (process.env.NEXT_PUBLIC_USE_MOCK === "false") return result;
  // 정적 JSON은 쿼리를 처리하지 못하므로 목업 모드에서만 필터·페이지를 적용한다.
  const items = result.items.filter((student) =>
    (!query.status || student.status === query.status) &&
    (!query.subStatus || student.subStatus === query.subStatus) &&
    (!query.q || student.name.includes(query.q)),
  );
  const page = query.page ?? 1;
  const size = query.size ?? 20;
  return { items: items.slice((page - 1) * size, page * size), total: items.length, page, size };
};

/** C3 수료생 상세 */
export const getCoordinatorStudent = async (studentId: number): Promise<CoordinatorStudentDetail> => {
  const result = await apiGet<CoordinatorStudentDetail>(`/api/coordinator/students/${studentId}`, "C3_coordinator_student_detail.json");
  if (process.env.NEXT_PUBLIC_USE_MOCK === "false" || result.student.studentId === studentId) return result;
  const list = await getCoordinatorStudents();
  const student = list.items.find((item) => item.studentId === studentId);
  if (!student) throw new ApiError("STUDENT_NOT_FOUND", "수료생 정보를 찾을 수 없어요.", 404);
  // 상세 JSON이 없는 수료생에게 다른 사람의 프로필·이력을 보여주지 않는다.
  return {
    student: { ...student, email: null, desiredJob: null, preferredRegion: null, reason: null },
    competencyProfile: null,
    registered: { education: 0, certificates: 0, projects: 0, hasCoverLetter: false },
    history: [],
    contactLogs: [],
  };
};

/** C1 · C3 통화 · 상담 기록 추가 */
export const addContactLog = async (studentId: number, body: ContactLogCreateRequest) => {
  const result = await apiPost<ContactLog>(`/api/coordinator/students/${studentId}/contact-logs`, "C3_contact_log_create.json", body);
  return process.env.NEXT_PUBLIC_USE_MOCK === "false" ? result : { ...result, ...body };
};
