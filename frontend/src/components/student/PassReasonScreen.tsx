"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import Button from "@/components/ui/Button";
import BottomSheet from "@/components/ui/BottomSheet";
import { respondToItem } from "@/lib/api/student";
import { rememberResponse } from "./mockRecommendationResponses";
import type { PassReason } from "@/types/student";
import JobDetailScreen from "./JobDetailScreen";
import { ActionError } from "./ScreenParts";
import { passLabels } from "./labels";
import { studentHref } from "./navigation";
import { errorMessage } from "./useApi";

export default function PassReasonScreen({ itemId, studentId }: { itemId: number; studentId: number }) {
  const router = useRouter();
  const [reason, setReason] = useState<PassReason | null>(null);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const close = () => { if (!busy) router.push(studentHref(`/student/jobs/${itemId}`, studentId)); };
  return <>
    <JobDetailScreen itemId={itemId} studentId={studentId} />
    <BottomSheet open onClose={close} title="패스하는 이유를 골라주세요">
      <p className="mb-4 text-[13px] text-ink-sub">이유에 맞춰 다음 추천을 바꿔드려요.</p>
      <form className="space-y-2.5" onSubmit={async (event) => {
        event.preventDefault(); if (!reason || busy) return;
        setBusy(true); setError(null);
        try { await respondToItem(itemId, { response: "PASS", passReason: reason, passNote: reason === "OTHER" ? note.trim() || null : null }, studentId); rememberResponse(studentId, itemId, "PASS"); router.push(studentHref("/student", studentId)); }
        catch (error) { setError(errorMessage(error)); setBusy(false); }
      }}>
        <fieldset disabled={busy} className="space-y-2.5"><legend className="sr-only">패스 사유</legend>{(Object.keys(passLabels) as PassReason[]).map((value) => <label key={value} className={`flex min-h-[50px] cursor-pointer items-center gap-3 rounded-xl border px-3 text-sm ${reason === value ? "border-primary bg-primary-tint" : "border-line bg-surface"}`}><input type="radio" name="passReason" value={value} checked={reason === value} onChange={() => setReason(value)} className="size-5 accent-primary" />{passLabels[value]}</label>)}
          <label className="block space-y-1 text-xs text-ink-sub"><span>기타를 고르면 적을 수 있어요 (담당자만 확인)</span><textarea value={note} onChange={(event) => setNote(event.target.value)} disabled={reason !== "OTHER"} rows={2} placeholder="자유롭게 적어주세요" className="w-full resize-none rounded-xl border border-line bg-surface p-3 text-sm text-ink disabled:bg-surface-warm" /></label>
        </fieldset>
        <ActionError message={error} /><div className="flex gap-2 pt-1"><Button type="button" variant="secondary" disabled={busy} onClick={close} className="flex-1">취소</Button><Button type="submit" disabled={!reason || busy} className="flex-[2]">{busy ? "처리 중…" : "패스하기"}</Button></div>
      </form>
    </BottomSheet>
  </>;
}
