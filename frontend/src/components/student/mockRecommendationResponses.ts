import type { ResponseType } from "@/types/student";
import { getMockRecommendationResponse } from "@/lib/api/student";

const mock = process.env.NEXT_PUBLIC_USE_MOCK !== "false";
const key = (studentId: number, itemId: number) => `ieum:mock-response:${studentId}:${itemId}`;

export function rememberResponse(studentId: number, itemId: number, response: Exclude<ResponseType, "NONE">) {
  if (mock) sessionStorage.setItem(key(studentId, itemId), response);
}

export function clearRememberedResponse(studentId: number, itemId: number) {
  if (mock) sessionStorage.removeItem(key(studentId, itemId));
}

export function restoreResponse(studentId: number, itemId: number, fallback: ResponseType): ResponseType {
  if (!mock) return fallback;
  const saved = getMockRecommendationResponse(studentId, itemId, fallback);
  if (saved !== "NONE") return saved;
  const response = sessionStorage.getItem(key(studentId, itemId));
  return response === "APPLY" || response === "PASS" || response === "HELP" ? response : saved;
}
