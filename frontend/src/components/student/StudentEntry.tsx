"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { getStudentName } from "@/lib/api/student";
import { studentHref } from "./navigation";
import { errorMessage } from "./useApi";
import StudentIcon from "./StudentIcon";

export default function StudentEntry({ onNeedHelp }: { onNeedHelp: () => void }) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [studentId, setStudentId] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const submitting = useRef(false);
  const dialogRef = useRef<HTMLDialogElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      dialog.showModal();
      inputRef.current?.focus({ preventScroll: true });
    } else if (!open && dialog.open) dialog.close();
  }, [open]);

  const close = () => { if (!submitting.current) setOpen(false); };

  return (
    <div className="student-entry">
      <button className="role-card student-card" type="button" aria-haspopup="dialog" aria-expanded={open} aria-controls="student-entry-dialog" disabled={busy} onClick={() => { setOpen(true); setError(null); }}>
        <span className="role-icon"><StudentIcon name="cap" className="icon" /></span>
        <span className="role-copy"><strong>수료생</strong><span>취업 기회와 맞춤형 지원 확인</span></span>
        <StudentIcon name="chevron" className="icon role-arrow" />
      </button>
        <dialog ref={dialogRef} id="student-entry-dialog" className="student-entry-dialog" aria-labelledby="student-entry-title" onClose={() => setOpen(false)} onCancel={(event) => {
          if (submitting.current) event.preventDefault();
          else setOpen(false);
        }} onClick={(event) => {
          if (event.target !== event.currentTarget) return;
          const bounds = event.currentTarget.getBoundingClientRect();
          if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) close();
        }}>
          <button className="dialog-close" type="button" aria-label="수료생 ID 입력 닫기" disabled={busy} onClick={close}><StudentIcon name="close" className="icon" /></button>
          <h2 id="student-entry-title">수료생으로 시작하기</h2>
          <form id="student-entry-form" autoComplete="off" noValidate aria-busy={busy} onSubmit={async (event) => {
            event.preventDefault();
            if (submitting.current) return;
            const value = studentId.trim();
            const id = Number(value);
            if (!value) { setError("안내받은 수료생 ID를 입력해주세요."); return; }
            if (!/^[0-9]+$/.test(value) || !Number.isSafeInteger(id) || id < 1) { setError("수료생 ID는 1 이상의 숫자로 입력해주세요."); return; }
            submitting.current = true; setBusy(true); setError(null);
            try {
              await getStudentName(id);
              router.push(studentHref("/student", id));
            } catch (error) {
              setError(errorMessage(error)); setBusy(false); submitting.current = false;
            }
          }}>
            <label htmlFor="student-entry-id" className="sr-only">수료생 ID</label>
            <p className="field-hint" id="student-entry-id-help">담당 매니저에게 안내받은 ID를 입력해주세요.</p>
            <div className={`input-wrap${error ? " is-error" : ""}`}>
              <StudentIcon name="cap" className="icon" />
              <input ref={inputRef} id="student-entry-id" name="studentId" type="text" inputMode="numeric" autoComplete="off" pattern="[0-9]+" placeholder="수료생 ID 입력" required value={studentId} disabled={busy} onChange={(event) => { setStudentId(event.target.value); setError(null); }} aria-invalid={Boolean(error)} aria-describedby={error ? "student-entry-id-help student-entry-error" : "student-entry-id-help"} />
            </div>
            {error && <p id="student-entry-error" role="alert">{error}</p>}
            <div className="form-actions">
              <button className="text-button" type="button" disabled={busy} onClick={onNeedHelp}>ID를 모르겠어요</button>
              <button className="primary-button" type="submit" disabled={busy}>{busy ? "확인 중…" : "수료생으로 시작하기"}<StudentIcon name="chevron" className="icon" /></button>
            </div>
          </form>
        </dialog>
    </div>
  );
}
