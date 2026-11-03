"use client";

import { useCallback, useEffect, useId, useRef, useState } from "react";
import BottomSheet from "@/components/ui/BottomSheet";
import { getStudentStatus, selectStudentStatus } from "@/lib/api/profile";
import type { SelectableStudentStatus, StudentCurrentStatus } from "@/types/profile";
import { ActionError } from "./ScreenParts";
import { errorMessage, useApi } from "./useApi";

const choices: { value: SelectableStudentStatus; label: string; description: string }[] = [
  { value: "SEEKING", label: "구직 중", description: "나에게 맞는 기회를 찾고 있어요." },
  { value: "EMPLOYED", label: "취업 완료", description: "새로운 직장에서 일을 시작했어요." },
  { value: "RESTING", label: "잠시 쉬는 중", description: "쉬어가며 다음 걸음을 생각하고 있어요." },
];
const labels: Record<StudentCurrentStatus["status"], string> = {
  SEEKING: "구직 중", EMPLOYED: "취업 완료", RESTING: "잠시 쉬는 중", CHECK_REQUIRED: "추가 확인 필요",
};

/** 실제 BE의 현재 상태를 읽고, 상태 선택 결과도 서버가 반환한 값으로 표시한다. */
export default function ProfileCurrentStatus({ studentId }: { studentId: number }) {
  const load = useCallback(() => getStudentStatus(studentId), [studentId]);
  const { data, loading, error: loadError, reload, setData } = useApi(load);
  const [selected, setSelected] = useState<SelectableStudentStatus | null>(null);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState("");
  const headingId = useId();
  const radioName = useId();

  useEffect(() => {
    const refresh = () => { if (!busyRef.current) reload(); };
    window.addEventListener("focus", refresh);
    return () => window.removeEventListener("focus", refresh);
  }, [reload]);

  const close = () => { if (!busyRef.current) setOpen(false); };
  async function save() {
    if (!selected || busyRef.current) return;
    busyRef.current = true; setBusy(true); setError(null);
    try {
      const result = await selectStudentStatus(studentId, selected);
      setData(result);
      setOpen(false);
      setMessage("현재 상태를 저장했어요.");
    } catch (failure) { setError(errorMessage(failure)); }
    finally { busyRef.current = false; setBusy(false); }
  }

  return <section className="s-profile-current-status" aria-labelledby={headingId}>
    <div className="s-profile-status-heading"><h3 id={headingId}>현재 상태</h3><button type="button" disabled={!data || loading || busy} onClick={() => {
      setSelected(choices.find(choice => choice.value === data?.status)?.value ?? null);
      setError(null); setMessage(""); setOpen(true);
    }}>변경</button></div>
    <span className="s-profile-status-value" data-status={data?.status ?? "UNSET"}>{data ? labels[data.status] : loading ? "불러오는 중…" : "상태를 확인하지 못했어요"}</span>
    <p className="s-profile-status-note">담당 매니저와 공유되는 현재 상태예요.</p>
    {loadError && <><ActionError message={loadError.message} /><button type="button" className="s-text-link" onClick={reload}>다시 확인</button></>}
    <p role="status" className="s-profile-status-note">{message}</p>
    <BottomSheet open={open} onClose={close} title="현재 상태를 선택해주세요">
      <p className="s-dialog-description">지금 나에게 맞는 상태를 골라주세요. 언제든 바꿀 수 있어요.</p>
      <p className="s-source-note">저장한 상태는 담당 매니저도 확인할 수 있어요. 기존 도움 요청은 그대로 유지돼요.</p>
      <fieldset className="s-profile-status-options" disabled={busy}><legend className="sr-only">현재 상태</legend>{choices.map(choice => <label key={choice.value} className="s-profile-status-option" data-selected={selected === choice.value}>
        <input type="radio" name={radioName} value={choice.value} checked={selected === choice.value} onChange={() => { setSelected(choice.value); setError(null); }} />
        <span><strong>{choice.label}</strong><small>{choice.description}</small></span>
      </label>)}</fieldset>
      <ActionError message={error} />
      <div className="s-cancel-dialog-actions"><button type="button" className="s-button" disabled={busy} onClick={close}>닫기</button><button type="button" className="s-button s-button-primary" disabled={!selected || busy} aria-busy={busy} onClick={save}>{busy ? "저장 중…" : "적용"}</button></div>
    </BottomSheet>
  </section>;
}
