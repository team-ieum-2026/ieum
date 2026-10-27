"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import Button from "@/components/ui/Button";
import { updateHelpRequest } from "@/lib/api/student";
import type { ContactMethod } from "@/types/student";
import { ActionError, StudentScreen } from "./ScreenParts";
import { studentHref } from "./navigation";
import { errorMessage } from "./useApi";

export default function HelpRequestScreen({ helpRequestId, studentId }: { helpRequestId: number; studentId: number }) {
  const router = useRouter();
  const [method, setMethod] = useState<ContactMethod | null>(null);
  const [memo, setMemo] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return <StudentScreen><form className="flex min-h-dvh flex-col justify-between gap-8 px-5 pt-[72px] pb-7" onSubmit={async (event) => {
    event.preventDefault(); if (busy) return;
    setBusy(true); setError(null);
    try { await updateHelpRequest(helpRequestId, { contactMethod: method, memo: memo.trim() || null }); router.push(studentHref("/student", studentId)); }
    catch (error) { setError(errorMessage(error)); setBusy(false); }
  }}>
    <div className="space-y-6"><div className="space-y-3"><div aria-hidden="true" className="flex size-14 items-center justify-center rounded-full bg-primary-soft text-3xl text-primary">✓</div><h1 className="text-[22px] leading-snug font-bold">담당 코디네이터에게<br />도움 요청을 전달했어요</h1><p className="text-sm leading-relaxed text-ink-sub">확인하는 대로 먼저 연락드릴게요. 지금 상황을 길게 설명하지 않아도 괜찮아요.</p></div>
      <fieldset disabled={busy}><legend className="mb-3 text-sm font-bold">편한 연락 방법 <span className="font-normal text-ink-sub">(선택)</span></legend><div className="flex gap-2">{([["SMS", "문자"], ["PHONE", "전화"], ["EMAIL", "이메일"]] as const).map(([value, label]) => <label key={value} className={`flex min-h-12 flex-1 cursor-pointer items-center justify-center gap-2 rounded-xl border text-sm ${method === value ? "border-primary bg-primary-tint font-bold" : "border-line bg-surface"}`}><input type="radio" name="contactMethod" checked={method === value} onChange={() => setMethod(value)} className="size-4 accent-primary" />{label}</label>)}</div></fieldset>
      <label className="block space-y-2 text-sm font-bold"><span>어떤 도움이 필요한지 <span className="font-normal text-ink-sub">(선택)</span></span><textarea rows={3} value={memo} onChange={(event) => setMemo(event.target.value)} disabled={busy} placeholder="예: 지원서를 같이 봐주셨으면 해요" className="w-full resize-none rounded-xl border border-line bg-surface p-3 font-normal" /></label>
    </div><div className="space-y-3"><ActionError message={error} /><Button type="submit" disabled={busy} className="w-full">{busy ? "저장 중…" : "확인"}</Button></div>
  </form></StudentScreen>;
}
