"use client";

import Link from "next/link";
import { useCallback, useEffect } from "react";
import { getStudentApplications, subscribeApplicationChanges } from "@/lib/api/student";
import Card from "@/components/ui/Card";
import StatusChip from "@/components/ui/StatusChip";
import { applicationLabels, passLabels, responseLabels } from "./labels";
import { dateLabel, studentHref } from "./navigation";
import { ApiState, StudentScreen } from "./ScreenParts";
import { useApi } from "./useApi";

export default function ApplicationsScreen({ studentId }: { studentId: number }) {
  const load = useCallback(() => getStudentApplications(studentId), [studentId]);
  // 다른 기기에서 코디네이터가 승인해도 서버 상태를 다시 조회해 반영한다.
  const { data, loading, error, reload } = useApi(load, {
    refreshIntervalMs: process.env.NEXT_PUBLIC_USE_MOCK === "false" ? 5000 : 0,
  });
  useEffect(() => subscribeApplicationChanges(reload), [reload]);
  return <StudentScreen>
    <h1 className="px-5 pt-6 pb-4 text-[22px] font-bold">지원 현황</h1>
    <ApiState loading={loading} error={error} retry={reload} />
    {data && <div className="space-y-3 px-4 pb-6">
      {!data.applications.length && <p className="text-sm text-ink-sub">아직 지원한 공고가 없어요.</p>}
      {data.applications.map((application) => {
        const stage = application.status === "DELIVERED" ? 3 : application.status === "APPROVED" ? 2 : ["SUBMITTED", "REJECTED"].includes(application.status) ? 1 : 0;
        return <Card key={application.applicationId} className="space-y-5 p-[18px]">
          <div className="flex items-start justify-between gap-2"><div><p className="text-[13px] text-ink-sub">{application.companyName}</p><h2 className="mt-1 text-[17px] font-bold">{application.title}</h2></div><StatusChip tone={application.status === "REJECTED" || application.status === "FAILED" ? "danger" : "success"}>{applicationLabels[application.status]}</StatusChip></div>
          {stage > 0 ? <ol className="space-y-4">{["서류 제출", "담당 코디네이터 검토", "기관 추천으로 기업에 전달"].map((label, i) => <li key={label} className="flex gap-3"><span aria-hidden="true" className={`flex size-6 shrink-0 items-center justify-center rounded-full border-2 text-xs ${i < stage ? "border-primary bg-primary text-surface" : i === stage ? "border-primary bg-primary-tint" : "border-line"}`}>{i < stage ? "✓" : ""}</span><div><h3 className="text-sm font-bold">{label}</h3><p className="mt-1 text-xs text-ink-sub">{i === 0 ? `${dateLabel(application.submittedAt)} · 문장 ${application.userEditedCount}개 직접 수정` : i === 1 ? application.status === "REJECTED" ? "담당 코디네이터가 보완 사항을 안내해 드려요" : stage >= 2 ? "검토를 마쳤어요" : "확인 중이에요" : stage === 3 ? "기업에 전달했어요" : "기업에 전달할 예정이에요"}</p></div></li>)}</ol> : <Link href={studentHref(`/student/applications/${application.applicationId}${application.status === "GENERATING" || application.status === "FAILED" ? "/progress" : ""}`, studentId)} className="inline-block text-sm font-bold text-primary">{application.status === "DRAFT" ? "초안 이어서 보기" : "생성 상태 확인"} →</Link>}
        </Card>;
      })}
      {data.otherItems.length > 0 && <section className="space-y-3 pt-3" aria-label="이번 주 다른 공고">
        <h2 className="text-sm font-bold text-ink-sub">이번 주 다른 공고</h2>
        {data.otherItems.map((item) => <Card key={item.itemId} className="space-y-3">
          <div><p className="text-[13px] text-ink-sub">{item.companyName}</p><h3 className="mt-1 text-sm font-bold">{item.title}</h3></div>
          {item.response === "NONE"
            ? <Link href={studentHref(`/student/jobs/${item.itemId}`, studentId)} className="inline-flex min-h-10 items-center rounded-lg border border-primary px-3 py-2 text-xs font-bold text-primary">답하기</Link>
            : <p className={`text-xs font-bold ${item.response === "PASS" ? "text-danger" : "text-ink-sub"}`}>{responseLabels[item.response]}{item.response === "PASS" && item.passReason && ` · ${passLabels[item.passReason]}`}</p>}
        </Card>)}
      </section>}
    </div>}
  </StudentScreen>;
}
