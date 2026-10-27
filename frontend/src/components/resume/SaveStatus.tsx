// 주인: FE-B — M6 저장 상태 표시 ("자동 저장됨"). 저장 중 · 실패 상태는 편집 저장(FE-B-05)에서 바꿔 준다
export type SaveState = "SAVED" | "SAVING" | "ERROR";

const LABEL: Record<SaveState, { text: string; className: string }> = {
  SAVED: { text: "✓ 자동 저장됨", className: "text-primary" },
  SAVING: { text: "저장 중…", className: "text-ink-muted" },
  ERROR: { text: "저장하지 못했어요", className: "text-danger" },
};

export default function SaveStatus({ state }: { state: SaveState }) {
  const label = LABEL[state];
  return (
    <span role="status" className={`whitespace-nowrap text-xs font-bold ${label.className}`}>
      {label.text}
    </span>
  );
}
