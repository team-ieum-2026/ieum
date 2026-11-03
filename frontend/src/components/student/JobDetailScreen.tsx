"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useRef, useState } from "react";
import { getRecommendationItem, respondToItem } from "@/lib/api/student";
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
  if (applying) return <StudentScreen><ApplicationLoading fullHeight /></StudentScreen>;
  return <StudentScreen>
    <StudentPageHeading title="나의 경험과 연결되는 기회" description="공고 조건과 추천 근거를 확인하고 선택해주세요." back={studentHref("/student", studentId)} />
    <ApiState loading={loading} error={error} retry={reload} notFoundHref={studentHref("/student", studentId)} notFoundLabel="맞춤 공고로" />
    {data && <div className="s-detail-layout"><article className="s-panel s-job-detail"><CareerImage variant={itemId} /><div className="s-company"><span className="s-company-mark" aria-hidden="true">{data.job.companyName.replace(/^\(주\)\s*/, "").slice(0, 1)}</span><span><strong>{data.job.companyName}</strong><small>{data.job.location} · {data.job.employmentType}</small></span></div><h2 className="s-detail-title">{data.job.title}</h2>
      <dl className="s-job-facts">{[["근무지", data.job.workAddress || data.job.location], ["고용 형태", data.job.employmentType], ["경력", data.job.career], ["학력", data.job.education], ["급여", data.job.salaryText]].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value || "정보 없음"}</dd></div>)}<div><dt>마감</dt><dd>{data.job.deadlineOn ? `${data.job.deadlineOn} · ${deadlineLabel(data.job.dDay)}` : deadlineLabel(data.job.dDay)}{data.job.untilFilled && <span className="block">채용 시 마감</span>}</dd></div></dl>
      <section className="s-detail-section"><h2>이 공고를 추천한 이유</h2>{data.reasons.map((reason, index) => <div className="s-reason-detail" key={index}><p><StudentIcon name="sparkles" /><span>{reason.text}</span></p>{reason.evidence.map((evidence, i) => <details className="s-evidence-details" key={i}><summary>근거 · {evidence.label || "등록 자료"}</summary><blockquote>{evidence.quote || "근거 원문이 제공되지 않았어요."}</blockquote>{evidence.collectedAt && <small>수집일 · {evidence.collectedAt}</small>}</details>)}</div>)}</section>
      <section className="s-detail-section"><h2>공고 요약</h2><ul>{data.summary.map(text => <li key={text}>{text}</li>)}</ul>{data.job.description && <details className="s-full-description" open><summary>공고 내용 자세히 보기</summary><p>{data.job.description}</p></details>}</section><p className="s-source-note">데이터 출처 · {data.job.sourceLabel}</p>
    </article><aside className="s-panel s-action-panel"><h2>어떤 선택이든 괜찮아요.</h2><p>지원서를 준비하거나, 잠시 패스하거나,<br />담당자와 함께 살펴볼 수 있어요.</p><ActionError message={actionError} />
      {data.response !== "NONE" ? <div className="s-action-stack"><span className={`s-response-badge s-response-${data.response}`}>{responseLabels[data.response]}</span><p className="s-letter-note">이미 응답한 공고예요.</p>{data.response === "APPLY" && <Link className="s-button s-button-primary" href={studentHref("/student/applications", studentId)}>지원 현황 보기<StudentIcon name="arrow" /></Link>}</div> : <div className="s-action-stack"><button className="s-button s-button-primary" type="button" disabled={busy} onClick={apply}>{busy ? "처리 중…" : "지원 서류 준비하기"}<StudentIcon name="arrow" /></button><Link aria-disabled={busy} tabIndex={busy ? -1 : undefined} className={`s-button${busy ? " pointer-events-none opacity-50" : ""}`} href={studentHref(`/student/jobs/${itemId}/pass`, studentId)}>이번엔 패스</Link><button className="s-button" type="button" disabled={busy} onClick={() => setHelpOpen(true)}><StudentIcon name="message" />담당자에게 도움 요청</button></div>}
      <p className="s-source-note">어떤 선택이든 따로 설명하지 않아도 괜찮아요. 지원 서류는 먼저 확인한 뒤 검토를 요청해요.</p>
      <ResponseCancelButton itemId={itemId} studentId={studentId} response={data.response} onCancelled={() => { setData({ ...data, response: "NONE" }); reload(); }} />
    </aside></div>}
    {helpOpen && data && <HelpRequestComposer key={`${studentId}-${itemId}`} studentId={studentId} job={{ itemId, companyName: data.job.companyName, title: data.job.title }} onClose={() => setHelpOpen(false)} onConflict={reload} />}
  </StudentScreen>;
}
