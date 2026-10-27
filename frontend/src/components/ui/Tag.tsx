import type { Source } from "@/types/student";

const styles = { AI: "bg-primary-tint text-primary-strong", REGISTERED: "bg-coord-bg text-ink-sub", USER: "bg-warn-bg text-warn-ink" };
const labels = { AI: "AI 작성", REGISTERED: "등록 자료", USER: "직접 입력" };

export default function Tag({ source }: { source: Source }) {
  return <span className={`inline-flex rounded-md px-2 py-0.5 text-xs font-bold ${styles[source]}`}>{labels[source]}</span>;
}
