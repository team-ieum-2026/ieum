// 주인: FE-B — 서류 API (M6 · M6-1 · M6-2). 경로 · 형식은 docs/API.md 2-1
import { apiGet, apiPatch, apiPost } from "@/lib/api/client";
import type { ApplicationDetail, DocumentPatchRequest, DocumentPatchResult, SubmitResult } from "@/types/resume";

/** M6 · M6-1 서류 전체 (이력서 + 자기소개서 + 근거) */
export const getApplication = (applicationId: number) =>
  apiGet<ApplicationDetail>(`/api/applications/${applicationId}`, "M6_application_document.json");

/** M6 · M6-1 · M6-2 문장 수정 · 섹션 항목 추가 · 수정 · 삭제 (한 번에 하나의 변경만) */
export const patchDocument = (applicationId: number, body: DocumentPatchRequest) =>
  apiPatch<DocumentPatchResult>(`/api/applications/${applicationId}/document`, "M6_application_document.json", body);

/** M6 최종 제출 (DRAFT → SUBMITTED) */
export const submitApplication = (applicationId: number) =>
  apiPost<SubmitResult>(`/api/applications/${applicationId}/submit`, "M6_submit.json");
