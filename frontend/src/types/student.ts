// 주인: FE-A — 공통 코드 값 + 수료생 흐름(M1 ~ M5, M7) + 코디네이터 수료생 목록 · 상세(C2 · C3) 응답 타입
// 공통 코드 값은 이 파일에만 두고 resume.ts · profile.ts · coordinator.ts 가 import 한다. (docs/API.md 1장)

// ---- 공통 코드 값 ----
export type ResponseType = "NONE" | "APPLY" | "PASS" | "HELP";
export type PassReason =
  | "ALREADY_EMPLOYED"
  | "JOB_MISMATCH"
  | "CONDITION_MISMATCH"
  | "SKILL_GAP"
  | "TAKING_BREAK"
  | "OTHER";
export type ContactMethod = "SMS" | "PHONE" | "EMAIL";
export type ApplicationStatus =
  | "GENERATING"
  | "DRAFT"
  | "SUBMITTED"
  | "APPROVED"
  | "REJECTED"
  | "DELIVERED"
  | "FAILED";
export type StudentStatus = "EMPLOYED" | "SEEKING" | "NEED_HELP" | "CHECK_REQUIRED";
export type SubStatus = "APPLYING" | "WAITING" | "OTHER";
export type Source = "AI" | "REGISTERED" | "USER";
export type MilitaryStatus = "SERVED" | "NOT_SERVED" | "EXEMPT" | "NOT_APPLICABLE" | null;
export type HelpRequestStatus = "OPEN" | "CONTACTED";
export type CompanyInfoStatus = "FOUND" | "PARTIAL" | "NOT_FOUND";

// ---- 근거 (docs/API.md 3-2) ----
export type RefType = "PROJECT" | "COVER_LETTER" | "CERTIFICATE" | "EDUCATION" | "COURSE" | "COMPANY_INFO";

export interface EvidenceRef {
  refType: RefType;
  refId: number;
}

export interface Evidence extends EvidenceRef {
  label: string;
  quote: string;
  url: string | null;
  collectedAt: string | null;
}

// ---- 역량 프로필 (docs/API.md 3-1) — M8 · C3 에서 같이 씀 ----
export interface CompetencyProfile {
  roles: string[];
  skills: { name: string; evidence: EvidenceRef[] }[];
  achievements: { text: string; evidence: EvidenceRef[] }[];
  certificates: string[];
  desiredJob: string | null;
  preferredRegion: string | null;
}

// ---- M1 추천 (M1_recommendations_current.json) ----
export interface JobSummary {
  jobPostingId: number;
  companyName: string;
  title: string;
  location: string;
  employmentType: string;
  career: string;
  deadlineOn: string;
  untilFilled: boolean;
  dDay: number;
}

export interface RecommendationItemSummary {
  itemId: number;
  rank: number;
  response: ResponseType;
  job: JobSummary;
  reasonSummary: string[];
}

export interface Recommendations {
  recommendationId: number;
  createdAt: string;
  basis: string;
  items: RecommendationItemSummary[];
}

// ---- M2 공고 상세 (M2_recommendation_item_detail.json) ----
export interface JobDetail extends JobSummary {
  jobCategory: string;
  workAddress: string;
  education: string;
  salaryText: string;
  postedOn: string;
  description: string;
  homepageUrl: string | null;
  source: string;
  sourceLabel: string;
}

export interface RecommendationReason {
  text: string;
  evidence: Evidence[];
}

export interface RecommendationItemDetail {
  itemId: number;
  rank: number;
  response: ResponseType;
  job: JobDetail;
  summary: string[];
  reasons: RecommendationReason[];
}

// ---- M2 · M3 응답 ----
export type ItemResponseRequest =
  | { response: "APPLY" }
  | { response: "PASS"; passReason: PassReason; passNote: string | null };

export interface ItemResponseResult {
  itemId: number;
  response: ResponseType;
  applicationId: number | null;
}

// ---- M2 → M4 도움 요청 ----
export interface HelpRequestCreateRequest {
  studentId: number;
  recommendationItemId: number;
}

export interface HelpRequestCreateResult {
  helpRequestId: number;
  status: HelpRequestStatus;
}

export interface HelpRequestUpdateRequest {
  contactMethod: ContactMethod | null;
  memo: string | null;
}

export interface HelpRequestUpdateResult extends HelpRequestCreateResult {
  contactMethod: ContactMethod | null;
  memo: string | null;
}

// ---- M5 서류 생성 진행 ----
export type ProgressStepKey = "COMPANY_INFO" | "COVER_LETTER" | "RESUME";
export type ProgressStepState = "WAITING" | "RUNNING" | "DONE" | "FAILED";

export interface ProgressStep {
  key: ProgressStepKey;
  state: ProgressStepState;
  note: string | null;
}

export interface ApplicationProgress {
  applicationId: number;
  status: ApplicationStatus;
  steps: ProgressStep[];
}

// ---- M7 지원 현황 ----
export interface ApplicationListItem {
  applicationId: number;
  companyName: string;
  title: string;
  status: ApplicationStatus;
  submittedAt: string | null;
  userEditedCount: number;
}

export interface OtherRecommendationItem {
  itemId: number;
  companyName: string;
  title: string;
  response: ResponseType;
  passReason: PassReason | null;
}

export interface StudentApplications {
  applications: ApplicationListItem[];
  otherItems: OtherRecommendationItem[];
}

// ---- C2 수료생 목록 (C2_coordinator_students.json) ----
export interface CoordinatorStudentListQuery {
  status?: StudentStatus;
  subStatus?: SubStatus;
  q?: string;
  page?: number;
  size?: number;
}

export interface CoordinatorStudentRow {
  studentId: number;
  name: string;
  courseLabel: string;
  completedOn: string;
  status: StudentStatus;
  subStatus: SubStatus | null;
  lastResponseAt: string | null;
  hasNote: boolean;
}

export interface CoordinatorStudentList {
  items: CoordinatorStudentRow[];
  total: number;
  page: number;
  size: number;
}

// ---- C3 수료생 상세 (C3_coordinator_student_detail.json) ----
export interface CoordinatorStudentBasic extends CoordinatorStudentRow {
  email: string | null;
  desiredJob: string | null;
  preferredRegion: string | null;
  reason: string | null;
}

export interface RegisteredSummary {
  education: number;
  certificates: number;
  projects: number;
  hasCoverLetter: boolean;
}

export interface RecommendationHistoryItem {
  itemId: number;
  companyName: string;
  title: string;
  response: ResponseType;
  passReason: PassReason | null;
  passNote: string | null;
  respondedAt: string | null;
  applicationId: number | null;
  applicationStatus: ApplicationStatus | null;
}

export interface RecommendationHistory {
  recommendationId: number;
  createdAt: string;
  items: RecommendationHistoryItem[];
}

export type ContactType = "PHONE" | "MEETING" | "MESSAGE";

export interface ContactLog {
  contactLogId: number;
  contactType: ContactType;
  note: string;
  createdAt: string;
}

export interface ContactLogCreateRequest {
  contactType: ContactType;
  note: string;
}

export interface CoordinatorStudentDetail {
  student: CoordinatorStudentBasic;
  competencyProfile: CompetencyProfile | null;
  registered: RegisteredSummary;
  history: RecommendationHistory[];
  contactLogs: ContactLog[];
}
