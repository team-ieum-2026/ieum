"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { getApplicationProgress, regenerateApplication } from "@/lib/api/student";
import type { ApplicationProgress } from "@/types/student";
import Button from "@/components/ui/Button";
import { ActionError, StudentScreen } from "./ScreenParts";
import { studentHref } from "./navigation";
import { errorMessage } from "./useApi";
import StudentPageHeading from "./StudentPageHeading";
import ApplicationLoading from "./ApplicationLoading";
import { clearApplicationLoadingStart, rememberApplicationLoadingStart, remainingApplicationLoadingMs } from "./applicationLoadingDelay";

const labels = { COMPANY_INFO: "기업 정보 확인", COVER_LETTER: "자기소개서 다시 쓰기", RESUME: "이력서 정리" };
const stateLabels = { WAITING: "대기 중", RUNNING: "진행 중", DONE: "완료", FAILED: "실패" };
/** 생성 완료(DRAFT) 뒤 자기소개서 → 이력서 순서로 체크를 보여 주는 간격 */
const FINISH_STEP_MS = 700;
/** 생성 중 막대: 기업 정보 완료(1/3)에서 시작해 시간에 따라 천천히 올라가고 85%를 넘지 않는다 */
const CREEP_FROM = 100 / 3;
const CREEP_TO = 85;
const CREEP_TIME_MS = 15000;

export default function ProgressScreen({ applicationId, studentId }: { applicationId: number; studentId: number }) {
  return <ApplicationProgressScreen key={`${studentId}:${applicationId}`} applicationId={applicationId} studentId={studentId} />;
}

