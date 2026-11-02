"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { getCurrentRecommendations, getStudentApplications, getStudentName, refreshRecommendations, subscribeApplicationChanges } from "@/lib/api/student";
import { ApiState, ActionError, StudentScreen } from "./ScreenParts";
import StudentPageHeading from "./StudentPageHeading";
import StudentIcon from "./StudentIcon";
import StudentHeroArt from "./StudentHero";
import RecommendationLayout from "./RecommendationLayout";
import ResponseCancelButton from "./ResponseCancelButton";
import ApplicationStatusBadge from "./ApplicationStatusBadge";
import { useStudentProfile } from "./StudentShell";
import { deadlineLabel, studentHref } from "./navigation";
import { restoreResponse } from "./mockRecommendationResponses";
import { errorMessage, useApi } from "./useApi";

const responseBadgeLabels = { APPLY: "지원완료", HELP: "도움요청 완료", PASS: "pass" };

export default function RecommendationsScreen({ studentId }: { studentId: number }) {
  const profileFrame = useStudentProfile();
  const profile = profileFrame?.profile;
  const load = useCallback(async () => {
    const [result, studentName] = await Promise.all([getCurrentRecommendations(studentId), getStudentName(studentId)]);
    return { ...result, studentName, items: result.items.map(item => ({ ...item, response: restoreResponse(studentId, item.itemId, item.response) })) };
  }, [studentId]);
  const { data, loading, error, reload, setData } = useApi(load);
  const loadApplications = useCallback(() => getStudentApplications(studentId), [studentId]);
  const activity = useApi(loadApplications);
  const reloadActivity = activity.reload;
  useEffect(() => subscribeApplicationChanges(() => { reload(); reloadActivity(); }), [reload, reloadActivity]);
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
    <StudentPageHeading eyebrow="YOUR NEXT CHAPTER" title={data ? `${data.studentName}님, 반가워요.` : "나의 다음 기회를 만나보세요."} />
    <ApiState loading={loading} error={error} retry={reload} />
    {data && <>
      <div className="s-hero-grid"><section className="s-hero"><div className="s-hero-copy"><span className="s-pill"><StudentIcon name="sparkles" />나를 위한 이번 주의 기회</span><h2>쌓아온 경험이,<br /><em>다음 기회</em>가 되도록.</h2><p>나의 경험에 맞는 공고를 만나고,<br />{ " " }근거가 연결된 지원 서류까지 함께 준비해요.</p></div><StudentHeroArt /></section>
      </div>
      <section className="s-recommend-polished" aria-labelledby="recommendation-heading"><div className="s-section-heading"><div><div className="s-section-title"><h2 id="recommendation-heading">나에게 도착한 맞춤 공고</h2><span className="s-count">{data.items.length}</span></div><p className="s-section-description">추천 이유를 확인하고, 나에게 맞는 다음 걸음을 골라보세요.</p></div><button type="button" className="s-refresh" disabled={refreshing} onClick={refresh}><StudentIcon name="refresh" />{refreshing ? "추천받는 중…" : "새로 추천받기"}</button></div>
        <div className="s-filter-row"><div className="s-filter-tags"><Link className="s-filter-chip" href={studentHref("/student/profile", studentId)}><StudentIcon name="briefcase" />{profile?.basic.desiredJob || "희망 직무 설정"}</Link><Link className="s-filter-chip" href={studentHref("/student/profile", studentId)}><StudentIcon name="pin" />{profile?.basic.preferredRegion || "희망 지역 설정"}</Link></div>
          <p className="s-updated"><span>{new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(data.createdAt))} 추천</span><br /><span>· {data.basis === "PROFILE_LATEST" ? "내 프로필 최신 기준" : data.basis}</span></p>
        </div>
        <ActionError message={actionError} />
        {data.items.length ? <div className="s-job-grid" aria-busy={refreshing}>{data.items.map((item, index) => <article key={item.itemId} className={`s-job-card${index === 0 ? " is-featured" : ""}`}>
          <div className="s-card-top"><div className="s-response-actions">{item.response === "NONE" ? <span className="s-recommend-rank"><StudentIcon name="sparkles" />추천 {String(item.rank).padStart(2, "0")}</span> : <span className={`s-response-badge s-response-${item.response}`}>{responseBadgeLabels[item.response]}</span>}<ResponseCancelButton itemId={item.itemId} studentId={studentId} response={item.response} onCancelled={() => { setData({ ...data, items: data.items.map(current => current.itemId === item.itemId ? { ...current, response: "NONE" } : current) }); reload(); activity.reload(); }} /></div><span className={`s-deadline${item.job.dDay !== null && item.job.dDay <= 7 ? " is-urgent" : ""}`}>{deadlineLabel(item.job.dDay)}{item.job.untilFilled && <span className="block">채용 시 마감</span>}</span></div>
          <Link className="s-job-card-link" href={studentHref(`/student/jobs/${item.itemId}`, studentId)}>
          <div className="s-company"><span className="s-company-mark" aria-hidden="true">{item.job.companyName.replace(/^\(주\)\s*/, "").slice(0, 1)}</span><span><strong>{item.job.companyName}</strong><small>{item.job.location} · {item.job.employmentType}</small></span></div><h3>{item.job.title}</h3><p className="s-job-meta"><StudentIcon name="briefcase" />{item.job.career} · {item.job.employmentType}</p>
          <div className="s-reason-box"><p><StudentIcon name="sparkles" />이런 점이 잘 맞아요</p><ul>{item.reasonSummary.slice(0, 2).map(reason => <li key={reason}><StudentIcon name="check" /><span>{reason}</span></li>)}</ul></div><span className="s-card-cta">공고 자세히 보기<StudentIcon name="arrow" /></span>
        </Link></article>)}</div> : <div className="s-empty"><StudentIcon name="leaf" /><h2>지금은 추천할 공고가 없어요.</h2><p>관심 직무나 희망 지역이 바뀌었다면<br />내 프로필에서 수정해주세요.</p><Link className="s-button s-button-primary" href={studentHref("/student/profile", studentId)}>내 프로필 확인</Link></div>}
      </section>
      <section className="s-activity" aria-labelledby="activity-heading"><div className="s-section-heading"><div className="s-section-title"><h2 id="activity-heading">이어가고 있는 여정</h2></div><Link className="s-text-link" href={studentHref("/student/applications", studentId)}>지원 현황 전체보기<StudentIcon name="arrow" /></Link></div>
        <ApiState loading={activity.loading} error={activity.error} retry={activity.reload} />
        {activity.data && <div className="s-activity-card"><span className="s-activity-symbol"><StudentIcon name="file" /></span><div className="s-activity-copy"><strong>{latest ? `${latest.companyName} · ${latest.title}` : "아직 준비 중인 지원서가 없어요."}</strong><p>{latest ? "지금까지 준비한 내용을 확인하고 다음 걸음을 이어가세요." : "맞춤 공고에서 나에게 맞는 기회를 골라보세요."}</p></div>{latest && <ApplicationStatusBadge status={latest.status} />}<Link className="s-text-link" href={studentHref(latest && latest.status === "DRAFT" ? `/student/applications/${latest.applicationId}` : "/student/applications", studentId)}>{latest?.status === "DRAFT" ? "초안 이어서 보기" : "지원 현황 보기"}<StudentIcon name="arrow" /></Link></div>}
      </section>
    </>}
  </RecommendationLayout></StudentScreen>;
}
