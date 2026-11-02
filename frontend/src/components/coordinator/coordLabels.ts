// 주인: FE-B — 코디네이터 전용 이름표 · 색 · 집계 (11/1 개편). 서버 값은 그대로 두고 화면 이름만 정한다.
// 수료생 화면용 components/student/labels.ts 는 고치지 않는다 (applicationLabels 등은 수료생 문구).
import type { StatusSummary } from "@/types/coordinator";
import type { ApplicationStatus, ContactMethod, HelpRequestStatus, ResponseType, StudentStatus } from "@/types/student";

export type CoordTone = "green" | "orange" | "red" | "blue" | "gray";

/** 관리 상태 4분류 (서버 status 그대로) */
export const MANAGE_ORDER: StudentStatus[] = ["EMPLOYED", "SEEKING", "NEED_HELP", "CHECK_REQUIRED"];
export const manageLabels: Record<StudentStatus, string> = {
  EMPLOYED: "취업 완료",
  SEEKING: "구직 중",
  NEED_HELP: "도움 필요",
  CHECK_REQUIRED: "추가 확인 필요",
};
export const manageTone: Record<StudentStatus, CoordTone> = {
  EMPLOYED: "green",
  SEEKING: "blue",
  NEED_HELP: "red",
  CHECK_REQUIRED: "orange",
};
/** 관리 상태 막대 · 도넛 색 (coordinator.css 변수) */
export const manageChartVar: Record<StudentStatus, string> = {
  EMPLOYED: "var(--coord-chart-employed)",
  SEEKING: "var(--coord-chart-seeking)",
  NEED_HELP: "var(--coord-chart-help)",
  CHECK_REQUIRED: "var(--coord-chart-check)",
};

/** 서류 상태 (코디네이터 이름) — 검토 대기=주황 · 수정 요청=빨강 · 승인 · 전달 대기=파랑 · 전달 완료=초록 */
export const docLabels: Record<ApplicationStatus, string> = {
  GENERATING: "서류 생성 중",
  DRAFT: "초안 작성 중",
  SUBMITTED: "검토 대기",
  REJECTED: "수정 요청",
  APPROVED: "승인 · 전달 대기",
  DELIVERED: "전달 완료",
  FAILED: "생성 실패",
};
export const docTone: Record<ApplicationStatus, CoordTone> = {
  GENERATING: "gray",
  DRAFT: "gray",
  SUBMITTED: "orange",
  REJECTED: "red",
  APPROVED: "blue",
  DELIVERED: "green",
  FAILED: "gray",
};

/** 도움 요청 상태 — CONTACTED 는 '연락 완료'(요청 완료 아님). CLOSED 는 백엔드가 지원하면 추가 */
export const helpLabels: Record<HelpRequestStatus, string> = { OPEN: "접수", CONTACTED: "연락 완료" };
export const helpTone: Record<HelpRequestStatus, CoordTone> = { OPEN: "red", CONTACTED: "orange" };

/** 수료생이 고른 선호 연락 수단 */
export const contactLabels: Record<ContactMethod, string> = { PHONE: "전화", SMS: "문자", EMAIL: "이메일" };

/** 추천 공고 응답 */
export const responseTagLabels: Record<ResponseType, string> = { APPLY: "지원", PASS: "패스", NONE: "미응답", HELP: "도움 요청" };
export const responseTone: Record<ResponseType, CoordTone> = { APPLY: "green", PASS: "gray", NONE: "orange", HELP: "red" };

/** 교육과정 라벨 끝의 ' N기'를 뗀다 ("청년취업사관학교 AI Agent 과정 4기" → "청년취업사관학교 AI Agent 과정") */
export const courseName = (label: string) => label.replace(/\s+\d+기$/, "");

/** 소수 첫째 자리 %. 분모가 0이면 0 */
export const pct = (n: number, total: number) => (total ? Math.round((n / total) * 1000) / 10 : 0);

/** statusSummary 의 4분류 인원 */
export const manageCounts = (s: StatusSummary): Record<StudentStatus, number> => ({
  EMPLOYED: s.EMPLOYED,
  SEEKING: s.SEEKING.total,
  NEED_HELP: s.NEED_HELP,
  CHECK_REQUIRED: s.CHECK_REQUIRED,
});

/** 관리 상태 파악률 = (전체 − 추가 확인 필요) ÷ 전체. 취업 여부를 직접 확인한 비율이 아니다 */
export const knownCount = (total: number, s: StatusSummary) => Math.max(0, total - s.CHECK_REQUIRED);

/** "2026-10-24T09:12:00+09:00" → "10.24" · "10.24 09:12" (서버가 +09:00 으로 준다) */
export const monthDay = (iso: string) => `${iso.slice(5, 7)}.${iso.slice(8, 10)}`;
export const monthDayTime = (iso: string) => `${monthDay(iso)} ${iso.slice(11, 16)}`;
