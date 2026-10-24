// 주인: FE-B — C1 대시보드 · C4 서류 검토 응답 타입 (C1_coordinator_dashboard.json, C4_coordinator_application.json)
// C2 · C3 (수료생 목록 · 상세) 타입은 FE-A 의 types/student.ts 에 있다.
import type {
  ApplicationStatus,
  CompanyInfoStatus,
  ContactMethod,
  HelpRequestStatus,
} from "@/types/student";
import type { ApplicationDocument, JobBrief } from "@/types/resume";

// ---- C1 대시보드 ----
export interface DashboardHelpRequest {
  helpRequestId: number;
  studentId: number;
  studentName: string;
  courseLabel: string;
  createdAt: string;
  jobTitle: string | null;
  contactMethod: ContactMethod | null;
  memo: string | null;
}

export interface StatusSummary {
  EMPLOYED: number;
  SEEKING: { total: number; APPLYING: number; WAITING: number; OTHER: number };
  NEED_HELP: number;
  CHECK_REQUIRED: number;
}

export interface ReviewQueueItem {
  applicationId: number;
  studentName: string;
  jobTitle: string;
  submittedAt: string;
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
  document: ApplicationDocument;
  verification: Verification;
}

export interface RejectRequest {
  note: string;
}

export interface ApplicationStatusResult {
  applicationId: number;
  status: ApplicationStatus;
}
