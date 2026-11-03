"use client";

import { useCallback, useEffect, useId, useRef, useState, type FormEvent } from "react";
import BottomSheet from "@/components/ui/BottomSheet";
import { ApiError } from "@/lib/api/client";
import { closeHelpRequest, getStudentHelpRequests, subscribeHelpRequestChanges } from "@/lib/api/student";
import { ACTIVE_HELP_STATUSES, type HelpRequestCloseReason, type HelpRequestCloseResult, type StudentHelpRequest, type StudentHelpRequestFilter } from "@/types/student";
import { helpCloseReasonLabels, helpCloseReasons, helpTypeLabels, studentHelpStatusLabels } from "./helpRequestLabels";
import { ActionError, ApiState } from "./ScreenParts";
import { errorMessage, useApi } from "./useApi";

const date = (value: string) => new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(value));
const dateTime = (value: string) => new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", month: "long", day: "numeric", weekday: "short", hour: "2-digit", minute: "2-digit" }).format(new Date(value));
/** 연락 완료 전 진행 중 요청 (접수 · 담당자 확인 중 · 상담 예정). 이 요청만 직접 종료할 수 있다 */
const isActive = (item: StudentHelpRequest) => ACTIVE_HELP_STATUSES.includes(item.status);

export default function StudentHelpRequests({ studentId }: { studentId: number }) {
  const [filter, setFilter] = useState<StudentHelpRequestFilter>("OPEN");
  // "진행 중"은 서버 상태 하나가 아니라 접수 · 처리 중 · 상담 예정을 함께 보여 준다 (전체를 받아 거른다)
  const load = useCallback(async () => filter === "OPEN"
    ? (await getStudentHelpRequests(studentId, "ALL")).filter(isActive)
    : getStudentHelpRequests(studentId, filter), [studentId, filter]);
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
    if (data) setData(data.map(item => item.helpRequestId === result.helpRequestId ? { ...item, ...result } : item).filter(item => filter !== "OPEN" || isActive(item)));
    setSelected(null);
    setMessage("선택한 도움 요청을 종료했어요. 현재 상태와 다른 요청은 유지돼요.");
    reload();
  }

  return <section className="s-my-help" aria-labelledby={headingId}>
    <div className="s-my-help-heading"><h2 id={headingId}>내 도움 요청</h2>
    <div className="s-my-help-filters" aria-label="도움 요청 조회 범위">{(["OPEN", "ALL"] as const).map(value => <button type="button" key={value} aria-pressed={filter === value} onClick={() => { setFilter(value); setSelected(null); setMessage(""); }}>{value === "OPEN" ? "진행 중" : "전체 내역"}</button>)}</div>
    </div>
    <div className="s-panel s-my-help-content">
    <ApiState loading={loading} error={error} retry={reload} />
    <p className="s-my-help-message" role="status">{message}</p>
    {data && data.length > 0 && <ul className="s-my-help-list">{data.map(item => <li key={item.helpRequestId} data-help-request-id={item.helpRequestId}>
      <div className="s-my-help-meta"><span className="s-help-state" data-status={item.status}>{studentHelpStatusLabels[item.status]}</span><span>{item.helpType === null ? "미분류" : helpTypeLabels[item.helpType]}</span><time dateTime={item.createdAt}>{date(item.createdAt)} 요청</time></div>
      <h3>{item.jobTitle ?? "일반 도움 요청"}</h3>
      {item.memo && <p className="s-my-help-memo">{item.memo}</p>}
      <div className="s-my-help-bottom">{item.status === "CLOSED" ? <p>{item.closedAt && `${date(item.closedAt)} 종료`}{item.closeReason && ` · ${helpCloseReasonLabels[item.closeReason]}`}</p> : item.status === "CONSULTATION_SCHEDULED" && item.consultationAt ? <p>상담 예정 · <time dateTime={item.consultationAt}>{dateTime(item.consultationAt)}</time></p> : <p>{item.contactMethod ? `선호 연락 · ${{ SMS: "문자", PHONE: "전화", EMAIL: "이메일" }[item.contactMethod]}` : "담당 매니저와 함께 확인해요."}</p>}
        {isActive(item) && <button type="button" className="s-text-link" onClick={() => { setSelected(item); setMessage(""); }}>요청 종료</button>}
      </div>
    </li>)}</ul>}
    {data && !data.length && !error && !loading && <div className="s-my-help-empty"><h3>{filter === "OPEN" ? "진행 중인 도움 요청이 없어요." : "아직 보낸 도움 요청이 없어요."}</h3><p>도움이 필요하면 상단의 ‘도움 요청’을 눌러주세요.</p></div>}
    </div>
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
