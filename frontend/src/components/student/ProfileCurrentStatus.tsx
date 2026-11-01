"use client";

import { useEffect, useId, useState } from "react";
import BottomSheet from "@/components/ui/BottomSheet";
import { ActionError } from "./ScreenParts";

const choices = [
  { value: "SEEKING", label: "구직 중", description: "나에게 맞는 기회를 찾고 있어요." },
  { value: "EMPLOYED", label: "취업 완료", description: "새로운 직장에서 일을 시작했어요." },
  { value: "PREPARING", label: "취업 준비 중", description: "학습과 경험을 쌓으며 준비하고 있어요." },
  { value: "TAKING_BREAK", label: "잠시 쉬는 중", description: "쉬어가며 다음 걸음을 생각하고 있어요." },
] as const;
type CurrentStatus = (typeof choices)[number]["value"];
const keyFor = (studentId: number) => `ieum:profile-status-preview:v1:${studentId}`;
const validStatus = (value: unknown): value is CurrentStatus => choices.some(choice => choice.value === value);

/** 프로필 표시용 브라우저 설정. 서버의 자동 판정 StudentStatus를 변경하지 않는다. */
export default function ProfileCurrentStatus({ studentId }: { studentId: number }) {
  const [saved, setSaved] = useState<CurrentStatus | null>(null);
  const [selected, setSelected] = useState<CurrentStatus | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState("");
  const headingId = useId();
  const radioName = useId();

  useEffect(() => {
    function restore() {
      try {
        const raw = localStorage.getItem(keyFor(studentId));
        const value: unknown = raw ? JSON.parse(raw) : null;
        setSaved(validStatus(value) ? value : null);
      } catch { setSaved(null); }
      setLoaded(true);
    }
    restore();
    const onStorage = (event: StorageEvent) => {
      if (event.key === keyFor(studentId) || event.key === null) restore();
    };
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, [studentId]);

  const status = choices.find(choice => choice.value === saved);
  function save() {
    if (!selected) return;
    try {
      localStorage.setItem(keyFor(studentId), JSON.stringify(selected));
      setSaved(selected); setOpen(false); setError(null);
      setMessage("이 브라우저에 상태 표시를 저장했어요.");
    } catch { setError("이 브라우저에 저장하지 못했어요. 저장 권한을 확인하고 다시 시도해주세요."); }
  }

  return <section className="s-profile-current-status" aria-labelledby={headingId}>
    <div className="s-profile-status-heading"><h3 id={headingId}>현재 상태</h3><button type="button" disabled={!loaded} onClick={() => { setSelected(saved); setError(null); setMessage(""); setOpen(true); }}>{saved ? "변경" : "선택하기"}</button></div>
    <span className="s-profile-status-value" data-status={saved ?? "UNSET"}>{!loaded ? "불러오는 중…" : status?.label ?? "상태를 선택해주세요"}</span>
    <p className="s-profile-status-note">미리보기 · 이 브라우저에만 저장돼요.</p>
    <p role="status" className="s-profile-status-note">{message}</p>
    <BottomSheet open={open} onClose={() => setOpen(false)} title="현재 상태를 선택해주세요">
      <p className="s-dialog-description">지금 나에게 맞는 상태를 골라주세요. 언제든 바꿀 수 있어요.</p>
      <p className="s-source-note">선택한 상태는 이 브라우저의 프로필에만 표시돼요. 담당자에게 공유되거나 추천·지원 상태에 반영되지는 않아요.</p>
      <fieldset className="s-profile-status-options"><legend className="sr-only">현재 상태</legend>{choices.map(choice => <label key={choice.value} className="s-profile-status-option" data-selected={selected === choice.value}>
        <input type="radio" name={radioName} value={choice.value} checked={selected === choice.value} onChange={() => { setSelected(choice.value); setError(null); }} />
        <span><strong>{choice.label}</strong><small>{choice.description}</small></span>
      </label>)}</fieldset>
      <ActionError message={error} />
      <div className="s-cancel-dialog-actions"><button type="button" className="s-button" onClick={() => setOpen(false)}>닫기</button><button type="button" className="s-button s-button-primary" disabled={!selected} onClick={save}>적용</button></div>
    </BottomSheet>
  </section>;
}
