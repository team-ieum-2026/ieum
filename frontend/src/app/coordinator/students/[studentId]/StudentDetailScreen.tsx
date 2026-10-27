"use client";

import Link from "next/link";
import { useCallback, useState } from "react";
import { addContactLog, getCoordinatorStudent } from "@/lib/api/coordinatorStudents";
import type { ContactType } from "@/types/student";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import BottomSheet from "@/components/ui/BottomSheet";
import StatusChip from "@/components/ui/StatusChip";
import Tag from "@/components/ui/Tag";
import { ActionError, ApiState } from "@/components/student/ScreenParts";
import { dateLabel } from "@/components/student/navigation";
import { applicationLabels, passLabels, responseLabels, statusLabels, statusTone, subStatusLabels } from "@/components/student/labels";
import { errorMessage, useApi } from "@/components/student/useApi";

const contactLabels: Record<ContactType, string> = { PHONE: "통화", MEETING: "상담", MESSAGE: "메시지" };

export default function StudentDetailScreen({ studentId }: { studentId: number }) {
  const load = useCallback(() => getCoordinatorStudent(studentId), [studentId]);
  const { data, loading, error, reload, setData } = useApi(load);
  const [open, setOpen] = useState(false);
  const [contactType, setContactType] = useState<ContactType>("PHONE");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  return <div className="space-y-[18px]">
    <Link href="/coordinator/students" className="text-[13px] text-primary hover:underline">← 수료생 목록으로</Link>
    <ApiState loading={loading} error={error} retry={reload} />
    {data && <>
      <div className="flex items-center justify-between gap-3"><div className="flex items-center gap-3"><h1 className="text-2xl font-bold">{data.student.name}</h1><StatusChip tone={statusTone(data.student.status)}>{statusLabels[data.student.status]}{data.student.subStatus && ` · ${subStatusLabels[data.student.subStatus]}`}</StatusChip></div><Button variant="secondary" onClick={() => { setOpen(true); setSaveError(null); setSaved(false); }}>통화 · 상담 기록 추가</Button></div>
      {saved && <p role="status" className="rounded-xl bg-primary-tint p-3 text-sm text-primary">상담 기록을 저장했어요.</p>}
      <div className="grid grid-cols-2 items-start gap-4">
        <div className="space-y-4">
          <Card className="space-y-4 p-5"><h2 className="text-[15px] font-bold">기본 정보</h2><dl className="grid grid-cols-[80px_1fr] gap-x-3 gap-y-3 text-[13px]">{[["과정", data.student.courseLabel], ["수료일", dateLabel(data.student.completedOn)], ["이메일", data.student.email], ["희망 직무", data.student.desiredJob], ["희망 지역", data.student.preferredRegion], ["자격증", data.competencyProfile?.certificates.join(", ")], ["상태 근거", data.student.reason]].map(([label, value]) => <div key={label} className="contents"><dt className="text-ink-sub">{label}</dt><dd className="break-words">{value || "—"}</dd></div>)}</dl></Card>
          <Card className="space-y-4 p-5"><div className="flex items-center justify-between"><h2 className="text-[15px] font-bold">역량 프로필</h2><Tag source="AI" /></div>{data.competencyProfile ? <div className="space-y-3 text-[13px]"><div><h3 className="mb-1 text-xs font-bold text-ink-sub">역할</h3><p>{data.competencyProfile.roles.join(", ") || "추출된 역할이 없어요."}</p></div><div className="flex flex-wrap gap-1.5">{data.competencyProfile.skills.map((skill) => <span key={skill.name} className="rounded-md border border-line px-2 py-1 text-xs">{skill.name}</span>)}</div><div><h3 className="mb-1 text-xs font-bold text-ink-sub">성과</h3><ul className="space-y-2">{data.competencyProfile.achievements.map((achievement) => <li key={achievement.text}>{achievement.text}</li>)}</ul>{!data.competencyProfile.achievements.length && <p className="text-ink-sub">추출된 성과가 없어요.</p>}</div></div> : <p className="text-sm text-ink-sub">아직 생성된 역량 프로필이 없어요.</p>}</Card>
          <Card className="space-y-3 p-5"><div className="flex items-center justify-between"><h2 className="text-[15px] font-bold">등록 자료</h2><Tag source="REGISTERED" /></div><ul className="list-disc space-y-2 pl-5 text-[13px]"><li>학력 {data.registered.education}건</li><li>자격증 {data.registered.certificates}건</li><li>프로젝트 · 포트폴리오 {data.registered.projects}건</li><li>자기소개서 원본: {data.registered.hasCoverLetter ? "등록됨" : "미등록"}</li></ul></Card>
        </div>
        <div className="space-y-4">
          <Card className="space-y-4 p-5"><h2 className="text-[15px] font-bold">추천 · 응답 이력</h2>{!data.history.length && <p className="text-sm text-ink-sub">추천 이력이 없어요.</p>}{data.history.map((history) => <div key={history.recommendationId}><p className="mb-2 text-xs text-ink-muted">{dateLabel(history.createdAt)} 추천</p><div className="divide-y divide-line">{history.items.map((item) => <div key={item.itemId} className="space-y-2 py-3 text-[13px]"><p className="font-bold">{item.companyName} · {item.title}</p><div className="flex flex-wrap items-center gap-2"><StatusChip>{responseLabels[item.response]}</StatusChip>{item.applicationStatus && <StatusChip tone="success">{applicationLabels[item.applicationStatus]}</StatusChip>}{item.applicationId && <Link href={`/coordinator/applications/${item.applicationId}`} className="font-bold text-primary">서류 보기 →</Link>}</div>{item.passReason && <p className="text-ink-sub">{passLabels[item.passReason]}</p>}{item.passNote && <p className="rounded-lg bg-coord-bg p-2">비고: {item.passNote}</p>}<p className="text-xs text-ink-muted">마지막 응답: {dateLabel(item.respondedAt)}</p></div>)}</div></div>)}</Card>
          <Card className="space-y-4 p-5"><h2 className="text-[15px] font-bold">비고 · 상담 기록</h2>{!data.contactLogs.length && <p className="text-[13px] text-ink-sub">아직 남긴 비고나 상담 기록이 없어요.</p>}<ul className="divide-y divide-line">{data.contactLogs.map((log, index) => <li key={`${log.contactLogId}-${index}`} className="space-y-2 py-3 first:pt-0"><p className="text-xs text-ink-sub">{dateLabel(log.createdAt)} · {contactLabels[log.contactType]}</p><p className="text-[13px] leading-relaxed whitespace-pre-wrap">{log.note}</p></li>)}</ul></Card>
        </div>
      </div>
      <BottomSheet open={open} onClose={() => { if (!busy) setOpen(false); }} title="통화 · 상담 기록 추가"><form className="space-y-4" onSubmit={async (event) => {
        event.preventDefault(); if (!note.trim() || busy) return;
        setBusy(true); setSaveError(null);
        try { const log = await addContactLog(studentId, { contactType, note: note.trim() }); setData({ ...data, contactLogs: [log, ...data.contactLogs] }); setNote(""); setOpen(false); setSaved(true); }
        catch (error) { setSaveError(errorMessage(error)); }
        finally { setBusy(false); }
      }}><label className="block space-y-2 text-sm"><span>연락 유형</span><select value={contactType} disabled={busy} onChange={(event) => setContactType(event.target.value as ContactType)} className="min-h-11 w-full rounded-xl border border-line bg-surface px-3">{(Object.keys(contactLabels) as ContactType[]).map((value) => <option key={value} value={value}>{contactLabels[value]}</option>)}</select></label><label className="block space-y-2 text-sm"><span>상담 내용</span><textarea required rows={5} value={note} disabled={busy} onChange={(event) => setNote(event.target.value)} placeholder="상담 내용을 남겨주세요" className="w-full rounded-xl border border-line p-3" /></label><ActionError message={saveError} /><Button type="submit" disabled={!note.trim() || busy} className="w-full">{busy ? "저장 중…" : "저장"}</Button></form></BottomSheet>
    </>}
  </div>;
}
