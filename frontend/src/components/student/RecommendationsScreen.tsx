"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { getCurrentRecommendations, getStudentApplications, getStudentName, refreshRecommendations, subscribeApplicationChanges } from "@/lib/api/student";
import { getApplication } from "@/lib/api/resume";
import type { ApplicationStatus } from "@/types/student";
import { ApiState, ActionError, StudentScreen } from "./ScreenParts";
import StudentIcon from "./StudentIcon";
import JobCard from "./JobCard";
import RecommendationLayout from "./RecommendationLayout";
import ApplicationStatusBadge from "./ApplicationStatusBadge";
import { useStudentProfile } from "./StudentShell";
import { studentHref } from "./navigation";
import { restoreResponse } from "./mockRecommendationResponses";
import { errorMessage, useApi } from "./useApi";

export default function RecommendationsScreen({ studentId }: { studentId: number }) {
  const profileFrame = useStudentProfile();
  const profile = profileFrame?.profile;
  const load = useCallback(async () => {
    const [result, studentName] = await Promise.all([getCurrentRecommendations(studentId), getStudentName(studentId)]);
    return { ...result, studentName, items: result.items.map(item => ({ ...item, response: restoreResponse(studentId, item.itemId, item.response) })) };
  }, [studentId]);
  const { data, loading, error, reload, setData } = useApi(load);
  const recommendedItems = data?.items;
  const loadApplications = useCallback(async () => {
    const result = await getStudentApplications(studentId);
    const candidates = result.applications
      .filter(application => recommendedItems?.some(item => item.response === "APPLY" && item.job.companyName === application.companyName && item.job.title === application.title))
      .sort((left, right) => right.applicationId - left.applicationId);
    const details = await Promise.allSettled(candidates.map(application => getApplication(application.applicationId)));
    const statusesByJob: Record<number, ApplicationStatus> = {};
    for (const detail of details) {
      if (detail.status !== "fulfilled") continue;
      const application = detail.value;
      // 같은 회사·직무명인 다른 공고와 섞이지 않게 실제 공고 번호로 연결한다.
      if (!statusesByJob[application.job.jobPostingId]) statusesByJob[application.job.jobPostingId] = application.status;
    }
    return { ...result, statusesByJob };
  }, [studentId, recommendedItems]);
  const activity = useApi(loadApplications, { refreshIntervalMs: process.env.NEXT_PUBLIC_USE_MOCK === "false" ? 5000 : 0 });
  const reloadActivity = activity.reload;
  useEffect(() => subscribeApplicationChanges(() => { reload(); reloadActivity(); }), [reload, reloadActivity]);
  useEffect(() => { window.addEventListener("focus", reloadActivity); return () => window.removeEventListener("focus", reloadActivity); }, [reloadActivity]);
  const latest = activity.data?.applications.find(application => application.status === "DRAFT") ?? activity.data?.applications[0];
  const [refreshing, setRefreshing] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  async function refresh() {
    if (!data || refreshing) return;
    setRefreshing(true); setActionError(null);
    try {
      const result = await refreshRecommendations(studentId);
      setData({ ...result, studentName: data.studentName, items: result.items.map(item => ({ ...item, response: restoreResponse(studentId, item.itemId, item.response) })) });
    } catch (error) { setActionError(errorMessage(error)); }
    finally { setRefreshing(false); }
  }
  return <StudentScreen lighter><RecommendationLayout>
    <section className="s-recommend-polished" aria-labelledby="recommendation-heading">
      <header className="s-recommend-header">
        <div>
          <h1 id="recommendation-heading">{data
            ? data.items.length > 0
              ? <>{data.studentName}님, 맞춤 공고 <span className="s-recommend-count">{data.items.length}</span>개가 도착했어요</>
              : <>{data.studentName}님, 새로운 기회를 준비하고 있어요</>
            : "나의 맞춤 공고"}{data && <> <span className="s-greeting-wave" role="img" aria-label="손 흔드는 인사">👋</span></>}</h1>
          <p>나의 경험과 역량을 바탕으로 추천한 새로운 기회를 확인해 보세요.</p>
        </div>
      </header>
      <ApiState loading={loading} error={error} retry={reload} />
      {data && <>
        <div className="s-filter-row"><div className="s-filter-tags"><Link className="s-filter-chip" href={studentHref("/student/profile", studentId)}><StudentIcon name="briefcase" />{profile?.basic.desiredJob || "희망 직무 설정"}</Link><Link className="s-filter-chip" href={studentHref("/student/profile", studentId)}><StudentIcon name="pin" />{profile?.basic.preferredRegion || "희망 지역 설정"}</Link></div>
          <div className="s-recommend-tools">
            <p className="s-updated"><span>{new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(data.createdAt))} 추천</span><br /><span>· {data.basis === "PROFILE_LATEST" ? "내 프로필 최신 기준" : data.basis}</span></p>
            <button type="button" className="s-refresh" disabled={refreshing} onClick={refresh}><StudentIcon name="refresh" />{refreshing ? "추천받는 중…" : "새로 추천받기"}</button>
          </div>
        </div>
        <ActionError message={actionError} />
        {data.items.length ? <div className="s-job-grid" aria-busy={refreshing}>{data.items.map(item => <JobCard key={item.itemId} item={item} studentId={studentId} applicationStatus={activity.data?.statusesByJob[item.job.jobPostingId]} onCancelled={() => { setData({ ...data, items: data.items.map(current => current.itemId === item.itemId ? { ...current, response: "NONE" } : current) }); reload(); activity.reload(); }} />)}</div> : <div className="s-empty"><StudentIcon name="sparkles" /><h2>지금은 추천할 공고가 없어요.</h2><p>관심 직무나 희망 지역이 바뀌었다면<br />내 프로필에서 수정해주세요.</p><Link className="s-button s-button-primary" href={studentHref("/student/profile", studentId)}>내 프로필 확인</Link></div>}
      </>}
    </section>
    {data && <>
      <section className="s-activity" aria-labelledby="activity-heading"><div className="s-section-heading"><div className="s-section-title"><h2 id="activity-heading">이어가고 있는 여정</h2></div><Link className="s-text-link" href={studentHref("/student/applications", studentId)}>지원 현황 전체보기<StudentIcon name="arrow" /></Link></div>
        <ApiState loading={activity.loading} error={activity.error} retry={activity.reload} />
        {activity.data && <div className="s-activity-card"><span className="s-activity-symbol"><StudentIcon name="file" /></span><div className="s-activity-copy"><strong>{latest ? `${latest.companyName} · ${latest.title}` : "아직 준비 중인 지원서가 없어요."}</strong><p>{latest ? "지금까지 준비한 내용을 확인하고 다음 걸음을 이어가세요." : "맞춤 공고에서 나에게 맞는 기회를 골라보세요."}</p></div>{latest && <ApplicationStatusBadge status={latest.status} />}<Link className="s-text-link" href={studentHref(latest && latest.status === "DRAFT" ? `/student/applications/${latest.applicationId}` : "/student/applications", studentId)}>{latest?.status === "DRAFT" ? "초안 이어서 보기" : "지원 현황 보기"}<StudentIcon name="arrow" /></Link></div>}
      </section>
    </>}
  </RecommendationLayout></StudentScreen>;
}
