"use client";
// 주인: FE-B — 통화 · 상담 기록 추가 창 (C3 수료생 상세 · C5 도움 요청 공용).
// C3 의 기존 기록 추가(연락 유형 + 상담 내용, POST /api/coordinator/students/{id}/contact-logs)를 화면 가운데 창으로 옮긴 것.
// 기록은 수료생 단위라 도움 요청과 연결되지 않는다. 배경 · Esc · 닫기 버튼으로 닫히고(저장 중 제외), 내용이 길면 창 안에서만 스크롤한다.
import { useEffect, useId, useRef, useState } from "react";
import { Icon, btn } from "@/components/coordinator/CoordUi";
import { errorMessage } from "@/components/student/useApi";
import { addContactLog } from "@/lib/api/coordinatorStudents";
import type { ContactLog, ContactType } from "@/types/student";

const contactTypeLabels: Record<ContactType, string> = { PHONE: "통화", MEETING: "상담", MESSAGE: "메시지" };

export default function ContactLogDialog({
  studentId,
  studentName,
  onClose,
  onSaved,
}: {
  studentId: number;
  /** 있으면 제목 아래에 누구의 기록인지 보여 준다 (C5) */
  studentName?: string;
  onClose: () => void;
  onSaved: (log: ContactLog) => void;
}) {
  const titleId = useId();
  const panelRef = useRef<HTMLDivElement>(null);
  const [contactType, setContactType] = useState<ContactType>("PHONE");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const close = () => {
    if (!busy) onClose();
  };

  useEffect(() => {
    panelRef.current?.focus();
  }, []);
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape" && !busy) onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [busy, onClose]);

  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!note.trim() || busy) return;
    setBusy(true);
    setError(null);
    try {
      onSaved(await addContactLog(studentId, { contactType, note: note.trim() }));
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 z-30 flex items-center justify-center bg-(--coord-forest)/40 p-4 backdrop-blur-[3px]" onClick={close}>
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onClick={(e) => e.stopPropagation()}
        className="flex max-h-[calc(100vh-32px)] w-full max-w-[520px] flex-col rounded-[10px] border border-(--coord-line) bg-(--coord-surface) shadow-[0_20px_40px_rgb(0_0_0/0.16)] outline-none"
      >
        <div className="flex shrink-0 items-start justify-between gap-3 border-b border-(--coord-line) px-6 py-4">
          <div className="min-w-0">
            <h2 id={titleId} className="text-xl font-extrabold">
              통화 · 상담 기록 추가
            </h2>
            {studentName && <p className="mt-0.5 text-[13px] text-(--coord-muted)">{studentName} 님 상담 기록에 남아요.</p>}
          </div>
          <button
            type="button"
            onClick={close}
            disabled={busy}
            aria-label="닫기"
            className="rounded-[4px] p-1 text-(--coord-muted) hover:bg-(--coord-head) hover:text-(--coord-ink) focus-visible:outline-3 focus-visible:outline-(--coord-green)"
          >
            <Icon name="close" className="size-5" />
          </button>
        </div>
        <form onSubmit={save} className="flex min-h-0 flex-col">
          <div className="flex min-h-0 flex-col gap-4 overflow-y-auto px-6 py-5 text-sm">
            <label className="flex flex-col gap-1.5 font-bold">
              연락 유형
              <select
                value={contactType}
                disabled={busy}
                onChange={(event) => setContactType(event.target.value as ContactType)}
                className="h-11 rounded-[4px] border border-(--coord-line-strong) bg-(--coord-surface) px-3 font-normal"
              >
                {(Object.keys(contactTypeLabels) as ContactType[]).map((value) => (
                  <option key={value} value={value}>
                    {contactTypeLabels[value]}
                  </option>
                ))}
              </select>
            </label>
            <label className="flex flex-col gap-1.5 font-bold">
              상담 내용
              <textarea
                required
                rows={6}
                value={note}
                disabled={busy}
                onChange={(event) => setNote(event.target.value)}
                placeholder="상담 내용을 남겨주세요"
                className="resize-y rounded-[4px] border border-(--coord-line-strong) px-3 py-2.5 font-normal leading-[1.6] focus-visible:outline-2 focus-visible:outline-(--coord-green)"
              />
            </label>
            {error && (
              <p role="alert" className="text-[13px] text-(--coord-red)">
                {error}
              </p>
            )}
          </div>
          <div className="flex shrink-0 gap-2 border-t border-(--coord-line) px-6 py-4">
            <button type="button" onClick={close} disabled={busy} className={`${btn.base} ${btn.outline} flex-1 py-3`}>
              취소
            </button>
            <button type="submit" disabled={!note.trim() || busy} aria-busy={busy} className={`${btn.base} ${btn.primary} flex-1 py-3`}>
              {busy ? "저장 중…" : "저장"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
