// 주인: FE-B — C1 대시보드 · C4 서류 검토 응답 타입 (C1_coordinator_dashboard.json, C4_coordinator_application.json)
// C2 · C3 (수료생 목록 · 상세) 타입은 FE-A 의 types/student.ts 에 있다.
import type {
  ApplicationStatus,
  CompanyInfoStatus,
  ContactMethod,
  HelpRequestCloseReason,
  HelpRequestStatus,
  StudentHelpRequestStatus,
  StudentStatus,
} from "@/types/student";
import type { ApplicationDocument, JobBrief } from "@/types/resume";

// ---- 11/2 BE-B 추가 값 (docs/API.md). FE-A 의 types/student.ts 에는 아직 없어 코디네이터 화면용으로 여기서 넓힌다 ----
/** 대표 상태 + 휴식(RESTING). NEED_HELP 는 이전 자료 호환용으로만 남는다 */
export type CoordStudentStatus = StudentStatus | "RESTING";

/** 도움 요청 유형. null 이면 미분류 (이전 공고 요청) */
export type HelpType = "RESUME_COVER_LETTER" | "JOB_APPLICATION" | "INTERVIEW_PREPARATION" | "CAREER_COUNSELING" | "OTHER";

/** 수료생 목록 항목 · 상세 student 에 붙는 값. 목업 JSON 에는 없을 수 있어 읽을 때 기본값을 쓴다 (coordLabels.studentExtra) */
export interface CoordStudentExtra {
  /** 미처리(OPEN) 도움 요청 건수. 모르면 null */
  openHelpRequestCount: number | null;
  /** 마지막 초기화 이후 일반 패스 연속 횟수. 모르면 null */
  consecutivePassCount: number | null;
  /** 백엔드 판정: 연속 패스 연락 대상 */
  needsPassFollowUp: boolean;
}

/** 일반 패스 연속 연락 대상 (대시보드 passFollowUpList · GET /api/coordinator/pass-follow-ups) */
export interface PassFollowUp {
  studentId: number;
  studentName: string;
  status: CoordStudentStatus;
  consecutivePassCount: number;
  lastPassAt: string | null;
}

// ---- C1 대시보드 ----
export interface DashboardHelpRequest {
  helpRequestId: number;
  studentId: number;
  studentName: string;
  courseLabel: string;
  createdAt: string;
  /** 공고 없는 일반 요청이면 null */
  jobTitle: string | null;
  contactMethod: ContactMethod | null;
  memo: string | null;
  /** 미분류면 null. 이전 목업 JSON 에는 키가 없을 수 있다 */
  helpType?: HelpType | null;
}

export interface StatusSummary {
  EMPLOYED: number;
  SEEKING: { total: number; APPLYING: number; WAITING: number; OTHER: number };
  /** 호환용 0. 인원 집계에 쓰지 않는다 */
  NEED_HELP: number;
  CHECK_REQUIRED: number;
  /** 휴식 인원. 이전 목업 JSON 에는 없을 수 있다 */
  RESTING?: number;
}

export interface ReviewQueueItem {
  applicationId: number;
  studentName: string;
  jobTitle: string;
  submittedAt: string;
  /** 공고 마감일 "2026-11-30". 없으면 null. 이전 목업 JSON 에는 키가 없을 수 있다 */
  deadlineOn?: string | null;
  /** 채용 시 마감 공고 */
  untilFilled?: boolean;
}

// ---- C4 · 도움 요청 상태별 목록 (GET /api/coordinator/applications · /api/coordinator/help-requests) ----
/** 서류 목록 상태 필터. 검토 요청 이후 단계만 (작성 중 GENERATING · DRAFT · FAILED 는 없다) */
export type CoordinatorApplicationFilter = "ALL" | "SUBMITTED" | "REJECTED" | "APPROVED" | "DELIVERED";

export interface CoordinatorApplicationListItem {
  applicationId: number;
  studentId: number;
  studentName: string;
  jobTitle: string;
  status: Exclude<CoordinatorApplicationFilter, "ALL">;
  submittedAt: string;
  reviewedAt: string | null;
  deliveredAt: string | null;
  deadlineOn: string | null;
  untilFilled: boolean;
}

export type CoordinatorHelpRequestFilter = StudentHelpRequestStatus | "ALL";

/** 도움 요청 목록 항목 = 대시보드 항목 + 상태 · 종료 정보 */
export interface CoordinatorHelpRequest extends DashboardHelpRequest {
  status: StudentHelpRequestStatus;
  closedAt: string | null;
  closeReason: HelpRequestCloseReason | null;
}

export interface CallListItem {
  studentId: number;
  studentName: string;
  lastResponseAt: string | null;
  daysSinceResponse: number;
}

export interface NoteToCheck {
  studentId: number;
  studentName: string;
  passNote: string;
}

export interface Dashboard {
  asOf: string;
  totalStudents: number;
  helpRequests: DashboardHelpRequest[];
  statusSummary: StatusSummary;
  reviewQueue: ReviewQueueItem[];
  callList: CallListItem[];
  notesToCheck: NoteToCheck[];
  /** 연속 패스 연락 대상. 이전 목업 JSON 에는 없을 수 있다 */
  passFollowUpList?: PassFollowUp[];
}

// ---- C1 도움 요청 처리 ----
export interface HelpRequestStatusUpdateRequest {
  status: HelpRequestStatus;
}

export interface HelpRequestStatusUpdateResult {
  helpRequestId: number;
  status: HelpRequestStatus;
}

// ---- C4 서류 검토 ----
export interface Verification {
  aiSentenceCount: number;
  aiSentenceWithEvidence: number;
  excludedSentenceCount: number;
  userEditedCount: number;
  companyInfoStatus: CompanyInfoStatus;
}

export interface CoordinatorApplication {
  applicationId: number;
  status: ApplicationStatus;
  student: { studentId: number; name: string };
  job: JobBrief;
  /** GENERATING · FAILED 이거나 서류 본문이 없으면 null (이때 verification 은 0 · NOT_FOUND) */
  document: ApplicationDocument | null;
  verification: Verification;
}

export interface RejectRequest {
  note: string;
}

export interface ApplicationStatusResult {
  applicationId: number;
  status: ApplicationStatus;
}
