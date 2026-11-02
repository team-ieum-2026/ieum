"use client";

import { useRef, useState } from "react";
import BottomSheet from "@/components/ui/BottomSheet";
import { ApiError } from "@/lib/api/client";
import { cancelItemResponse } from "@/lib/api/student";
import type { ResponseType } from "@/types/student";
import { clearRememberedResponse } from "./mockRecommendationResponses";
import { ActionError } from "./ScreenParts";
import { errorMessage } from "./useApi";

export default function ResponseCancelButton({ itemId, studentId, response, onCancelled }: {
  itemId: number; studentId: number; response: ResponseType; onCancelled: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const busyRef = useRef(false);
  if (response === "NONE") return null;
  const label = response === "APPLY" ? "지원 취소" : response === "PASS" ? "패스 취소" : "도움 요청 취소";
  const close = () => { if (!busyRef.current) setOpen(false); };
  async function confirm() {
    if (busyRef.current || response === "NONE") return;
    busyRef.current = true; setBusy(true); setError(null);
    try {
      await cancelItemResponse(itemId, response, studentId);
      clearRememberedResponse(studentId, itemId);
      setOpen(false);
      onCancelled();
    } catch (failure) {
      setError(failure instanceof ApiError && [404, 405, 501].includes(failure.status)
        ? "지금은 취소 요청을 처리할 수 없어요. 기존 응답은 유지돼요."
        : errorMessage(failure));
    } finally { busyRef.current = false; setBusy(false); }
  }
  return <>
    <button type="button" className="s-response-cancel" aria-label={label} onClick={() => { setError(null); setOpen(true); }}>취소</button>
    <BottomSheet open={open} title={`${label}할까요?`} onClose={close}>
      <p className="s-dialog-description">{response === "APPLY"
        ? "지원 준비를 취소하면 이 공고의 작성 중인 지원서가 삭제되고, 응답 전 상태로 돌아가요. 이미 검토를 요청한 지원서는 직접 취소할 수 없어요."
        : response === "PASS"
          ? "패스 사유와 메모를 지우고 응답 전 상태로 돌아가요. 다시 지원하거나 패스할 수 있어요."
          : "도움 요청을 취소하고 응답 전 상태로 돌아가요. 담당자가 이미 연락을 시작한 요청은 직접 취소할 수 없어요. 취소 후에는 다시 도움을 요청할 수 있어요."}</p>
      <p className="s-source-note">내 프로필에 저장한 자료는 유지돼요.</p>
      <ActionError message={error} />
      {busy && <p role="status" className="s-source-note">취소 요청을 처리하고 있어요…</p>}
      <div className="s-cancel-dialog-actions"><button type="button" className="s-button" disabled={busy} onClick={close}>유지하기</button><button type="button" className="s-button s-button-primary" disabled={busy} onClick={confirm}>{busy ? "취소 중…" : label}</button></div>
    </BottomSheet>
  </>;
}
