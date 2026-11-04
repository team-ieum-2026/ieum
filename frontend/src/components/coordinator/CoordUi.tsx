// 주인: FE-B — 코디네이터 화면 공통 부품 (11/1 개편 목업). 공통 UI(components/ui · FE-A)를 고치지 않고 코디네이터 폴더에 따로 둔다.
// 색은 coordinator.css 의 --coord-* 변수만 쓴다.
import type { ReactNode } from "react";
import { courseName, manageLabels, manageTone, type CoordTone } from "@/components/coordinator/coordLabels";
import { subStatusLabels } from "@/components/student/labels";
import { ApiError } from "@/lib/api/client";
import type { CoordStudentStatus } from "@/types/coordinator";
import type { SubStatus } from "@/types/student";

const ICONS = {
  home: "M3 10 12 3l9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z",
  people:
    "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75",
  doc: "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8ZM14 2v6h6M8 13h8M8 17h5",
  help: "M21 11.5a8.5 8.5 0 0 1-8.5 8.5H4l-2 2V11.5a9.5 9.5 0 0 1 19 0ZM8 10h8M8 14h5",
  chart: "M4 3v18h17M8 16v-4M13 16V6M18 16V9",
  arrow: "M5 12h14M13 6l6 6-6 6",
  back: "M19 12H5M11 6l-6 6 6 6",
  phone: "M22 16v3a2 2 0 0 1-2 2A19 19 0 0 1 3 4a2 2 0 0 1 2-2h3l2 5-3 2a14 14 0 0 0 8 8l2-3Z",
  mail: "M4 5h16a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1M3 7l9 6 9-6",
  sms: "M21 11.5a8.5 8.5 0 0 1-8.5 8.5H4l-2 2V11.5a9.5 9.5 0 0 1 19 0Z",
  search: "M21 21l-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0",
  link: "M10 13a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-2 2M14 11a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l2-2",
  calendar: "M8 2v4M16 2v4M3 10h18M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2",
  bell: "M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4",
  chevron: "m8 10 4 4 4-4",
  close: "M6 6l12 12M18 6 6 18",
  refresh: "M21 12a9 9 0 1 1-2.64-6.36M21 3v6h-6",
  info: "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20M12 8v4M12 16h.01",
  /** 새싹 (수료생 화면 StudentIcon 의 sprout 과 같은 모양) */
  sprout: "M12 21v-8M12 15C4 15 3 10 4 6c6 0 9 3 8 9ZM12 12c0-6 3-9 8-9 1 6-2 10-8 10",
  pencil: "M17 3a2.8 2.8 0 0 1 4 4L7.5 20.5 2 22l1.5-5.5Z",
  checkCircle: "M22 11.1V12a10 10 0 1 1-5.9-9.1M22 4 12 14l-3-3",
  user: "M20 21a8 8 0 0 0-16 0M12 13a5 5 0 1 0 0-10 5 5 0 0 0 0 10Z",
} as const;
export type IconName = keyof typeof ICONS;

export function Icon({ name, className = "size-[19px]" }: { name: IconName; className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      aria-hidden="true"
      className={`shrink-0 fill-none stroke-current ${className}`}
      strokeWidth={1.7}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d={ICONS[name]} />
    </svg>
  );
}

const TONE: Record<CoordTone, string> = {
  green: "bg-(--coord-green-soft) text-(--coord-green-ink)",
  orange: "bg-(--coord-orange-soft) text-(--coord-orange)",
  red: "bg-(--coord-red-soft) text-(--coord-red)",
  blue: "bg-(--coord-blue-soft) text-(--coord-blue)",
  gray: "bg-(--coord-gray-soft) text-(--coord-gray)",
};
export const toneClass = (tone: CoordTone) => TONE[tone];

/** 이름표 테두리: 앰버 · 빨강만 테두리가 있다 (리뉴얼 .badge.amber · .badge.red) */
const TONE_BORDER: Record<CoordTone, string> = {
  green: "border-transparent",
  orange: "border-(--coord-orange-line)",
  red: "border-(--coord-red-line)",
  blue: "border-transparent",
  gray: "border-transparent",
};

