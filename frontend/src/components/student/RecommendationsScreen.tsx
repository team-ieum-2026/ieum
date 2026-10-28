"use client";

import Link from "next/link";
import { useCallback, useState } from "react";
import { getCurrentRecommendations, getStudentName, refreshRecommendations } from "@/lib/api/student";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import StatusChip from "@/components/ui/StatusChip";
import { ApiState, ActionError, StudentScreen } from "./ScreenParts";
import { deadlineLabel, studentHref } from "./navigation";
import { responseLabels } from "./labels";
import { restoreResponse } from "./mockRecommendationResponses";
import { errorMessage, useApi } from "./useApi";

export default function RecommendationsScreen({ studentId }: { studentId: number }) {
  const load = useCallback(async () => {
    const [result, studentName] = await Promise.all([getCurrentRecommendations(studentId), getStudentName(studentId)]);
    return { ...result, studentName, items: result.items.map((item) => ({ ...item, response: restoreResponse(studentId, item.itemId, item.response) })) };
  }, [studentId]);
  const { data, loading, error, reload, setData } = useApi(load);
  const [refreshing, setRefreshing] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  async function refresh() {
    if (!data) return;
    setRefreshing(true); setActionError(null);
    try {
      const result = await refreshRecommendations(studentId);
      setData({ ...result, studentName: data.studentName, items: result.items.map((item) => ({ ...item, response: restoreResponse(studentId, item.itemId, item.response) })) });
    }
    catch (error) { setActionError(errorMessage(error)); }
    finally { setRefreshing(false); }
  }
  return <StudentScreen lighter>
    <header className="px-5 pt-5 pb-3"><span className="text-xl font-bold tracking-tight text-primary">이음</span></header>
    <ApiState loading={loading} error={error} retry={reload} />
    {data && <>
      <div className="space-y-3 px-5 pb-4">
        <h1 className="text-[22px] leading-snug font-bold">{data.items.length ? <><span className="text-[27px]">{data.studentName}님,</span><br />이번 주 맞춤 공고 {data.items.length}개가 도착했어요.</> : "지금은 추천할 공고가 없어요"}</h1>
        <div className="flex items-center justify-between gap-2">
          <p className="text-xs leading-relaxed text-ink-muted">{new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(data.createdAt))} 추천<br />{data.basis === "PROFILE_LATEST" ? "내 프로필 최신 기준" : data.basis}</p>
          <Button variant="secondary" disabled={refreshing} onClick={refresh} className="h-[37px] min-h-[37px]! translate-y-[40px] shrink-0 rounded-full border-primary px-3 py-0! text-xs text-primary">{refreshing ? "추천받는 중…" : "↻ 새로 추천받기"}</Button>
        </div>
        <ActionError message={actionError} />
      </div>
      <div className="space-y-3 px-4 pt-[40px] pb-6" aria-busy={refreshing}>
        {!data.items.length && <Card><p className="text-sm text-ink-sub">관심 직무나 희망 지역이 바뀌었다면 내 프로필에서 수정해 주세요.</p><Link href={studentHref("/student/profile", studentId)} className="mt-3 inline-block text-sm font-bold text-primary">내 프로필 보기 →</Link></Card>}
        {data.items.map((item) => <Link key={item.itemId} href={studentHref(`/student/jobs/${item.itemId}`, studentId)} className="block rounded-2xl focus-visible:outline-2 focus-visible:outline-primary">
          <Card className="space-y-3 hover:border-primary">
            <div className="flex items-center justify-between gap-2"><span className={`inline-flex shrink-0 items-center rounded-md px-2 py-1 text-xs font-bold ${item.response === "APPLY" ? "bg-primary-tint text-primary-strong" : item.response === "PASS" ? "bg-danger-bg text-danger" : "bg-primary-soft text-primary-strong"}`}>{item.response === "APPLY" ? "지원완료" : item.response === "PASS" ? "pass" : `추천 ${item.rank}`}</span><span className={`text-[15px] font-bold ${item.job.dDay !== null && item.job.dDay <= 7 ? "text-warn-ink" : "text-ink-sub"}`}>{deadlineLabel(item.job.dDay)}{item.job.untilFilled && " · 채용 시 마감"}</span></div>
            <div className="space-y-1"><p className="text-[13px] text-ink-sub">{item.job.companyName}</p><h2 className="text-[17px] font-bold">{item.job.title}</h2><p className="text-xs text-ink-sub">{item.job.location} · {item.job.employmentType} · {item.job.career}</p></div>
            <ul className="space-y-1 border-t border-line pt-3 text-[13px] leading-relaxed">{item.reasonSummary.slice(0, 2).map((reason) => <li key={reason} className="flex gap-2"><span aria-hidden="true" className="text-primary">✓</span>{reason}</li>)}</ul>
            {item.response === "HELP" && <StatusChip tone="danger">{responseLabels[item.response]}</StatusChip>}
          </Card>
        </Link>)}
      </div>
    </>}
  </StudentScreen>;
}
