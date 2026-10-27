// 주인: FE-B — 출처 태그 (AI 작성 · 등록 · 직접 입력). M6 · M6-1 · C4 에서 같이 쓴다
import type { Source } from "@/types/student";

const STYLE: Record<Source, { label: string; className: string }> = {
  AI: { label: "AI 작성", className: "bg-primary-tint text-primary-strong" },
  REGISTERED: { label: "등록", className: "bg-page text-ink-sub" },
  USER: { label: "직접 입력", className: "bg-warn-bg text-warn-ink" },
};

interface SourceTagProps {
  source: Source;
  /** 기본 문구 대신 쓸 문구 (예: "AI 작성 4문장 · 근거 연결") */
  label?: string;
}

export default function SourceTag({ source, label }: SourceTagProps) {
  const style = STYLE[source];
  return (
    <span className={`shrink-0 whitespace-nowrap rounded-[5px] px-1.5 py-0.5 text-[10px] font-bold ${style.className}`}>
      {label ?? style.label}
    </span>
  );
}