/** 상태 이름표 (리뉴얼 .badge, 알약 모양). small: 표 · 목록 안의 좁은 자리용 */
export function Badge({ tone = "gray", small = false, children }: { tone?: CoordTone; small?: boolean; children: ReactNode }) {
  return (
    <span
      className={`inline-flex items-center gap-[5px] rounded-full border leading-[1.2] font-bold whitespace-nowrap ${
        small ? "px-2 py-[3px] text-xs" : "px-2.5 py-1 text-[13.5px]"
      } ${TONE[tone]} ${TONE_BORDER[tone]}`}
    >
      {children}
    </span>
  );
}

/** 페이지 제목 (+ 오른쪽 동작). 1차 피드백: 제목 아래 설명은 두지 않고 제목만 30px 로 키운다 */
export function PageTitle({ title, action }: { title: string; action?: ReactNode }) {
  return (
    <div className="mb-5 flex items-center justify-between gap-4">
      <h1 className="min-w-0 text-[30px] leading-[1.3] font-extrabold tracking-[-0.5px]">{title}</h1>
      {action && <div className="flex shrink-0 items-center gap-2.5">{action}</div>}
    </div>
  );
}

/** 흰 카드 (리뉴얼 .app-card) */
export function Panel({ children, className = "", label }: { children: ReactNode; className?: string; label?: string }) {
  return (
    <section
      aria-label={label}
      className={`overflow-hidden rounded-[10px] border border-(--coord-line) bg-(--coord-surface) shadow-(--coord-shadow) ${className}`}
    >
      {children}
    </section>
  );
}

/** 카드 머리 (리뉴얼 .app-card-header) */
export function PanelHead({ title, children, sub }: { title: ReactNode; children?: ReactNode; sub?: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3 border-b border-(--coord-line) px-5 py-4">
      <div className="min-w-0">
        <h2 className="flex items-center gap-2 text-[17px] font-extrabold">{title}</h2>
        {sub && <p className="mt-[3px] text-[13px] text-(--coord-muted)">{sub}</p>}
      </div>
      {children}
    </div>
  );
}

/**
 * 표의 교육과정 이름 (C2 · C6 공통). 원본 이름은 그대로 두고(끝의 ' N기'만 뗌) 단어 단위로 줄을 바꾼다.
 * lines 줄을 넘으면 줄임표. 전체 이름은 마우스 안내(title)와 화면 낭독기로 확인할 수 있다
 */
export function CourseLabel({ label, lines = 2 }: { label: string; lines?: 1 | 2 }) {
  const name = courseName(label);
  return (
    <span title={label} className={`block leading-[1.4] break-keep ${lines === 1 ? "truncate" : "line-clamp-2"}`}>
      {name}
    </span>
  );
}

/**
 * 관리 상태 배지 + 세부 상태 (C2 · C6 공통). 세부 상태는 지우지 않는다.
 * stacked: 배지 아래 줄(C2, 행이 두 줄 높이) · 아니면 배지 옆 같은 줄(C6, 한 줄 행). 넘치면 줄임표 + 마우스 안내
 */
export function ManageStatus({ status, subStatus, stacked = false }: { status: CoordStudentStatus; subStatus: SubStatus | null; stacked?: boolean }) {
  const sub = subStatus ? subStatusLabels[subStatus] : null;
  return (
    <span className={`flex min-w-0 ${stacked ? "flex-col items-start gap-0.5" : "items-center gap-1.5"}`} title={sub ? `${manageLabels[status]} · ${sub}` : undefined}>
      <Badge tone={manageTone[status]}>{manageLabels[status]}</Badge>
      {sub && <span className="max-w-full truncate text-xs text-(--coord-muted)">{sub}</span>}
    </span>
  );
}

/** 이름 첫 글자를 뺀 두 글자 (리뉴얼 .task-avatar). red: 도움 요청 행 */
export function Avatar({ name, large = false, small = false, red = false }: { name: string; large?: boolean; small?: boolean; red?: boolean }) {
  return (
    <span
      aria-hidden="true"
      className={`inline-grid shrink-0 place-items-center rounded-full border font-extrabold ${
        red ? "border-(--coord-red-line) bg-(--coord-red-soft) text-(--coord-red)" : "border-(--coord-tint-line) bg-(--coord-tint) text-(--coord-green)"
      } ${
        large ? "size-[60px] text-xl" : small ? "size-[34px] text-[13px]" : "size-10 text-sm"
      }`}
    >
      {name.length > 2 ? name.slice(1) : name}
    </span>
  );
}

