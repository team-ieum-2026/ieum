"use client";

import { useCallback, useEffect, useId, useRef, useState, type FormEvent } from "react";
import BottomSheet from "@/components/ui/BottomSheet";
import { ApiError } from "@/lib/api/client";
import { closeHelpRequest, getStudentHelpRequests, subscribeHelpRequestChanges } from "@/lib/api/student";
import type { HelpRequestCloseReason, HelpRequestCloseResult, StudentHelpRequest, StudentHelpRequestFilter } from "@/types/student";
import { helpCloseReasonLabels, helpCloseReasons, helpTypeLabels, studentHelpStatusLabels } from "./helpRequestLabels";
import { ActionError, ApiState } from "./ScreenParts";
import { errorMessage, useApi } from "./useApi";

const date = (value: string) => new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(value));

export default function StudentHelpRequests({ studentId }: { studentId: number }) {
  const [filter, setFilter] = useState<StudentHelpRequestFilter>("OPEN");
  const load = useCallback(() => getStudentHelpRequests(studentId, filter), [studentId, filter]);
  const { data, loading, error, reload, setData } = useApi(load);
  const [selected, setSelected] = useState<StudentHelpRequest | null>(null);
  const [message, setMessage] = useState("");
  const headingId = useId();

  useEffect(() => {
    window.addEventListener("focus", reload);
    const unsubscribe = subscribeHelpRequestChanges(reload);
    return () => { window.removeEventListener("focus", reload); unsubscribe(); };
  }, [reload]);

  function closed(result: HelpRequestCloseResult) {
    if (data) setData(data.map(item => item.helpRequestId === result.helpRequestId ? { ...item, ...result } : item).filter(item => filter !== "OPEN" || item.status === "OPEN"));
    setSelected(null);
    setMessage("선택한 도움 요청을 종료했어요. 현재 상태와 다른 요청은 유지돼요.");
    reload();
  }

  return <section className="s-panel s-my-help" aria-labelledby={headingId}>
    <div className="s-my-help-heading"><div><h2 id={headingId}>내 도움 요청</h2><p>담당자에게 보낸 요청을 확인하고, 필요한 요청만 유지할 수 있어요.</p></div></div>
    <div className="s-my-help-filters" aria-label="도움 요청 조회 범위">{(["OPEN", "ALL"] as const).map(value => <button type="button" key={value} aria-pressed={filter === value} onClick={() => { setFilter(value); setSelected(null); setMessage(""); }}>{value === "OPEN" ? "접수 중" : "전체 내역"}</button>)}</div>
    <ApiState loading={loading} error={error} retry={reload} />
    <p className="s-my-help-message" role="status">{message}</p>
    {data && data.length > 0 && <ul className="s-my-help-list">{data.map(item => <li key={item.helpRequestId} data-help-request-id={item.helpRequestId}>
      <div className="s-my-help-meta"><span className="s-help-state" data-status={item.status}>{studentHelpStatusLabels[item.status]}</span><span>{item.helpType === null ? "미분류" : helpTypeLabels[item.helpType]}</span><time dateTime={item.createdAt}>{date(item.createdAt)} 요청</time></div>
      <h3>{item.jobTitle ?? "일반 도움 요청"}</h3>
      {item.memo && <p className="s-my-help-memo">{item.memo}</p>}
      <div className="s-my-help-bottom">{item.status === "CLOSED" ? <p>{item.closedAt && `${date(item.closedAt)} 종료`}{item.closeReason && ` · ${helpCloseReasonLabels[item.closeReason]}`}</p> : <p>{item.contactMethod ? `선호 연락 · ${{ SMS: "문자", PHONE: "전화", EMAIL: "이메일" }[item.contactMethod]}` : "담당 코디네이터와 함께 확인해요."}</p>}
        {item.status === "OPEN" && <button type="button" className="s-text-link" onClick={() => { setSelected(item); setMessage(""); }}>요청 종료</button>}
      </div>
    </li>)}</ul>}
    {data && !data.length && !error && !loading && <div className="s-my-help-empty"><h3>{filter === "OPEN" ? "접수 중인 도움 요청이 없어요." : "아직 보낸 도움 요청이 없어요."}</h3><p>도움이 필요하면 상단의 ‘도움 요청’을 눌러주세요.</p></div>}
    {selected && <CloseHelpRequestDialog key={selected.helpRequestId} studentId={studentId} request={selected} onClose={() => setSelected(null)} onClosed={closed} onConflict={reload} />}
  </section>;
}

function CloseHelpRequestDialog({ studentId, request, onClose, onClosed, onConflict }: {
  studentId: number; request: StudentHelpRequest; onClose: () => void; onClosed: (result: HelpRequestCloseResult) => void; onConflict: () => void;
}) {
  const [reason, setReason] = useState<HelpRequestCloseReason | null>(null);
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);
  const radioName = useId();
  const close = () => { if (!busyRef.current) onClose(); };

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!reason || busyRef.current || conflict) return;
    busyRef.current = true; setBusy(true); setError(null);
    try { onClosed(await closeHelpRequest(request.helpRequestId, { studentId, closeReason: reason })); }
    catch (failure) {
      if (failure instanceof ApiError && failure.status === 409) {
        setConflict(true);
        setError("요청 상태가 바뀌었어요. 최신 목록을 다시 확인해주세요.");
        onConflict();
      } else setError(errorMessage(failure));
    } finally { busyRef.current = false; setBusy(false); }
  }

  return <BottomSheet open title="도움 요청을 종료할까요?" onClose={close}>
    <form className="s-help-close-form" onSubmit={submit}>
      <p className="s-dialog-description">{request.jobTitle ?? "일반 도움 요청"}</p>
      <p className="s-source-note">선택한 요청 한 건만 종료해요. 공고 응답과 현재 상태는 바뀌지 않아요.</p>
      <fieldset className="s-profile-status-options" disabled={busy || conflict}><legend>종료 사유</legend>{helpCloseReasons.map(value => <label key={value} className="s-profile-status-option" data-selected={reason === value}><input type="radio" name={radioName} value={value} checked={reason === value} required onChange={() => { setReason(value); setError(null); }} /><span>{helpCloseReasonLabels[value]}</span></label>)}</fieldset>
      <ActionError message={error} />
      <div className="s-cancel-dialog-actions"><button type="button" className="s-button" disabled={busy} onClick={close}>유지하기</button><button type="submit" className="s-button s-button-primary" disabled={!reason || busy || conflict} aria-busy={busy}>{busy ? "종료 중…" : "요청 종료"}</button></div>
    </form>
  </BottomSheet>;
}
