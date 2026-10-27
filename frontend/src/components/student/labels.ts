import type { ApplicationStatus, PassReason, ResponseType, StudentStatus, SubStatus } from "@/types/student";

export const responseLabels: Record<ResponseType, string> = { NONE: "응답 전", APPLY: "지원함", PASS: "패스", HELP: "도움 요청" };
export const passLabels: Record<PassReason, string> = { ALREADY_EMPLOYED: "이미 취업했어요", JOB_MISMATCH: "직무가 맞지 않아요", CONDITION_MISMATCH: "지역이나 근무 조건이 안 맞아요", SKILL_GAP: "요구 역량이 부족한 것 같아요", TAKING_BREAK: "지금은 구직을 잠시 쉬고 있어요", OTHER: "기타" };
export const statusLabels: Record<StudentStatus, string> = { EMPLOYED: "취업 완료", SEEKING: "구직 중", NEED_HELP: "도움 필요", CHECK_REQUIRED: "추가 확인 필요" };
export const subStatusLabels: Record<SubStatus, string> = { APPLYING: "기관 추천 지원 중", WAITING: "패스 후 대기 중", OTHER: "기타 사유" };
export const applicationLabels: Record<ApplicationStatus, string> = { GENERATING: "서류 생성 중", DRAFT: "초안 작성 중", SUBMITTED: "코디네이터 검토 중", APPROVED: "승인 완료", REJECTED: "보완 필요", DELIVERED: "전달 완료", FAILED: "생성 실패" };
export function statusTone(status: StudentStatus) {
  return status === "NEED_HELP" ? "danger" : status === "CHECK_REQUIRED" ? "warning" : status === "EMPLOYED" ? "success" : "neutral";
}
