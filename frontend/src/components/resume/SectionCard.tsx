// 주인: FE-B — M6 섹션 카드 (이름 · 요약 한 줄 · 출처 태그). 누르면 편집 바텀시트를 연다 (FE-B-02)
import SourceTag from "@/components/resume/SourceTag";
import type { SectionSummary } from "@/components/resume/sectionSummary";

interface SectionCardProps {
  summary: SectionSummary;
  onOpen: (summary: SectionSummary) => void;
}

export function SectionCard({ summary, onOpen }: SectionCardProps) {
  return (
    <button
      type="button"
      onClick={() => onOpen(summary)}
      className="flex min-h-10 w-full items-center gap-2.5 border-b border-line bg-surface py-1.5 pl-3.5 pr-1.5 text-left last:border-b-0 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
    >
      <span className="w-16 shrink-0 text-[13px] font-bold">{summary.label}</span>
      <span
        className={`min-w-0 flex-1 truncate text-xs ${summary.isEmpty ? "text-ink-muted" : "text-ink-sub"}`}
      >
        {summary.text}
      </span>
      {summary.source && <SourceTag source={summary.source} />}
      <span className="flex w-7 shrink-0 justify-center text-ink-muted" aria-hidden="true">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="m9 18 6-6-6-6" />
        </svg>
      </span>
    </button>
  );
}

/** 카드 여러 장을 흰 상자 하나로 묶음 */
export function SectionCardGroup({ children }: { children: React.ReactNode }) {
  return <div className="overflow-hidden rounded-2xl border border-line bg-surface">{children}</div>;
}
