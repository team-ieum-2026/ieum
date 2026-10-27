// 주인: FE-B — M6 · M6-1 저장 상태 표시 ("저장됨"). 버튼을 누를 때 저장하므로 "자동"이라고 쓰지 않는다
export type SaveState = "SAVED" | "SAVING" | "ERROR";

const LABEL: Record<SaveState, { text: string; className: string }> = {
  SAVED: { text: "✓ 저장됨", className: "text-primary" },
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
