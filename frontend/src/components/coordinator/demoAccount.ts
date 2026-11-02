// 주인: FE-B — 코디네이터 데모 계정 · 상담 일정 자리 (11/2). P0 에는 로그인이 없어서(AGENTS.md 9) 프론트에서만 쓰는 표시용 값이다.
// 실제 계정 · 상담 일정 API 가 생기면 이 파일만 그 응답으로 바꾸면 된다. 실제 인물이 아닌 모의 이름.
export const DEMO_COORDINATOR = {
  name: "정다은",
  role: "코디네이터",
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

/** 상담 일정 조회 API 연동 여부. false 면 '상담 예정' 지표 · '오늘 예정된 상담'은 미연동 빈 상태로 보여 준다 (가짜 일정 없음) */
export const CONSULTATION_API_CONNECTED = false;
