import Link from "next/link";
import type { ReactNode } from "react";
import Button from "@/components/ui/Button";
import { ApiError } from "@/lib/api/client";

export function StudentScreen({ children, lighter = false }: { children: ReactNode; lighter?: boolean }) {
  return <div className={`flex flex-1 flex-col text-ink ${lighter ? "bg-[color-mix(in_srgb,var(--color-surface-warm)_60%,var(--color-surface))]" : "bg-surface-warm"}`}>{children}</div>;
}

export function ScreenHeader({ title, back }: { title: string; back?: string }) {
  return <header className="flex min-h-16 items-center gap-1 px-3 pt-2">{back && <Link href={back} aria-label="뒤로" className="flex size-11 items-center justify-center rounded-lg text-ink focus-visible:outline-2 focus-visible:outline-primary"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="m15 18-6-6 6-6" /></svg></Link>}<span className="text-base font-bold">{title}</span></header>;
}

export function ApiState({ loading, error, retry, notFoundHref = "/", notFoundLabel = "처음으로" }: { loading: boolean; error: string | Error | null; retry: () => void; notFoundHref?: string; notFoundLabel?: string }) {
  if (error) {
    const notFound = error instanceof ApiError && error.status === 404;
    return <div role="alert" className="space-y-4 p-6 text-sm"><p className="text-danger">{error instanceof Error ? error.message : error}</p>{notFound
      ? <Link href={notFoundHref} className="inline-flex min-h-11 items-center rounded-xl border border-line bg-surface px-4 py-2 text-sm font-bold text-ink">{notFoundLabel}</Link>
      : <Button variant="secondary" onClick={retry}>다시 시도</Button>}</div>;
  }
  if (loading) return <p role="status" className="p-6 text-sm text-ink-sub">불러오고 있어요…</p>;
  return null;
}

export function ActionError({ message }: { message: string | null }) {
  return message ? <p role="alert" className="text-sm text-danger">{message}</p> : null;
}
