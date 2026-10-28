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
    <button type="button" aria-expanded={open} aria-controls="student-entry-form" onClick={() => setOpen(!open)} className="flex w-full flex-col gap-0.5 rounded-[14px] bg-primary px-5 py-[18px] text-left text-surface hover:bg-primary-strong focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-primary">
      <span className="text-[17px] font-bold">수료생으로 보기</span>
      <span className="text-[13px] text-primary-soft">모바일 · 맞춤 공고와 지원 서류</span>
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
      <label htmlFor="student-entry-id" className="block text-sm font-bold">수료생 ID</label>
      <input id="student-entry-id" name="studentId" type="text" inputMode="numeric" autoComplete="off" pattern="[0-9]+" placeholder="아이디를 입력해주세요" required value={studentId} disabled={busy} onChange={(event) => { setStudentId(event.target.value); setError(null); }} aria-invalid={Boolean(error)} aria-describedby={error ? "student-entry-error" : undefined} className="min-h-11 w-full rounded-xl border border-line bg-surface px-3 text-sm focus:placeholder:text-transparent" />
      {error && <p id="student-entry-error" role="alert" className="text-sm text-danger">{error}</p>}
      <Button type="submit" disabled={busy} className="w-full">{busy ? "확인 중…" : "확인"}</Button>
    </form>}
  </div>;
}
