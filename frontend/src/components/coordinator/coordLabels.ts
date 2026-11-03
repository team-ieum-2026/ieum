// 주인: FE-B — 코디네이터 전용 이름표 · 색 · 집계 (11/1 개편). 서버 값은 그대로 두고 화면 이름만 정한다.
// 수료생 화면용 components/student/labels.ts 는 고치지 않는다 (applicationLabels 등은 수료생 문구).
import type { CoordStudentExtra, CoordStudentStatus, DashboardHelpRequest, HelpType, StatusSummary } from "@/types/coordinator";
import type { ApplicationStatus, ContactMethod, HelpRequestCloseReason, ResponseType, StudentHelpRequestStatus } from "@/types/student";

export type CoordTone = "green" | "orange" | "red" | "blue" | "gray";

/**
 * 대표 상태 4분류 (서버 status 그대로, 합계 = 전체 수료생). NEED_HELP 는 대표 상태가 아니어서 빠진다 (11/2 BE-B).
 * 도움 요청은 상태가 아니라 openHelpRequestCount · helpRequests 로 따로 보여 준다
 */
export const MANAGE_ORDER: CoordStudentStatus[] = ["EMPLOYED", "SEEKING", "RESTING", "CHECK_REQUIRED"];
export const manageLabels: Record<CoordStudentStatus, string> = {
  EMPLOYED: "취업 완료",
  SEEKING: "구직 중",
  RESTING: "휴식",
  CHECK_REQUIRED: "추가 확인 필요",
  /** 이전 자료 호환용 (목록에 남아 있으면 이름만 보여 준다) */
  NEED_HELP: "도움 필요",
};
export const manageTone: Record<CoordStudentStatus, CoordTone> = {
  EMPLOYED: "green",
  SEEKING: "blue",
  RESTING: "gray",
  CHECK_REQUIRED: "orange",
  NEED_HELP: "red",
};
/** 관리 상태 막대 · 도넛 색 (coordinator.css 변수) */
export const manageChartVar: Record<CoordStudentStatus, string> = {
  EMPLOYED: "var(--coord-chart-employed)",
  SEEKING: "var(--coord-chart-seeking)",
  RESTING: "var(--coord-chart-resting)",
  CHECK_REQUIRED: "var(--coord-chart-check)",
  NEED_HELP: "var(--coord-chart-help)",
};

/** 도움 요청 유형 (docs/API.md). null · 키 없음은 '미분류' */
export const helpTypeLabels: Record<HelpType, string> = {
  RESUME_COVER_LETTER: "이력서·자소서",
  JOB_APPLICATION: "채용공고·지원",
  INTERVIEW_PREPARATION: "면접 준비",
  CAREER_COUNSELING: "진로 상담",
  OTHER: "기타",
};
export const HELP_TYPE_ORDER = Object.keys(helpTypeLabels) as HelpType[];
export const helpTypeLabel = (t: HelpType | null | undefined) => (t ? helpTypeLabels[t] : "미분류");
/** 도움 요청 한 줄 제목: 공고가 있으면 공고, 없으면 '공고 없는 요청' (jobTitle=null 인 일반 요청) */
export const helpTitle = (h: Pick<DashboardHelpRequest, "jobTitle">) => h.jobTitle ?? "공고 없는 도움 요청";

/** 수료생 목록 항목 · 상세 student 의 11/2 추가 값을 안전하게 읽는다 (목업 JSON 처럼 키가 없으면 null · false) */
export function studentExtra(row: object): CoordStudentExtra {
  const r = row as Partial<Record<keyof CoordStudentExtra, unknown>>;
  return {
    openHelpRequestCount: typeof r.openHelpRequestCount === "number" ? r.openHelpRequestCount : null,
    consecutivePassCount: typeof r.consecutivePassCount === "number" ? r.consecutivePassCount : null,
    needsPassFollowUp: r.needsPassFollowUp === true,
  };
}

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

/** 도움 요청 상태 — CONTACTED 는 '연락 완료'(요청 완료 아님). CLOSED 는 수료생이 직접 종료한 요청 */
export const helpLabels: Record<StudentHelpRequestStatus, string> = { OPEN: "접수", CONTACTED: "연락 완료", CLOSED: "요청 종료" };
export const helpTone: Record<StudentHelpRequestStatus, CoordTone> = { OPEN: "red", CONTACTED: "orange", CLOSED: "gray" };

/** 수료생이 도움 요청을 종료한 사유 */
export const closeReasonLabels: Record<HelpRequestCloseReason, string> = {
  EMPLOYED: "취업해서 더 이상 필요 없어요",
  NO_LONGER_NEEDED: "도움이 더 이상 필요 없어요",
  OTHER: "기타",
};

/** 수료생이 고른 선호 연락 수단 */
export const contactLabels: Record<ContactMethod, string> = { PHONE: "전화", SMS: "문자", EMAIL: "이메일" };

/** 추천 공고 응답 */
export const responseTagLabels: Record<ResponseType, string> = { APPLY: "지원", PASS: "패스", NONE: "미응답", HELP: "도움 요청" };
export const responseTone: Record<ResponseType, CoordTone> = { APPLY: "green", PASS: "gray", NONE: "orange", HELP: "red" };

/** 교육과정 라벨 끝의 ' N기'를 뗀다 ("청년취업사관학교 AI Agent 과정 4기" → "청년취업사관학교 AI Agent 과정") */
export const courseName = (label: string) => label.replace(/\s+\d+기$/, "");

/** 소수 첫째 자리 %. 분모가 0이면 0 */
export const pct = (n: number, total: number) => (total ? Math.round((n / total) * 1000) / 10 : 0);

/** statusSummary 의 대표 상태 4분류 인원. NEED_HELP(호환용 0)는 집계에 쓰지 않는다. RESTING 이 없는 이전 응답은 0 */
export const manageCounts = (s: StatusSummary): Record<CoordStudentStatus, number> => ({
  EMPLOYED: s.EMPLOYED,
  SEEKING: s.SEEKING.total,
  RESTING: s.RESTING ?? 0,
  CHECK_REQUIRED: s.CHECK_REQUIRED,
  NEED_HELP: 0,
});

/** 관리 상태 파악률 = (전체 − 추가 확인 필요) ÷ 전체. 취업 여부를 직접 확인한 비율이 아니다 */
export const knownCount = (total: number, s: StatusSummary) => Math.max(0, total - s.CHECK_REQUIRED);

/** "2026-10-24T09:12:00+09:00" → "10.24" · "10.24 09:12" (서버가 +09:00 으로 준다) */
export const monthDay = (iso: string) => `${iso.slice(5, 7)}.${iso.slice(8, 10)}`;
export const monthDayTime = (iso: string) => `${monthDay(iso)} ${iso.slice(11, 16)}`;
