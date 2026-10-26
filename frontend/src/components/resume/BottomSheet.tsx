"use client";
// 주인: FE-B — 아래에서 올라오는 시트 공통 틀. M6 카드 편집 · 제출 확인, M6-2 섹션 편집(FE-B-05)에서 같이 쓴다
// 배경을 누르거나 Esc 를 누르면 닫힌다.
import { useEffect, useId, useRef } from "react";

interface BottomSheetProps {
  open: boolean;
  title: string;
  description?: string;
  onClose: () => void;
  children?: React.ReactNode;
  /** 아래쪽 버튼 영역 */
  actions: React.ReactNode;
}

export default function BottomSheet({ open, title, description, onClose, children, actions }: BottomSheetProps) {
  const titleId = useId();
  const sheetRef = useRef<HTMLElement>(null);

  useEffect(() => {
    if (!open) return;
    sheetRef.current?.focus();
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-30 flex items-end justify-center bg-ink/50" onClick={onClose}>
      <section
        ref={sheetRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onClick={(e) => e.stopPropagation()}
        className="max-h-[80vh] w-[390px] overflow-y-auto rounded-t-[22px] bg-surface px-5 pb-7 pt-5 outline-none"
      >
        <h2 id={titleId} className="text-lg font-bold">
          {title}
        </h2>
        {description && <p className="mt-2 text-[13px] leading-relaxed text-ink-sub">{description}</p>}
        {children}
        <div className="mt-5 flex gap-2">{actions}</div>
      </section>
    </div>
  );
}
