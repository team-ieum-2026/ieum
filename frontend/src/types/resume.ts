// 주인: FE-B — 서류 JSON(docs/API.md 3-3) + M6 · M6-1 · M6-2 응답 타입 (M6_application_document.json)
import type { ApplicationStatus, CompanyInfoStatus, Evidence, MilitaryStatus, Source } from "@/types/student";

export type SectionName =
  | "BASIC"
  | "EDUCATION"
  | "TRAINING"
  | "CAREER"
  | "MILITARY"
  | "CERTIFICATES"
  | "SKILLS"
  | "PROJECTS"
  | "PORTFOLIO"
  | "COVER_LETTER";

/** AI 문장: source 가 AI 면 evidence 가 1개 이상, USER 면 비어 있어도 됨 */
export interface Sentence {
  sentenceId: string;
  text: string;
  source: Source;
  evidence: Evidence[];
}

export interface BasicSection {
  section: "BASIC";
  source: Source;
  fields: {
    name: string;
    email: string | null;
    githubUrl: string | null;
    desiredJob: string | null;
  };
}

export interface EducationItem {
  itemId: string;
  source: Source;
  schoolName: string;
  major: string | null;
  degree: string | null;
  status: string | null;
  graduatedOn: string | null;
}

export interface EducationSection {
  section: "EDUCATION";
  items: EducationItem[];
}

export interface TrainingItem {
  itemId: string;
  source: Source;
  name: string;
  campus: string | null;
  startedOn: string | null;
  endedOn: string | null;
}

export interface TrainingSection {
  section: "TRAINING";
  items: TrainingItem[];
}

/** DB student_career 컬럼과 같은 이름. endedOn 이 null 이면 재직 중 */
export interface CareerItem {
  itemId: string;
  source: Source;
  companyName: string;
  position: string | null;
  startedOn: string | null;
  endedOn: string | null;
  description: string | null;
}

export interface CareerSection {
  section: "CAREER";
  items: CareerItem[];
}

export interface MilitarySection {
  section: "MILITARY";
  value: MilitaryStatus;
}

export interface CertificateItem {
  itemId: string;
  source: Source;
  name: string;
  issuer: string | null;
  acquiredOn: string | null;
}

export interface CertificatesSection {
  section: "CERTIFICATES";
  items: CertificateItem[];
}

export interface SkillsSection {
  section: "SKILLS";
  source: Source;
  summary: Sentence[];
  tags: string[];
}

export interface ProjectItem {
  itemId: string;
  title: string;
  periodText: string | null;
  teamSize: number | null;
  role: string | null;
  bullets: Sentence[];
}

export interface ProjectsSection {
  section: "PROJECTS";
  items: ProjectItem[];
}

export interface PortfolioItem {
  itemId: string;
  fileName: string;
  include: boolean;
}

export interface PortfolioSection {
  section: "PORTFOLIO";
  items: PortfolioItem[];
}

export interface CoverLetterSection {
  section: "COVER_LETTER";
  sentences: Sentence[];
}

export type DocumentSection =
  | BasicSection
  | EducationSection
  | TrainingSection
  | CareerSection
  | MilitarySection
  | CertificatesSection
  | SkillsSection
  | ProjectsSection
  | PortfolioSection
  | CoverLetterSection;

/** sections 순서가 곧 M6 · C4 화면 순서 */
export interface ApplicationDocument {
  companyInfoStatus: CompanyInfoStatus;
  companyInfoNote: string | null;
  excludedSentenceCount: number;
  sections: DocumentSection[];
}

export interface JobBrief {
  jobPostingId: number;
  companyName: string;
  title: string;
}

// ---- M6 · M6-1 서류 조회 ----
export interface ApplicationDetail {
  applicationId: number;
  status: ApplicationStatus;
  job: JobBrief;
  /** GENERATING · FAILED 에서는 null (BE-A 확정) */
  document: ApplicationDocument | null;
}

// ---- PATCH /api/applications/{id}/document — 한 번에 하나의 변경만 ----
export type SectionItemInput = Record<string, unknown>;

export type DocumentPatchRequest =
  | { op: "UPDATE_SENTENCE"; sentenceId: string; text: string }
  | { op: "ADD_ITEM"; section: SectionName; item: SectionItemInput; saveToProfile: boolean }
  | { op: "UPDATE_ITEM"; section: SectionName; itemId: string; item: SectionItemInput; saveToProfile: boolean }
  | { op: "DELETE_ITEM"; section: SectionName; itemId: string; saveToProfile: boolean };

export interface DocumentPatchResult {
  document: ApplicationDocument;
}

// ---- M6 최종 제출 ----
export interface SubmitResult {
  applicationId: number;
  status: ApplicationStatus;
}
