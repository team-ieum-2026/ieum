"use client";

import Link from "next/link";
import { useCallback, useState } from "react";
import { getCoordinatorStudents } from "@/lib/api/coordinatorStudents";
import type { StudentStatus } from "@/types/student";
import Button from "@/components/ui/Button";
import StatusChip from "@/components/ui/StatusChip";
import { ApiState } from "@/components/student/ScreenParts";
import { dateLabel } from "@/components/student/navigation";
import { statusLabels, statusTone, subStatusLabels } from "@/components/student/labels";
import { useApi } from "@/components/student/useApi";

export default function StudentsScreen() {
  const [status, setStatus] = useState<StudentStatus | undefined>();
  const [input, setInput] = useState("");
  const [q, setQ] = useState("");
  const [page, setPage] = useState(1);
  const load = useCallback(() => getCoordinatorStudents({ status, q, page, size: 20 }), [status, q, page]);
  const { data, loading, error, reload } = useApi(load);
  return <div className="space-y-[18px]">
    <h1 className="text-2xl font-bold">수료생 목록</h1>
    <div className="flex flex-wrap items-center gap-2"><div className="flex flex-wrap gap-2" aria-label="수료생 상태 필터">{[undefined, ...Object.keys(statusLabels) as StudentStatus[]].map((value) => <button key={value ?? "ALL"} type="button" aria-pressed={status === value} onClick={() => { setStatus(value); setPage(1); }} className={`min-h-10 rounded-full border px-3 text-[13px] font-bold focus-visible:outline-2 focus-visible:outline-primary ${status === value ? "border-primary bg-primary text-surface" : "border-line bg-surface text-ink-sub"}`}>{value ? statusLabels[value] : "전체"}</button>)}</div>
      <form className="ml-auto flex items-center gap-2" onSubmit={(event) => { event.preventDefault(); setQ(input.trim()); setPage(1); }}><label htmlFor="student-search" className="text-[13px] text-ink-sub">이름 검색</label><input id="student-search" type="search" value={input} onChange={(event) => setInput(event.target.value)} placeholder="이름을 입력하세요" className="min-h-10 w-40 rounded-xl border border-line bg-surface px-3 text-sm" /><Button type="submit" variant="secondary" className="min-h-10 px-3 py-2">검색</Button></form>
    </div>
    <ApiState loading={loading} error={error} retry={reload} />
    {data && <>
      <div className="overflow-x-auto rounded-2xl border border-line bg-surface"><table className="w-full text-left text-[13px]"><caption className="sr-only">수료생 목록: 이름, 과정, 수료일, 상태, 마지막 응답 및 상세</caption><thead className="border-b border-line bg-coord-bg text-xs text-ink-sub"><tr>{["이름", "과정 · 기수", "수료일", "상태", "세부", "마지막 응답", "비고", "상세"].map((label) => <th key={label} scope="col" className="px-3 py-4 font-medium whitespace-nowrap">{label}</th>)}</tr></thead><tbody>{data.items.map((student) => <tr key={student.studentId} className="border-b border-line last:border-b-0 hover:bg-primary-tint"><th scope="row" className="px-3 py-4 font-bold whitespace-nowrap">{student.name}</th><td className="px-3 py-4">{student.courseLabel}</td><td className="px-3 py-4 whitespace-nowrap">{dateLabel(student.completedOn)}</td><td className="px-3 py-4"><StatusChip tone={statusTone(student.status)}>{statusLabels[student.status]}</StatusChip></td><td className="px-3 py-4 text-ink-sub">{student.subStatus ? subStatusLabels[student.subStatus] : "—"}</td><td className="px-3 py-4 whitespace-nowrap">{dateLabel(student.lastResponseAt)}</td><td className="px-3 py-4">{student.hasNote ? "있음" : "—"}</td><td className="px-3 py-4"><Link href={`/coordinator/students/${student.studentId}`} aria-label={`${student.name} 상세 보기`} className="font-bold text-primary underline-offset-4 hover:underline">상세</Link></td></tr>)}</tbody></table>{!data.items.length && <p className="p-10 text-center text-sm text-ink-sub">조건에 맞는 수료생이 없어요.</p>}</div>
      <div className="flex items-center justify-between"><p role="status" className="text-xs text-ink-muted">{data.total ? `${(page - 1) * data.size + 1}–${(page - 1) * data.size + data.items.length}` : "0"} / {data.total}명</p><div className="flex items-center gap-3"><Button variant="secondary" disabled={page <= 1} onClick={() => setPage((value) => value - 1)} className="min-h-9 py-1">이전</Button><span className="text-xs">{page} / {Math.max(1, Math.ceil(data.total / data.size))}</span><Button variant="secondary" disabled={page * data.size >= data.total} onClick={() => setPage((value) => value + 1)} className="min-h-9 py-1">다음</Button></div></div>
    </>}
  </div>;
}
