// 주인: FE-B — 코디네이터 데모 계정 · 상담 일정 자리 (11/2). P0 에는 로그인이 없어서(AGENTS.md 9) 프론트에서만 쓰는 표시용 값이다.
// 실제 계정 · 상담 일정 API 가 생기면 이 파일만 그 응답으로 바꾸면 된다. 실제 인물이 아닌 모의 이름.
export const DEMO_COORDINATOR = {
  name: "정다은",
  role: "매니저",
  /** 아바타에 쓰는 한 글자 */
  initial: "정",
  /** true: 프론트 데모 계정 (계정 메뉴에 '데모 계정'이라고 알린다) */
  isDemo: true,
} as const;

/** 상담 일정 한 건 (향후 API 형식 자리) */
export interface ConsultationSchedule {
  id: number;
  studentId: number;
  studentName: string;
  /** "2026-11-02T14:00:00+09:00" */
  scheduledAt: string;
  topic: string | null;
}

/**
 * 상담 일정 연동 여부. 11/3 부터 도움 요청 상담 예정(CONSULTATION_SCHEDULED · consultationAt)으로 연결됐다.
 * false 로 바꾸면 '상담 예정' 지표 · '오늘 예정된 상담'을 미연동 빈 상태로 보여 준다 (가짜 일정 없음)
 */
export const CONSULTATION_API_CONNECTED = true;

/** 목업 모드 여부 (lib/api/client.ts 와 같은 조건: 값이 "false" 가 아니면 public/mock/ 을 읽는다). 안내 문구 표시에만 쓴다 */
export const IS_MOCK = process.env.NEXT_PUBLIC_USE_MOCK !== "false";
/** 목업 모드에서 저장 · 상태 변경 성공 메시지 뒤에 붙이는 안내 */
export const MOCK_SAVE_NOTE = "시연용 목업이며 실제 저장되지 않습니다.";
