"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { getStudentName } from "@/lib/api/student";
import Button from "@/components/ui/Button";
import { studentHref } from "./navigation";
import { errorMessage } from "./useApi";

export default function StudentEntry() {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [studentId, setStudentId] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return <div className="space-y-3">
    <button type="button" aria-expanded={open} aria-controls="student-entry-form" onClick={() => setOpen(!open)} className="flex min-h-[75px] w-full items-center gap-3 rounded-2xl border border-primary/40 bg-primary-tint px-3 py-3 text-left hover:border-primary hover:bg-primary-soft focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-primary">
      <span className="flex size-[34px] shrink-0 items-center justify-center rounded-xl bg-primary-soft text-primary">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="m3 9 9-4 9 4-9 4-9-4ZM7 11v6c3 2 7 2 10 0v-6M21 9v6" />
        </svg>
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-[17px] leading-6 font-bold text-primary-strong">수료생</span>
        <span className="block text-[13px] leading-5 text-ink-sub">취업 기회와 맞춤형 지원 확인</span>
      </span>
      <svg className="shrink-0 text-primary/60" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="m9 5 7 7-7 7" />
      </svg>
    </button>
    {open && <form id="student-entry-form" autoComplete="off" noValidate className="space-y-3 rounded-xl border border-line bg-surface p-4" onSubmit={async (event) => {
      event.preventDefault();
      if (busy) return;
      if (!studentId.trim()) {
        setError("아이디를 입력해주세요");
        return;
      }
      const id = Number(studentId);
      if (!/^[0-9]+$/.test(studentId) || !Number.isSafeInteger(id) || id < 1) {
        setError("존재하지 않는 ID에요. 다시 입력해주세요.");
        return;
      }
      setBusy(true); setError(null);
      try {
        await getStudentName(id);
        router.push(studentHref("/student", id));
      } catch (error) { setError(errorMessage(error)); setBusy(false); }
    }}>
      <div className="space-y-1">
        <label htmlFor="student-entry-id" className="block text-sm font-bold">수료생 ID</label>
        <p id="student-entry-id-help" className="text-[13px] leading-5 text-ink-sub">안내받은 수료생 ID를 입력해주세요.</p>
      </div>
      <input id="student-entry-id" name="studentId" type="text" inputMode="numeric" autoComplete="off" pattern="[0-9]+" placeholder="아이디를 입력해주세요" required value={studentId} disabled={busy} onChange={(event) => { setStudentId(event.target.value); setError(null); }} aria-invalid={Boolean(error)} aria-describedby={error ? "student-entry-id-help student-entry-error" : "student-entry-id-help"} className="min-h-11 w-full rounded-xl border border-line bg-surface px-3 text-sm focus:placeholder:text-transparent" />
      {error && <p id="student-entry-error" role="alert" className="text-sm text-danger">{error}</p>}
      <Button type="submit" disabled={busy} className="w-full">{busy ? "확인 중…" : "확인"}</Button>
    </form>}
  </div>;
}
