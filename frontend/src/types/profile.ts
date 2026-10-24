// 주인: FE-B — M8 내 프로필 응답 타입 (M8_student_profile.json)
import type { CompetencyProfile, MilitaryStatus, Source } from "@/types/student";
import type { SectionItemInput, SectionName } from "@/types/resume";

export interface ProfileBasic {
  name: string;
  email: string | null;
  githubUrl: string | null;
  desiredJob: string | null;
  preferredRegion: string | null;
  militaryStatus: MilitaryStatus;
  completedOn: string;
}

export interface ProfileCourse {
  courseId: number;
  name: string;
  cohort: string;
  campus: string;
  startedOn: string;
  endedOn: string;
}

export interface ProfileEducation {
  itemId: number;
  source: Source;
  schoolName: string;
  major: string | null;
  degree: string | null;
  status: string | null;
  graduatedOn: string | null;
}

/** DB student_career 컬럼과 같은 이름. endedOn 이 null 이면 재직 중 */
export interface ProfileCareer {
  itemId: number;
  source: Source;
  companyName: string;
  position: string | null;
  startedOn: string | null;
  endedOn: string | null;
  description: string | null;
}

export interface ProfileCertificate {
  itemId: number;
  source: Source;
  name: string;
  issuer: string | null;
  acquiredOn: string | null;
}

export interface ProfileProject {
  itemId: number;
  title: string;
  periodText: string | null;
  teamSize: number | null;
  techStack: string[];
  portfolioFileName: string | null;
  includeInApply: boolean;
}

export interface ProfileCoverLetter {
  content: string;
  updatedAt: string;
}

export interface StudentProfile {
  studentId: number;
  /** "반영 완료" 배너용. 역량 프로필이 마지막으로 다시 만들어진 시각 */
  profileUpdatedAt: string | null;
  basic: ProfileBasic;
  course: ProfileCourse;
  education: ProfileEducation[];
  careers: ProfileCareer[];
  certificates: ProfileCertificate[];
  skills: string[];
  projects: ProfileProject[];
  coverLetter: ProfileCoverLetter | null;
  competencyProfile: CompetencyProfile | null;
}

// ---- 프로필 항목 추가 · 수정 (요청 본문은 서류 PATCH 의 section · item 형식과 같음) ----
export interface ProfileItemRequest {
  section: SectionName;
  item: SectionItemInput;
}

export interface CoverLetterUpdateRequest {
  content: string;
}
