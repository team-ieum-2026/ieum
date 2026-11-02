import type { HelpRequestCloseReason, HelpType, StudentHelpRequestStatus } from "@/types/student";

export const helpTypeLabels: Record<HelpType, string> = {
  RESUME_COVER_LETTER: "이력서·자소서",
  JOB_APPLICATION: "채용공고·지원",
  INTERVIEW_PREPARATION: "면접 준비",
  CAREER_COUNSELING: "진로 상담",
  OTHER: "기타",
};

export const helpTypes: HelpType[] = ["RESUME_COVER_LETTER", "JOB_APPLICATION", "INTERVIEW_PREPARATION", "CAREER_COUNSELING", "OTHER"];
export const studentHelpStatusLabels: Record<StudentHelpRequestStatus, string> = {
  OPEN: "접수", CONTACTED: "연락 완료", CLOSED: "종료",
};
export const helpCloseReasonLabels: Record<HelpRequestCloseReason, string> = {
  EMPLOYED: "취업했어요", NO_LONGER_NEEDED: "이제 도움이 필요하지 않아요", OTHER: "기타",
};
export const helpCloseReasons: HelpRequestCloseReason[] = ["EMPLOYED", "NO_LONGER_NEEDED", "OTHER"];
