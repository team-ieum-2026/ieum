"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useRef, useState } from "react";
import { getRecommendationItem, getStudentApplications, respondToItem } from "@/lib/api/student";
import { getApplication } from "@/lib/api/resume";
import { ActionError, ApiState, StudentScreen } from "./ScreenParts";
import { deadlineLabel, studentHref } from "./navigation";
import { responseLabels } from "./labels";
import { rememberResponse, restoreResponse } from "./mockRecommendationResponses";
import { errorMessage, useApi } from "./useApi";
import StudentPageHeading from "./StudentPageHeading";
import StudentIcon from "./StudentIcon";
import ApplicationLoading from "./ApplicationLoading";
import { rememberApplicationLoadingStart } from "./applicationLoadingDelay";
import ResponseCancelButton from "./ResponseCancelButton";
import CareerImage from "./CareerImage";
import HelpRequestComposer from "./HelpRequestComposer";

export default function JobDetailScreen({ itemId, studentId }: { itemId: number; studentId: number }) {
  const router = useRouter();
  const load = useCallback(async () => { const result = await getRecommendationItem(itemId); return { ...result, response: restoreResponse(studentId, itemId, result.response) }; }, [itemId, studentId]);
  const { data, loading, error, reload, setData } = useApi(load);
  const [busy, setBusy] = useState(false);
  const [applying, setApplying] = useState(false);
  const [helpOpen, setHelpOpen] = useState(false);
  const busyRef = useRef(false);
  const [actionError, setActionError] = useState<string | null>(null);
  async function apply() {
    if (busyRef.current) return;
    const loadingStartedAt = Date.now();
    busyRef.current = true; setBusy(true); setActionError(null);
    setApplying(true);
    try {
      const result = await respondToItem(itemId, { response: "APPLY" }, studentId);
      if (!result.applicationId) throw new Error("지원서 정보를 받지 못했어요. 다시 확인해 주세요.");
      rememberApplicationLoadingStart(result.applicationId, loadingStartedAt);
      rememberResponse(studentId, itemId, "APPLY"); router.push(studentHref(`/student/applications/${result.applicationId}/progress`, studentId));
    } catch (error) { setActionError(errorMessage(error)); busyRef.current = false; setBusy(false); setApplying(false); }
  }
  async function openApplication() {
    if (!data || busyRef.current) return;
    busyRef.current = true; setBusy(true); setActionError(null);
    try {
      const result = await getStudentApplications(studentId);
      const candidates = result.applications
        .filter(application => application.companyName === data.job.companyName && application.title === data.job.title)
        .sort((left, right) => Number(right.status === "DRAFT") - Number(left.status === "DRAFT"));
      for (const candidate of candidates) {
        const application = await getApplication(candidate.applicationId);
        if (application.job.jobPostingId !== data.job.jobPostingId) continue;
        const progress = application.status === "GENERATING" || application.status === "FAILED";
        router.push(studentHref(`/student/applications/${candidate.applicationId}${progress ? "/progress" : ""}`, studentId));
        return;
      }
      throw new Error("이 공고의 지원서를 찾지 못했어요. 지원 현황을 확인해주세요.");
    } catch (error) {
      setActionError(errorMessage(error));
    } finally {
      busyRef.current = false; setBusy(false);
    }
  }
  if (applying) return <StudentScreen><ApplicationLoading fullHeight /></StudentScreen>;
  return <StudentScreen>
    <div className="s-job-detail-heading"><StudentPageHeading title={data?.job.companyName ?? ""} description={data ? [data.job.location, data.job.employmentType].filter(Boolean).join(" · ") : undefined} back={studentHref("/student", studentId)} /></div>
    <ApiState loading={loading} error={error} retry={reload} notFoundHref={studentHref("/student", studentId)} notFoundLabel="맞춤 공고로" />
    {data && <div className="s-detail-layout"><article className="s-panel s-job-detail"><CareerImage variant={data.rank - 1} jobTitle={data.job.title} /><h2 className="s-detail-title">{data.job.title}</h2>
      <dl className="s-job-facts">{[["근무지", data.job.workAddress || data.job.location], ["고용 형태", data.job.employmentType], ["경력", data.job.career], ["학력", data.job.education], ["급여", data.job.salaryText]].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value || "정보 없음"}</dd></div>)}<div><dt>마감</dt><dd>{data.job.deadlineOn ? `${data.job.deadlineOn} · ${deadlineLabel(data.job.dDay)}` : deadlineLabel(data.job.dDay)}{data.job.untilFilled && <span className="block">채용 시 마감</span>}</dd></div></dl>
      <section className="s-detail-section"><h2>이 공고를 추천한 이유</h2>{data.reasons.map((reason, index) => <div className="s-reason-detail" key={index}><p><StudentIcon name="sparkles" /><span>{reason.text}</span></p>{reason.evidence.map((evidence, i) => <details className="s-evidence-details" key={i}><summary>근거 · {evidence.label || "등록 자료"}</summary><blockquote>{evidence.quote || "근거 원문이 제공되지 않았어요."}</blockquote>{evidence.collectedAt && <small>수집일 · {evidence.collectedAt}</small>}</details>)}</div>)}</section>
      <section className="s-detail-section"><h2>공고 요약</h2><ul>{data.summary.map(text => <li key={text}>{text}</li>)}</ul>{data.job.description && <details className="s-full-description" open><summary>공고 내용 자세히 보기</summary><p>{data.job.description}</p></details>}</section><p className="s-source-note">데이터 출처 · {data.job.sourceLabel}</p>
    </article><aside className="s-panel s-action-panel"><ActionError message={actionError} />
      {data.response !== "NONE" ? <div className="s-action-stack"><span className={`s-response-badge s-response-${data.response}`}>{responseLabels[data.response]}</span><p className="s-letter-note">이미 응답한 공고예요.</p>{data.response === "APPLY" && <button className="s-button s-button-primary" type="button" disabled={busy} onClick={openApplication}>{busy ? "확인 중…" : "지원 현황 보기"}<StudentIcon name="arrow" /></button>}</div> : <div className="s-action-stack"><div className="s-ai-apply"><button className="s-button s-button-primary" type="button" disabled={busy} onClick={apply}>{busy ? "처리 중…" : "AI로 지원서 초안 만들기"}<StudentIcon name="arrow" /></button></div><Link aria-disabled={busy} tabIndex={busy ? -1 : undefined} className={`s-button${busy ? " pointer-events-none opacity-50" : ""}`} href={studentHref(`/student/jobs/${itemId}/pass`, studentId)}>이 공고는 넘어가기</Link><button className="s-button" type="button" disabled={busy} onClick={() => setHelpOpen(true)}><StudentIcon name="message" />담당자에게 도움 요청</button></div>}
      <ResponseCancelButton itemId={itemId} studentId={studentId} response={data.response} onCancelled={() => { setData({ ...data, response: "NONE" }); reload(); }} />
    </aside></div>}
    {helpOpen && data && <HelpRequestComposer key={`${studentId}-${itemId}`} studentId={studentId} job={{ itemId, companyName: data.job.companyName, title: data.job.title }} onClose={() => setHelpOpen(false)} onConflict={reload} />}
  </StudentScreen>;
}