function ApplicationProgressScreen({ applicationId, studentId }: { applicationId: number; studentId: number }) {
  const router = useRouter();
  const [data, setData] = useState<ApplicationProgress | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [retrying, setRetrying] = useState(false);
  // 생성 중 막대가 올라간 정도(0~1)와, 생성 완료 뒤 차례로 체크한 단계 수(1 = 자기소개서, 2 = 이력서)
  const [creep, setCreep] = useState(0);
  const [finished, setFinished] = useState(0);
  const busyRef = useRef(false);
  const activeRef = useRef(false);
  useEffect(() => {
    activeRef.current = true;
    let active = true;
    const enteredAt = Date.now();
    const timers: ReturnType<typeof setTimeout>[] = [];
    async function poll() {
      try {
        const next = await getApplicationProgress(applicationId);
        if (!active) return;
        setData(next); setError(null);
        if (next.status === "DRAFT") {
          // AI 생성은 한 번의 호출이라 단계별 완료 시각이 없다. 완료되면 자기소개서 → 이력서 순서로 체크해 보여 준 뒤 이동한다
          setFinished(1);
          timers.push(setTimeout(() => { if (active) setFinished(2); }, FINISH_STEP_MS));
          timers.push(setTimeout(() => {
            if (!active) return;
            clearApplicationLoadingStart(applicationId);
            router.replace(studentHref(`/student/applications/${applicationId}`, studentId));
          }, Math.max(remainingApplicationLoadingMs(applicationId, enteredAt), FINISH_STEP_MS * 2)));
        } else if (next.status === "GENERATING") {
          timers.push(setTimeout(poll, 2000));
        } else if (next.status === "FAILED") {
          clearApplicationLoadingStart(applicationId);
        }
      } catch (error) { if (active) setError(errorMessage(error)); }
    }
    void poll();
    return () => { active = false; activeRef.current = false; timers.forEach(clearTimeout); };
  }, [applicationId, studentId, router, attempt]);
  const generating = data?.status === "GENERATING";
  useEffect(() => {
    if (!generating) return;
    const startedAt = Date.now();
    const id = setInterval(() => setCreep(1 - Math.exp(-(Date.now() - startedAt) / CREEP_TIME_MS)), 400);
    return () => clearInterval(id);
  }, [generating]);
  const serverSteps = data?.steps ?? (["COMPANY_INFO", "COVER_LETTER", "RESUME"] as const).map((key) => ({ key, state: "WAITING" as const, note: null }));
  // DRAFT 직후에는 서버가 모두 DONE 으로 주지만, 화면은 자기소개서 → 이력서 순서로 체크한다
  const steps = data?.status === "DRAFT"
    ? serverSteps.map((step) => step.key === "COVER_LETTER"
      ? { ...step, state: finished >= 1 ? "DONE" as const : "RUNNING" as const }
      : step.key === "RESUME" ? { ...step, state: finished >= 2 ? "DONE" as const : finished >= 1 ? "RUNNING" as const : "WAITING" as const } : step)
    : serverSteps;
  const done = steps.filter((step) => step.state === "DONE").length;
  const failed = data?.status === "FAILED";
  const creepPercent = CREEP_FROM + (CREEP_TO - CREEP_FROM) * creep;
  const percent = !data ? 0
    : generating ? creepPercent
    : data.status === "DRAFT" ? (finished >= 2 ? 100 : Math.max(200 / 3, creepPercent))
    : failed ? (done * 100) / 3
    : 100;

  async function retry() {
    if (busyRef.current) return;
    if (!failed) {
      setError(null);
      setAttempt(value => value + 1);
      return;
    }
    busyRef.current = true;
    setRetrying(true);
    setError(null);
    try {
      await regenerateApplication(applicationId);
      if (!activeRef.current) return;
      rememberApplicationLoadingStart(applicationId, Date.now());
      setData(null);
      setCreep(0);
      setAttempt(value => value + 1);
    } catch (failure) {
      if (activeRef.current) setError(errorMessage(failure));
    } finally {
      busyRef.current = false;
      if (activeRef.current) setRetrying(false);
    }
  }
  return <StudentScreen><div className="s-progress">
    {(failed || error) && <StudentPageHeading title={failed ? "초안 생성이 잠시 멈췄어요." : "서류 생성 상태를 확인하지 못했어요."} description="잠시 후 다시 시도해주세요." />}
    <div className="s-progress-frame">
    {!failed && !error && <ApplicationLoading />}
    <div className="s-panel s-progress-card"><div className="s-progress-intro"><div role="progressbar" aria-label="서류 생성 진행률" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(percent)} className="h-2 w-full overflow-hidden rounded-full bg-line"><div className="h-full rounded-full bg-primary transition-[width] duration-500 ease-out motion-reduce:transition-none" style={{ width: `${percent}%` }} /></div></div>
    <div className="s-progress-steps"><div className="sr-only" role="status">{done} / 3 단계 완료</div>{steps.map((step) => <div key={step.key} className="flex gap-3.5"><div aria-hidden="true" className={`flex size-7 shrink-0 items-center justify-center rounded-full border-2 ${step.state === "DONE" ? "border-primary bg-primary text-surface" : step.state === "RUNNING" ? "animate-pulse border-primary bg-primary-tint" : step.state === "FAILED" ? "border-danger text-danger" : "border-line"}`}>{step.state === "DONE" ? "✓" : step.state === "FAILED" ? "!" : ""}</div><div><h2 className="text-[15px] font-bold">{labels[step.key]} <span className="text-xs font-normal text-ink-sub">{stateLabels[step.state]}</span></h2><p className="mt-1 text-[13px] leading-relaxed text-ink-sub">{step.note ?? (step.state === "WAITING" ? "앞 단계가 끝나면 시작해요" : step.state === "DONE" ? "정리를 마쳤어요" : "등록한 자료를 공고에 맞게 정리하고 있어요")}</p></div></div>)}</div></div>
    </div>
    <ActionError message={error} />
    {(failed || error) && <div className="space-y-3"><Button className="w-full" variant="secondary" onClick={retry} disabled={retrying} aria-busy={retrying}>{retrying ? "생성 시작 중…" : "다시 시도"}</Button><p className="text-xs text-ink-muted">{failed ? "실패한 서류를 다시 만들어요. 계속 실패하면 담당 매니저에게 문의해 주세요." : "생성 상태를 다시 확인해요. 계속 실패하면 담당 매니저에게 문의해 주세요."}</p></div>}
  </div></StudentScreen>;
}
