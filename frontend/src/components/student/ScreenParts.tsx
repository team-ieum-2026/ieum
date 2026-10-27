import Link from "next/link";
import type { ReactNode } from "react";
import Button from "@/components/ui/Button";

export function StudentScreen({ children }: { children: ReactNode }) {
  return <div className="flex flex-1 flex-col bg-surface-warm text-ink">{children}</div>;
}

export function ScreenHeader({ title, back }: { title: string; back?: string }) {
  return <header className="flex min-h-16 items-center gap-1 px-3 pt-2">{back && <Link href={back} aria-label="뒤로" className="flex size-11 items-center justify-center rounded-lg text-ink focus-visible:outline-2 focus-visible:outline-primary"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="m15 18-6-6 6-6" /></svg></Link>}<span className="text-base font-bold">{title}</span></header>;
}

export function ApiState({ loading, error, retry }: { loading: boolean; error: string | null; retry: () => void }) {
  if (error) return <div role="alert" className="space-y-4 p-6 text-sm"><p className="text-danger">{error}</p><Button variant="secondary" onClick={retry}>다시 시도</Button></div>;
  if (loading) return <p role="status" className="p-6 text-sm text-ink-sub">불러오고 있어요…</p>;
  return null;
}

export function ActionError({ message }: { message: string | null }) {
  return message ? <p role="alert" className="text-sm text-danger">{message}</p> : null;
}
