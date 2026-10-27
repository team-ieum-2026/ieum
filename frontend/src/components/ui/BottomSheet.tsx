"use client";

import { useEffect, useId, useRef, type ReactNode } from "react";

export default function BottomSheet({ open, onClose, title, children }: { open: boolean; onClose: () => void; title: string; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
    const previous = document.body.style.overflow;
    if (open) document.body.style.overflow = "hidden";
    return () => { document.body.style.overflow = previous; };
  }, [open]);
  return <dialog ref={ref} aria-labelledby={titleId} onCancel={(event) => { event.preventDefault(); onClose(); }} className="fixed inset-x-0 top-auto bottom-0 m-0 mx-auto max-h-[90dvh] w-[390px] max-w-full overflow-y-auto rounded-t-3xl bg-surface px-5 pt-3 pb-6 text-ink backdrop:bg-ink/45">
    <div aria-hidden="true" className="mx-auto mb-5 h-1 w-10 rounded bg-line" />
    <div className="mb-4 flex items-start justify-between gap-3"><h2 id={titleId} className="text-lg font-bold">{title}</h2><button type="button" aria-label="닫기" onClick={onClose} className="size-8 shrink-0 rounded text-ink-sub focus-visible:outline-2 focus-visible:outline-primary">✕</button></div>
    {children}
  </dialog>;
}
