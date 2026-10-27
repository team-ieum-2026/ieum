import type { ReactNode } from "react";

export default function StatusChip({ children, tone = "neutral" }: { children: ReactNode; tone?: "neutral" | "success" | "warning" | "danger" }) {
  const styles = { neutral: "bg-coord-bg text-ink-sub", success: "bg-primary-soft text-primary-strong", warning: "bg-warn-bg text-warn-ink", danger: "bg-danger-bg text-danger" };
  return <span className={`inline-flex shrink-0 items-center rounded-md px-2 py-1 text-xs font-bold ${styles[tone]}`}>{children}</span>;
}
