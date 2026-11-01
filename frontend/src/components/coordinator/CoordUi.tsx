// 주인: FE-B — 코디네이터 화면 공통 부품 (11/1 개편 목업). 공통 UI(components/ui · FE-A)를 고치지 않고 코디네이터 폴더에 따로 둔다.
// 색은 coordinator.css 의 --coord-* 변수만 쓴다.
import type { ReactNode } from "react";
import type { CoordTone } from "@/components/coordinator/coordLabels";

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

/** 상태 이름표 (목업 .badge) */
export function Badge({ tone = "gray", children }: { tone?: CoordTone; children: ReactNode }) {
  return (
    <span className={`inline-flex items-center gap-1 rounded-[5px] px-[7px] py-1 text-[11px] leading-[1.4] font-semibold whitespace-nowrap ${TONE[tone]}`}>
      {children}
    </span>
  );
}

/** 페이지 제목 + 설명 (+ 오른쪽 동작) */
export function PageTitle({ title, description, action }: { title: string; description: string; action?: ReactNode }) {
  return (
    <div className="mb-6 flex items-center justify-between gap-4">
      <div>
        <h1 className="text-[27px] leading-[1.3] font-bold tracking-[-1px]">{title}</h1>
        <p className="mt-2 text-[13px] text-(--coord-muted)">{description}</p>
      </div>
      {action}
    </div>
  );
}

/** 흰 카드 (목업 .panel) */
export function Panel({ children, className = "", label }: { children: ReactNode; className?: string; label?: string }) {
  return (
    <section aria-label={label} className={`overflow-hidden rounded-xl border border-(--coord-line) bg-(--coord-surface) ${className}`}>
      {children}
    </section>
  );
}

/** 카드 머리 (목업 .panelhead) */
export function PanelHead({ title, children, sub }: { title: ReactNode; children?: ReactNode; sub?: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3 border-b border-(--coord-line-soft) px-[22px] py-5">
      <div>
        <h2 className="flex items-center gap-2 text-[15px] font-bold">{title}</h2>
        {sub && <p className="mt-1 text-[11px] text-(--coord-muted)">{sub}</p>}
      </div>
      {children}
    </div>
  );
}

/** 이름 첫 글자를 뺀 두 글자 (목업 아바타) */
export function Avatar({ name, large = false }: { name: string; large?: boolean }) {
  return (
    <span
      aria-hidden="true"
      className={`inline-grid shrink-0 place-items-center rounded-full bg-(--coord-green-soft) font-semibold text-(--coord-green-ink) ${
        large ? "size-[58px] text-xl" : "size-[34px] text-xs"
      }`}
    >
      {name.length > 2 ? name.slice(1) : name}
    </span>
  );
}

/** 불러오기 · 오류 · 다시 시도 */
export function LoadState({ error, onRetry, loadingText }: { error: string | null; onRetry: () => void; loadingText: string }) {
  return (
    <div className="flex min-h-[40vh] flex-col items-center justify-center gap-3 text-sm text-(--coord-muted)">
      <p role={error ? "alert" : "status"}>{error ?? loadingText}</p>
      {error && (
        <button type="button" onClick={onRetry} className="font-bold text-(--coord-green) focus-visible:outline-2 focus-visible:outline-(--coord-green)">
          다시 시도
        </button>
      )}
    </div>
  );
}

/** 버튼 모양 (목업 .btn) */
export const btn = {
  base: "inline-flex items-center justify-center gap-[7px] rounded-lg border px-3.5 py-[9px] text-[13px] font-semibold whitespace-nowrap transition focus-visible:outline-3 focus-visible:outline-offset-3 focus-visible:outline-(--coord-green) disabled:cursor-not-allowed disabled:opacity-50",
  outline: "border-(--coord-line) bg-(--coord-surface) hover:bg-(--coord-hover)",
  primary: "border-(--coord-green) bg-(--coord-green) text-(--coord-surface) hover:bg-(--coord-forest)",
  danger: "border-(--coord-red-soft) bg-(--coord-red-soft) text-(--coord-red)",
  sm: "px-2.5 py-1.5 text-xs",
} as const;
