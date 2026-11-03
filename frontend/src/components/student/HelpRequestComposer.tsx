"use client";

import { useCallback, useId, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import BottomSheet from "@/components/ui/BottomSheet";
import { ApiError } from "@/lib/api/client";
import { createHelpRequest, getCurrentRecommendations } from "@/lib/api/student";
import type { HelpType } from "@/types/student";
import { ActionError } from "./ScreenParts";
import { helpTypeLabels, helpTypes } from "./helpRequestLabels";
import { rememberResponse, restoreResponse } from "./mockRecommendationResponses";
import { studentHref } from "./navigation";
import { errorMessage, useApi } from "./useApi";

interface HelpRequestJob { itemId: number; companyName: string; title: string }

/** POST는 유형과 대상을 고른 뒤 제출할 때만 수행한다. M2와 전역 메뉴가 공유한다. */
export default function HelpRequestComposer({ studentId, job, onClose, onConflict }: {
  studentId: number; job?: HelpRequestJob; onClose: () => void; onConflict?: () => void;
}) {
  const router = useRouter();
  const [helpType, setHelpType] = useState<HelpType | null>(null);
  const [itemId, setItemId] = useState<number | null>(job?.itemId ?? null);
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const [error, setError] = useState<string | null>(null);
  const [conflictId, setConflictId] = useState<number | null>(null);
  const radioName = useId();
  const close = () => { if (!busyRef.current) onClose(); };

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!helpType || busyRef.current || (itemId !== null && conflictId === itemId)) return;
    busyRef.current = true; setBusy(true); setError(null);
    try {
      const result = await createHelpRequest({ studentId, recommendationItemId: itemId, helpType });
      if (itemId !== null) rememberResponse(studentId, itemId, "HELP");
      onClose();
      router.push(studentHref(`/student/help/${result.helpRequestId}`, studentId));
    } catch (failure) {
      setError(errorMessage(failure));
      if (failure instanceof ApiError && failure.status === 409) {
        setConflictId(itemId);
        onConflict?.();
      }
      busyRef.current = false; setBusy(false);
    }
  }

  return <BottomSheet open title="어떤 도움이 필요하세요?" onClose={close}>
    <form className="s-help-composer" onSubmit={submit}>
      <p className="s-dialog-description">도움 유형을 골라주세요. 담당 매니저가 함께 살펴볼게요.</p>
      <fieldset className="s-help-type-options" disabled={busy}>
        <legend>도움 유형 <span>(필수)</span></legend>
        {helpTypes.map(type => <label key={type} data-selected={helpType === type}>
          <input type="radio" name={radioName} value={type} checked={helpType === type} onChange={() => { setHelpType(type); setError(null); }} required />
          <span>{helpTypeLabels[type]}</span>
        </label>)}
      </fieldset>
      {job ? <div className="s-help-job-context"><span>함께 살펴볼 공고</span><strong>{job.companyName}</strong><p>{job.title}</p></div>
        : <HelpRequestJobChoices studentId={studentId} selected={itemId} disabled={busy} conflictId={conflictId} onSelect={id => { setItemId(id); setError(null); }} />}
      <ActionError message={error} />
      {conflictId !== null && itemId === conflictId && <p role="status" className="s-source-note">이미 응답한 공고예요. 창을 닫고 공고 상태를 다시 확인하거나 다른 대상을 골라주세요.</p>}
      <div className="s-cancel-dialog-actions"><button type="button" className="s-button" disabled={busy} onClick={close}>취소</button><button type="submit" className="s-button s-button-primary" disabled={!helpType || busy || (itemId !== null && conflictId === itemId)} aria-busy={busy}>{busy ? "요청 중…" : "도움 요청 보내기"}</button></div>
    </form>
  </BottomSheet>;
}

function HelpRequestJobChoices({ studentId, selected, disabled, conflictId, onSelect }: {
  studentId: number; selected: number | null; disabled: boolean; conflictId: number | null; onSelect: (itemId: number | null) => void;
}) {
  const load = useCallback(() => getCurrentRecommendations(studentId), [studentId]);
  const { data, loading, error, reload } = useApi(load);
  const radioName = useId();
  const choices = data?.items.filter(item => item.itemId !== conflictId && restoreResponse(studentId, item.itemId, item.response) === "NONE") ?? [];
  return <fieldset className="s-help-target-options" disabled={disabled}>
    <legend>관련 공고</legend>
    <label data-selected={selected === null}><input type="radio" name={radioName} value="" checked={selected === null} onChange={() => onSelect(null)} /><span><strong>공고 없이 도움 요청</strong><small>공고가 없어도 필요한 도움을 요청할 수 있어요.</small></span></label>
    {error ? <div role="alert" className="s-help-load-error"><p>{error.message}</p><button type="button" className="s-text-link" onClick={reload}>공고 다시 불러오기</button></div> : loading && <p role="status" className="s-source-note">공고를 불러오고 있어요…</p>}
    {choices.map(item => <label key={item.itemId} data-selected={selected === item.itemId}><input type="radio" name={radioName} value={item.itemId} checked={selected === item.itemId} onChange={() => onSelect(item.itemId)} /><span><strong>{item.job.companyName}</strong><small>{item.job.title}</small></span></label>)}
    {data && !choices.length && <p className="s-source-note">선택할 미응답 공고가 없어요. 공고 없이 도움을 요청할 수 있어요.</p>}
  </fieldset>;
}
