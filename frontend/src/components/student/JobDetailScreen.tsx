"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useState } from "react";
import { createHelpRequest, getRecommendationItem, respondToItem } from "@/lib/api/student";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import StatusChip from "@/components/ui/StatusChip";
import { ActionError, ApiState, ScreenHeader, StudentScreen } from "./ScreenParts";
import { deadlineLabel, studentHref } from "./navigation";
import { responseLabels } from "./labels";
import { rememberResponse, restoreResponse } from "./mockRecommendationResponses";
import { errorMessage, useApi } from "./useApi";

export default function JobDetailScreen({ itemId, studentId }: { itemId: number; studentId: number }) {
  const router = useRouter();
  const load = useCallback(async () => {
    const result = await getRecommendationItem(itemId);
    return { ...result, response: restoreResponse(studentId, itemId, result.response) };
  }, [itemId, studentId]);
  const { data, loading, error, reload } = useApi(load);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  async function respond(action: "APPLY" | "HELP") {
    setBusy(true); setActionError(null);
    try {
      if (action === "APPLY") {
        const result = await respondToItem(itemId, { response: "APPLY" }, studentId);
        if (!result.applicationId) throw new Error("지원서 정보를 받지 못했어요. 다시 확인해 주세요.");
        rememberResponse(studentId, itemId, "APPLY");
        router.push(studentHref(`/student/applications/${result.applicationId}/progress`, studentId));
      } else {
        const result = await createHelpRequest({ studentId, recommendationItemId: itemId });
        router.push(studentHref(`/student/help/${result.helpRequestId}`, studentId));
      }
    } catch (error) { setActionError(errorMessage(error)); setBusy(false); }
  }
  return <StudentScreen>
    <ScreenHeader title="추천 공고" back={studentHref("/student", studentId)} />
    <ApiState loading={loading} error={error} retry={reload} notFoundHref={studentHref("/student", studentId)} notFoundLabel="맞춤 공고로" />
    {data && <>
      <div className="flex-1 space-y-5 px-5 pt-2 pb-6">
        <div className="space-y-2"><p className="text-[13px] text-ink-sub">{data.job.companyName}</p><h1 className="text-[21px] font-bold">{data.job.title}</h1>
          <dl className="space-y-2 border-y border-line py-3 text-[13px]">{[["근무지", data.job.workAddress || data.job.location], ["고용 형태", data.job.employmentType], ["경력 · 학력", [data.job.career, data.job.education].filter(Boolean).join(" · ")], ["급여", data.job.salaryText]].map(([label, value]) => <div key={label} className="flex justify-between gap-4"><dt className="shrink-0 text-ink-sub">{label}</dt><dd className="text-right">{value || "정보 없음"}</dd></div>)}<div className="flex justify-between gap-3"><dt className="text-ink-sub">마감</dt><dd className={`text-right font-bold ${data.job.dDay !== null && data.job.dDay <= 7 ? "text-warn-ink" : "text-ink"}`}>{data.job.deadlineOn ? `${data.job.deadlineOn} (${deadlineLabel(data.job.dDay)})` : deadlineLabel(data.job.dDay)}{data.job.untilFilled && <span className="block">채용 시 마감</span>}</dd></div></dl>
        </div>
        <section className="space-y-2"><h2 className="text-[15px] font-bold">이 공고를 추천한 이유</h2>{data.reasons.map((reason, index) => <Card key={index} className="space-y-2 p-3.5"><p className="text-sm leading-relaxed">{reason.text}</p>{reason.evidence.map((evidence, i) => <details key={i} className="text-xs"><summary className="cursor-pointer font-bold text-primary">근거 · {evidence.label}</summary><blockquote className="mt-2 border-l-2 border-primary-soft pl-3 leading-relaxed text-ink-sub">{evidence.quote}</blockquote></details>)}</Card>)}</section>
        <section className="space-y-2"><h2 className="text-[15px] font-bold">공고 요약</h2><ul className="list-disc space-y-1 pl-5 text-[13px] leading-relaxed">{data.summary.map((text) => <li key={text}>{text}</li>)}</ul>{data.job.description && <details className="text-[13px]"><summary className="cursor-pointer text-ink-sub">공고 내용 더 보기</summary><p className="mt-2 whitespace-pre-line leading-relaxed">{data.job.description}</p></details>}<p className="text-[11px] text-ink-muted">출처 · {data.job.sourceLabel}</p></section>
      </div>
      <div className="space-y-2 border-t border-line bg-surface px-5 pt-4 pb-6">
        <ActionError message={actionError} />
        {data.response !== "NONE" ? <div className="py-3 text-center"><StatusChip>{responseLabels[data.response]}</StatusChip><p className="mt-2 text-xs text-ink-sub">이미 응답한 공고예요.</p></div> : <><Button className="w-full" disabled={busy} onClick={() => respond("APPLY")}>{busy ? "처리 중…" : "지원하기"}</Button><div className="flex gap-2"><Link aria-disabled={busy} tabIndex={busy ? -1 : undefined} href={studentHref(`/student/jobs/${itemId}/pass`, studentId)} className={`flex min-h-12 flex-1 items-center justify-center rounded-xl border border-line text-sm font-bold ${busy ? "pointer-events-none opacity-50" : ""}`}>패스</Link><Button variant="danger" className="flex-1" disabled={busy} onClick={() => respond("HELP")}>도움 요청</Button></div></>}
        <p className="pt-1 text-center text-xs text-ink-muted">어떤 선택이든 따로 설명하지 않아도 괜찮아요</p>
      </div>
    </>}
  </StudentScreen>;
}
