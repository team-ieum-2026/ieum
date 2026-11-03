"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { getStudentApplications, subscribeApplicationChanges } from "@/lib/api/student";
import type { ApplicationStatus } from "@/types/student";
import { applicationLabels, passLabels, responseLabels } from "./labels";
import { dateLabel, studentHref } from "./navigation";
import { ApiState, StudentScreen } from "./ScreenParts";
import { useApi } from "./useApi";
import ApplicationStatusBadge from "./ApplicationStatusBadge";
import StudentPageHeading from "./StudentPageHeading";
import StudentIcon from "./StudentIcon";

export default function ApplicationsScreen({ studentId }: { studentId: number }) {
  const load = useCallback(() => getStudentApplications(studentId), [studentId]);
  const { data, loading, error, reload } = useApi(load, { refreshIntervalMs: process.env.NEXT_PUBLIC_USE_MOCK === "false" ? 5000 : 0 });
  useEffect(() => subscribeApplicationChanges(reload), [reload]);
  const [filter, setFilter] = useState<ApplicationStatus | "ALL">("ALL");
  const applications = data?.applications ?? [];
  const visible = applications.filter(application => filter === "ALL" || application.status === filter);
  const counts = [applications.filter(a => ["GENERATING", "DRAFT", "FAILED", "REJECTED"].includes(a.status)).length, applications.filter(a => ["SUBMITTED", "APPROVED"].includes(a.status)).length, applications.filter(a => a.status === "DELIVERED").length];
  return <StudentScreen>
    <StudentPageHeading title="한 걸음씩, 나의 지원 여정." description="서류 준비부터 기관 추천 전달까지 진행 상황을 확인해요." />
    <ApiState loading={loading} error={error} retry={reload} />
    {data && <>
      <div className="s-summary-grid">{["서류 준비·보완", "담당자 검토·승인", "기관 추천 전달"].map((label, i) => <section className="s-summary-card" key={label}><h2>{label}</h2><strong>{counts[i]}<small>건</small></strong></section>)}</div>
      <div className="s-section-heading"><div className="s-section-title"><h2>나의 지원서</h2><span className="s-count">{applications.length}</span></div></div>
      <div className="s-status-filters" aria-label="지원 상태 필터"><button type="button" className="s-status-filter" aria-pressed={filter === "ALL"} onClick={() => setFilter("ALL")}>전체<span>{applications.length}</span></button>{(Object.keys(applicationLabels) as ApplicationStatus[]).map(status => <button type="button" key={status} className="s-status-filter" aria-pressed={filter === status} onClick={() => setFilter(status)}>{applicationLabels[status]}<span>{applications.filter(a => a.status === status).length}</span></button>)}</div>
      {visible.length ? <div className="s-applications-grid">{visible.map(application => {
        const stage = application.status === "DELIVERED" ? 3 : application.status === "APPROVED" ? 2 : ["SUBMITTED", "REJECTED"].includes(application.status) ? 1 : 0;
        const inProgress = application.status === "GENERATING" || application.status === "FAILED";
        return <article className="s-application" key={application.applicationId}><div className="s-application-heading"><div><p>{application.companyName}</p><h2>{application.title}</h2></div><ApplicationStatusBadge status={application.status} /></div>
          <p className="s-application-date">{application.submittedAt ? `${dateLabel(application.submittedAt)} 제출 · 문장 ${application.userEditedCount}개 직접 수정` : "지원 서류를 준비하고 있어요."}</p>
          <ol className="s-stages">{["서류 제출", "담당자 검토", "기관 추천 전달"].map((label, i) => <li key={label} className={i < stage ? "is-done" : i === stage ? "is-current" : ""}><h3>0{i + 1} · {label}</h3>{stage > 0 && <p>{i === 0 ? "서류를 제출했어요" : i === 1 ? application.status === "REJECTED" ? "담당자가 보완 사항을 안내해드려요" : stage >= 2 ? "검토를 마쳤어요" : "확인 중이에요" : stage === 3 ? "기업에 전달했어요" : "전달을 준비해요"}</p>}</li>)}</ol>
          <div className="s-application-bottom"><p>{application.status === "REJECTED" ? "담당 코디네이터가 보완 사항을 안내해드려요." : application.status === "FAILED" ? "초안 생성을 완료하지 못했어요. 생성 상태를 다시 확인해주세요." : application.status === "DRAFT" ? "나의 경험을 더해 지원서를 완성해주세요." : application.status === "GENERATING" ? "등록 자료를 지원 서류로 정리하고 있어요." : "검토를 요청한 서류도 다시 확인할 수 있어요."}</p><Link className="s-text-link" href={studentHref(`/student/applications/${application.applicationId}${inProgress ? "/progress" : ""}`, studentId)}>{inProgress ? "생성 상태 확인" : application.status === "DRAFT" ? "초안 이어서 보기" : "서류 확인"}<StudentIcon name="arrow" /></Link></div>
        </article>;
      })}</div> : <div className="s-empty"><StudentIcon name="folder" /><h2>{applications.length ? "이 상태의 지원서가 없어요." : "아직 지원한 공고가 없어요."}</h2><p>{applications.length ? "다른 상태를 선택해보세요." : "맞춤 공고에서 나에게 맞는 기회를 만나보세요."}</p>{!applications.length && <Link href={studentHref("/student", studentId)} className="s-button s-button-primary">맞춤 공고 보기</Link>}</div>}
      {data.otherItems.length > 0 && <section className="s-activity" aria-label="이번 주 다른 공고"><div className="s-section-heading"><div className="s-section-title"><h2>이번 주 다른 공고</h2></div></div><div className="s-other-jobs">{data.otherItems.map(item => <article className="s-other-job" key={item.itemId}><div><h3>{item.title}</h3><p>{item.companyName}</p></div>{item.response === "NONE" ? <Link className="s-text-link" href={studentHref(`/student/jobs/${item.itemId}`, studentId)}>살펴보기<StudentIcon name="chevron" /></Link> : <p className={`s-response-badge s-response-${item.response}`}>{responseLabels[item.response]}{item.response === "PASS" && item.passReason && ` · ${passLabels[item.passReason]}`}</p>}</article>)}</div></section>}
    </>}
  </StudentScreen>;
}
