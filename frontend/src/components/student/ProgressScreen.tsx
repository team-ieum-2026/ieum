"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getApplicationProgress } from "@/lib/api/student";
import type { ApplicationProgress } from "@/types/student";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import { ActionError, StudentScreen } from "./ScreenParts";
import { studentHref } from "./navigation";
import { errorMessage } from "./useApi";

const labels = { COMPANY_INFO: "기업 정보 확인", COVER_LETTER: "자기소개서 다시 쓰기", RESUME: "이력서 정리" };
const stateLabels = { WAITING: "대기 중", RUNNING: "진행 중", DONE: "완료", FAILED: "실패" };

export default function ProgressScreen({ applicationId, studentId }: { applicationId: number; studentId: number }) {
  const router = useRouter();
  const [data, setData] = useState<ApplicationProgress | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    let timer: ReturnType<typeof setTimeout>;
    async function poll() {
      try {
        const next = await getApplicationProgress(applicationId);
        if (!active) return;
        setData(next); setError(null);
        if (next.status === "DRAFT") {
          router.replace(studentHref(`/student/applications/${applicationId}`, studentId));
        } else if (next.status === "GENERATING") {
          timer = setTimeout(poll, 2000);
        }
      } catch (error) { if (active) setError(errorMessage(error)); }
    }
    void poll();
    return () => { active = false; clearTimeout(timer); };
  }, [applicationId, studentId, router, attempt]);
  const steps = data?.steps ?? (["COMPANY_INFO", "COVER_LETTER", "RESUME"] as const).map((key) => ({ key, state: "WAITING" as const, note: null }));
  const done = steps.filter((step) => step.state === "DONE").length;
  const failed = data?.status === "FAILED";
  return <StudentScreen><div className="flex min-h-dvh flex-col gap-7 px-5 pt-[72px] pb-7">
    <div className="space-y-3"><h1 className="text-[22px] leading-snug font-bold">{failed ? <>초안 생성이 잠시 멈췄어요</> : <>지원 서류 초안을<br />만들고 있어요</>}</h1><progress aria-label="서류 생성 진행률" value={done} max={3} className="h-2 w-full overflow-hidden rounded-full [&::-webkit-progress-bar]:bg-line [&::-webkit-progress-value]:bg-primary [&::-moz-progress-bar]:bg-primary" /></div>
    <Card className="space-y-5 p-5"><div className="sr-only" role="status">{done} / 3 단계 완료</div>{steps.map((step) => <div key={step.key} className="flex gap-3.5"><div aria-hidden="true" className={`flex size-7 shrink-0 items-center justify-center rounded-full border-2 ${step.state === "DONE" ? "border-primary bg-primary text-surface" : step.state === "RUNNING" ? "animate-pulse border-primary bg-primary-tint" : step.state === "FAILED" ? "border-danger text-danger" : "border-line"}`}>{step.state === "DONE" ? "✓" : step.state === "FAILED" ? "!" : ""}</div><div><h2 className="text-[15px] font-bold">{labels[step.key]} <span className="text-xs font-normal text-ink-sub">{stateLabels[step.state]}</span></h2><p className="mt-1 text-[13px] leading-relaxed text-ink-sub">{step.note ?? (step.state === "WAITING" ? "앞 단계가 끝나면 시작해요" : step.state === "DONE" ? "정리를 마쳤어요" : "등록한 자료를 공고에 맞게 정리하고 있어요")}</p></div></div>)}</Card>
    <p className="rounded-xl bg-primary-tint px-3.5 py-3 text-[13px] leading-relaxed text-ink-sub">모든 문장은 등록한 자료나 기업 공식 정보에 근거해서 써요. 근거가 없는 내용은 쓰지 않아요.</p>
    <ActionError message={error} />
    {(failed || error) && <div className="space-y-3"><Button className="w-full" variant="secondary" onClick={() => { setError(null); setData(null); setAttempt((value) => value + 1); }}>다시 시도</Button><p className="text-xs text-ink-muted">생성 상태를 다시 확인합니다. 계속 실패하면 담당 코디네이터에게 문의해 주세요.</p></div>}
  </div></StudentScreen>;
}
