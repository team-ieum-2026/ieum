// 주인: FE-A — API 호출 공통 클라이언트
// NEXT_PUBLIC_USE_MOCK=true(기본) 면 public/mock/<파일>.json 을 읽고, false 면 NEXT_PUBLIC_API_BASE_URL + 경로를 호출한다.
// 응답 { data } 의 data 를 돌려주고, { error } 면 ApiError 를 던진다 (docs/API.md 1장).
// 목업은 브라우저 상대 경로(/mock/…)로 읽으므로 화면("use client") 컴포넌트나 useEffect 안에서 호출한다.

export class ApiError extends Error {
  readonly code: string;
  readonly status: number;

  constructor(code: string, message: string, status: number) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
  }
}

type Method = "GET" | "POST" | "PATCH" | "PUT" | "DELETE";

interface Envelope<T> {
  data?: T;
  error?: { code: string; message: string };
}

const USE_MOCK = process.env.NEXT_PUBLIC_USE_MOCK !== "false";
const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";

/** P0 는 로그인이 없음. 수료생 화면의 기본 수료생 id (주소의 ?studentId= 가 있으면 그 값을 우선) */
export const DEMO_STUDENT_ID = Number(process.env.NEXT_PUBLIC_DEMO_STUDENT_ID ?? "1");

async function request<T>(method: Method, path: string, mockFile: string, body?: unknown): Promise<T> {
  const url = USE_MOCK ? `/mock/${mockFile}` : `${API_BASE_URL}${path}`;
  const init: RequestInit = USE_MOCK
    ? { method: "GET", cache: "no-store" }
    : {
        method,
        headers: body === undefined ? undefined : { "Content-Type": "application/json" },
        body: body === undefined ? undefined : JSON.stringify(body),
        cache: "no-store",
      };

  let res: Response;
  try {
    res = await fetch(url, init);
  } catch {
    throw new ApiError("NETWORK_ERROR", "서버에 연결할 수 없어요.", 0);
  }

  let json: Envelope<T>;
  try {
    json = (await res.json()) as Envelope<T>;
  } catch {
    throw new ApiError("INVALID_RESPONSE", "응답을 읽을 수 없어요.", res.status);
  }

  if (json.error) {
    throw new ApiError(json.error.code, json.error.message, res.status);
  }
  if (!res.ok || json.data === undefined) {
    throw new ApiError("INTERNAL_ERROR", "일시적인 오류가 생겼어요.", res.status);
  }
  return json.data;
}

export const apiGet = <T>(path: string, mockFile: string) => request<T>("GET", path, mockFile);
export const apiPost = <T>(path: string, mockFile: string, body?: unknown) =>
  request<T>("POST", path, mockFile, body);
export const apiPatch = <T>(path: string, mockFile: string, body?: unknown) =>
  request<T>("PATCH", path, mockFile, body);
export const apiPut = <T>(path: string, mockFile: string, body?: unknown) =>
  request<T>("PUT", path, mockFile, body);
export const apiDelete = <T>(path: string, mockFile: string) => request<T>("DELETE", path, mockFile);