/** 불러오기 · 오류 · 다시 시도 */
/**
 * 조회 실패 문구: 서버 메시지 + 원인 (HTTP 상태 · 오류 코드). 목업으로 대신 보여 주지 않고 실패를 그대로 알린다.
 * 예: "일시적인 오류가 생겼어요. (HTTP 500 · INTERNAL_ERROR)" · 서버가 꺼져 있으면 "백엔드 서버에 연결할 수 없어요. (NETWORK_ERROR)"
 */
export function apiErrorText(e: unknown, fallback: string) {
  if (!(e instanceof ApiError)) return fallback;
  if (e.status === 0) return `백엔드 서버에 연결할 수 없어요. 서버가 켜져 있는지 확인해 주세요. (${e.code})`;
  return `${e.message} (HTTP ${e.status} · ${e.code})`;
}

export function LoadState({ error, onRetry, loadingText }: { error: string | null; onRetry: () => void; loadingText: string }) {
  return (
    <div className="flex min-h-[40vh] flex-col items-center justify-center gap-3 text-[15px] text-(--coord-muted)">
      <p role={error ? "alert" : "status"}>{error ?? loadingText}</p>
      {error && (
        <button type="button" onClick={onRetry} className="font-bold text-(--coord-green) focus-visible:outline-2 focus-visible:outline-(--coord-green)">
          다시 시도
        </button>
      )}
    </div>
  );
}

/** 버튼 모양 (리뉴얼 .btn: 4px 모서리 · 굵은 글자) */
export const btn = {
  base: "inline-flex items-center justify-center gap-[7px] rounded-[4px] border px-4 py-[9px] text-[14.5px] leading-[1.25] font-bold whitespace-nowrap transition focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-(--coord-brand) disabled:cursor-not-allowed disabled:opacity-50",
  outline:
    "border-(--coord-line) bg-(--coord-surface) text-(--coord-ink) hover:border-(--coord-line-strong) hover:bg-(--coord-page) disabled:hover:border-(--coord-line) disabled:hover:bg-(--coord-surface)",
  primary:
    "border-(--coord-brand) bg-(--coord-brand) text-(--coord-surface) hover:border-(--coord-brand-hover) hover:bg-(--coord-brand-hover) disabled:hover:border-(--coord-brand) disabled:hover:bg-(--coord-brand)",
  danger: "border-(--coord-red-line) bg-(--coord-red-soft) text-(--coord-red) hover:bg-(--coord-red-line)/40",
  /** base 의 크기와 겹치므로 ! 로 확실히 덮는다 (Tailwind 는 클래스 순서가 아니라 CSS 생성 순서로 이겨서 sm 이 무시될 수 있음) */
  sm: "px-3! py-1.5! text-[13.5px]!",
  /** 툴바 · 탭 · 페이지 이동 컨트롤 높이 36px 로 맞춤 (sm · 기본과 함께 쓴다) */
  ctl: "h-9 py-0!",
} as const;

/** '목록으로' 돌아가기 링크 (2차 피드백: 배경 · 테두리 없이 아이콘과 글씨만, C3 · C4 공용) */
export const backLink =
  "inline-flex items-center gap-1.5 rounded-[4px] text-[14.5px] font-bold text-(--coord-muted) transition-colors hover:text-(--coord-ink) focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-(--coord-green)";

/**
 * 제목 오른쪽 '목록 새로고침' (C1 · C4 목록 · 도움 요청). 페이지 전체가 아니라 그 화면의 조회 API 만 다시 부른다.
 * 조회 중에는 비활성화 · 아이콘 회전. 실패 문구는 버튼 왼쪽 한 줄 (레이아웃 높이를 바꾸지 않음), 기존 목록은 화면 쪽에서 그대로 둔다
 */
export function RefreshAction({ busy, error, onRefresh, children }: { busy: boolean; error: string | null; onRefresh: () => void; children?: ReactNode }) {
  return (
    <div className="flex min-w-0 items-center gap-2.5">
      {error && (
        <p role="alert" title={error} className="max-w-[420px] truncate text-[13px] font-semibold text-(--coord-red)">
          새로고침 실패 · {error}
        </p>
      )}
      {children}
      <button type="button" onClick={onRefresh} disabled={busy} aria-busy={busy} className={`${btn.base} ${btn.outline} ${btn.sm} ${btn.ctl}`}>
        <Icon name="refresh" className={`size-3.5 ${busy ? "animate-spin" : ""}`} />
        {busy ? "새로고침 중…" : "목록 새로고침"}
      </button>
    </div>
  );
}
