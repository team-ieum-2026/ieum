import type { HTMLAttributes } from "react";

export default function Card({ className = "", ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div {...props} className={`rounded-2xl border border-line bg-surface p-4 ${className}`} />;
}
