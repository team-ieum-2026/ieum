"use client";
// 주인: FE-B — C2 수료생 관리 (11/1 역할 조정으로 FE-B, 11/1 개편 목업).
// - 상단 요약: 취업 완료 · 구직 중 · 도움 필요 · 추가 확인 필요 (대시보드 statusSummary). 누르면 그 상태로 거른다
// - 목록 4열: 수료생 / 교육과정 / 현재 상태 / 최근 응답 (GET /api/coordinator/students, 한 페이지 10명)
//   교육과정은 끝의 ' N기'를 떼어 표시. 추천 응답 · 도움 요청 · 서류 · 다음 행동 열은 목록 API 에 없어 숨김
// - 필터: 이름 검색(q) · 관리 상태(status · subStatus)만. 교육과정 · 지원 업무 필터는 API 에 없어 숨김
// - 필터를 바꿔도 열 너비 · 행 높이는 같게 (table-fixed + 고정 행 높이 + 줄임표)
import Link from "next/link";
import { useEffect, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Avatar, Badge, Icon, LoadState, PageTitle, Panel, PanelHead, btn } from "@/components/coordinator/CoordUi";
import { MANAGE_ORDER, courseName, manageCounts, manageLabels, manageTone, monthDay } from "@/components/coordinator/coordLabels";
import { subStatusLabels } from "@/components/student/labels";
import { ApiError } from "@/lib/api/client";
import { getDashboard } from "@/lib/api/coordinatorReview";
import { getCoordinatorStudents } from "@/lib/api/coordinatorStudents";
import type { Dashboard } from "@/types/coordinator";
import type { CoordinatorStudentList, StudentStatus, SubStatus } from "@/types/student";

const PAGE_SIZE = 10;

/** 관리 상태 필터 값: "EMPLOYED" · "SEEKING" · "SEEKING:APPLYING" … */
const FILTERS: { value: string; label: string }[] = [
  { value: "", label: "전체" },
  ...MANAGE_ORDER.flatMap((s) =>
    s === "SEEKING"
      ? [
          { value: s, label: manageLabels[s] },
          ...(Object.keys(subStatusLabels) as SubStatus[]).map((sub) => ({ value: `${s}:${sub}`, label: `　└ ${subStatusLabels[sub]}` })),
        ]
      : [{ value: s, label: manageLabels[s] }],
  ),
];

export default function StudentsScreen() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const statusValue = searchParams.get("status");
  const status = statusValue && Object.hasOwn(manageLabels, statusValue) ? (statusValue as StudentStatus) : undefined;
  const subStatusValue = searchParams.get("subStatus");
  const subStatus =
    status === "SEEKING" && subStatusValue && Object.hasOwn(subStatusLabels, subStatusValue) ? (subStatusValue as SubStatus) : undefined;
  const q = searchParams.get("q")?.trim() ?? "";
  const pageValue = Number(searchParams.get("page"));
  const page = Number.isSafeInteger(pageValue) && pageValue >= 1 ? pageValue : 1;

  const [summary, setSummary] = useState<Dashboard | null>(null);
  const [list, setList] = useState<CoordinatorStudentList | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let ignore = false;
    getDashboard()
      .then((d) => {
        if (!ignore) setSummary(d);
      })
      .catch(() => {
        // 요약을 못 받아도 목록은 보여 준다 (요약 칸만 숨김)
      });
    return () => {
      ignore = true;
    };
  }, [reloadKey]);

  useEffect(() => {
    let ignore = false;
    getCoordinatorStudents({ status, subStatus, q, page, size: PAGE_SIZE })
      .then((d) => {
        if (!ignore) {
          setList(d);
          setError(null);
        }
      })
      .catch((e: unknown) => {
        if (!ignore) setError(e instanceof ApiError ? e.message : "수료생 목록을 불러오지 못했어요.");
      });
    return () => {
      ignore = true;
    };
  }, [status, subStatus, q, page, reloadKey]);

  function changeQuery(changes: Record<string, string | undefined>) {
    const params = new URLSearchParams(searchParams.toString());
    for (const [key, value] of Object.entries(changes)) {
      if (!value || (key === "page" && value === "1")) params.delete(key);
      else params.set(key, value);
    }
    const query = params.toString();
    router.push(query ? `${pathname}?${query}` : pathname, { scroll: false });
  }
  const setFilter = (value: string) => {
    const [s, sub] = value.split(":");
    changeQuery({ status: s || undefined, subStatus: sub, page: "1" });
  };
  const filterValue = status ? (subStatus ? `${status}:${subStatus}` : status) : "";
  const counts = summary ? manageCounts(summary.statusSummary) : null;
  const lastPage = list ? Math.max(1, Math.ceil(list.total / list.size)) : 1;

  return (
    // 머리 · 필터 · 페이지 이동은 고정하고 표 영역만 안에서 스크롤 (상단 막대 60 + 위아래 여백 70)
    <div className="flex h-[calc(100vh-130px)] min-h-[560px] flex-col">
      <PageTitle title="수료생 관리" description="최근 응답과 요청을 먼저 보고, 필요한 다음 행동으로 이어가세요." />
      {counts && (
        <div role="group" aria-label="관리 상태 요약" className="mb-3 grid grid-cols-4 overflow-hidden rounded-[10px] border border-(--coord-line) bg-(--coord-surface)">
          {MANAGE_ORDER.map((s) => {
            const selected = status === s && !subStatus;
            return (
              <button
                key={s}
                type="button"
                aria-pressed={selected}
                onClick={() => setFilter(selected ? "" : s)}
                className={`flex items-center justify-between gap-2.5 border-r border-(--coord-line) px-[18px] py-[9px] text-left transition last:border-r-0 focus-visible:outline-3 focus-visible:-outline-offset-3 focus-visible:outline-(--coord-green) ${
                  selected ? "bg-(--coord-green-soft) shadow-[inset_0_-3px_0_var(--coord-green)]" : "hover:bg-(--coord-hover)"
                }`}
              >
                <Badge tone={manageTone[s]}>{manageLabels[s]}</Badge>
                <b className="text-lg leading-[1.2] tracking-[-0.5px]">
                  {counts[s]}
                  <small className="ml-[3px] text-[11px] font-medium text-(--coord-muted)">명</small>
                </b>
              </button>
            );
          })}
        </div>
      )}
      <Panel label="관리 중인 수료생" className="flex min-h-0 flex-1 flex-col">
        <PanelHead
          title={
            <>
              관리 중인 수료생 {summary && <span className="text-(--coord-green)">{summary.totalStudents}</span>}
            </>
          }
        >
          <span className="text-xs text-(--coord-muted)">이름을 누르면 추천 응답 · 지원서류 · 상담 기록이 열려요</span>
        </PanelHead>
        <div className="flex flex-wrap items-end gap-[9px] border-b border-(--coord-line) px-5 py-4">
          <form
            className="flex items-center gap-2"
            onSubmit={(event) => {
              event.preventDefault();
              changeQuery({ q: String(new FormData(event.currentTarget).get("q") ?? "").trim(), page: "1" });
            }}
          >
            <input
              key={q}
              name="q"
              type="search"
              defaultValue={q}
              aria-label="수료생 이름 검색"
              placeholder="수료생 이름 검색"
              className="w-[220px] rounded-lg border border-(--coord-line) bg-(--coord-surface) px-2.5 py-2 text-xs"
            />
            <button type="submit" aria-label="검색" className="grid size-9 place-items-center rounded-lg hover:bg-(--coord-hover)">
              <Icon name="search" />
            </button>
          </form>
          <label className="flex min-w-[180px] flex-col gap-0.5 text-[11px] font-semibold text-(--coord-muted)">
            관리 상태
            <select
              value={filterValue}
              onChange={(event) => setFilter(event.target.value)}
              className="rounded-lg border border-(--coord-line) bg-(--coord-surface) px-2.5 py-2 text-xs font-normal text-(--coord-ink)"
            >
              {FILTERS.map((f) => (
                <option key={f.value} value={f.value}>
                  {f.label}
                </option>
              ))}
            </select>
          </label>
          <button
            type="button"
            onClick={() => changeQuery({ status: undefined, subStatus: undefined, q: undefined, page: "1" })}
            className="py-2 text-xs font-semibold text-(--coord-green)"
          >
            초기화
          </button>
        </div>
        {!list ? (
          <LoadState
            error={error}
            loadingText="수료생 목록을 불러오는 중이에요…"
            onRetry={() => {
              setError(null);
              setReloadKey((k) => k + 1);
            }}
          />
        ) : (
          <>
            {error && (
              <p role="alert" className="px-5 pt-3 text-xs font-bold text-(--coord-red)">
                {error}
              </p>
            )}
            <div className="min-h-0 flex-1 overflow-auto">
            <table className="w-full table-fixed border-collapse text-left text-xs">
              <caption className="sr-only">수료생 목록: 수료생, 교육과정, 현재 상태, 최근 응답</caption>
              <thead className="sticky top-0 z-[1] bg-(--coord-head) text-[11px] text-(--coord-muted)">
                <tr>
                  {[
                    ["수료생", "w-[20%]"],
                    ["교육과정", "w-[25%]"],
                    ["현재 상태", "w-[23%]"],
                    ["최근 응답", "w-[32%]"],
                  ].map(([label, w]) => (
                    <th key={label} scope="col" className={`${w} border-b border-(--coord-line-soft) px-[17px] py-[15px] font-medium`}>
                      {label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {list.items.map((s) => (
                  <tr key={s.studentId} className="h-16 border-b border-(--coord-line-soft) hover:bg-(--coord-hover)">
                    <td className="overflow-hidden px-[17px]">
                      <Link
                        href={`/coordinator/students/${s.studentId}`}
                        aria-label={`${s.name} 상세 보기`}
                        className="flex items-center gap-2.5 font-semibold hover:text-(--coord-green)"
                      >
                        <Avatar name={s.name} />
                        <span className="truncate">{s.name}</span>
                      </Link>
                    </td>
                    <td className="truncate px-[17px]" title={s.courseLabel}>
                      {courseName(s.courseLabel)}
                    </td>
                    <td className="overflow-hidden px-[17px]">
                      <Badge tone={manageTone[s.status]}>{manageLabels[s.status]}</Badge>
                      {s.subStatus && <span className="mt-[3px] block truncate text-[10px] text-(--coord-muted)">{subStatusLabels[s.subStatus]}</span>}
                    </td>
                    <td className="overflow-hidden px-[17px]">
                      <b className={`text-xs ${s.status === "CHECK_REQUIRED" ? "text-(--coord-orange)" : "text-(--coord-green)"}`}>
                        {s.lastResponseAt ? `${monthDay(s.lastResponseAt)} 응답` : "응답 기록 없음"}
                      </b>
                      {s.hasNote && <span className="mt-[3px] block truncate text-[10px] text-(--coord-muted)">비고 있음</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {list.items.length === 0 && (
              <p className="p-[35px] text-center text-[13px] text-(--coord-muted)">조건에 맞는 수료생이 없어요. 필터를 초기화해 보세요.</p>
            )}
            </div>
            <div className="flex items-center justify-between border-t border-(--coord-line-soft) px-5 py-3.5 text-[11px] text-(--coord-muted)">
              <span role="status">
                {list.total}명 중 {list.total ? (page - 1) * list.size + 1 : 0}–{(page - 1) * list.size + list.items.length}명
              </span>
              <div className="flex items-center gap-3">
                <button
                  type="button"
                  disabled={page <= 1}
                  onClick={() => changeQuery({ page: String(page - 1) })}
                  className={`${btn.base} ${btn.outline} ${btn.sm}`}
                >
                  이전
                </button>
                <span>
                  {page} / {lastPage}
                </span>
                <button
                  type="button"
                  disabled={page >= lastPage}
                  onClick={() => changeQuery({ page: String(page + 1) })}
                  className={`${btn.base} ${btn.outline} ${btn.sm}`}
                >
                  다음
                </button>
              </div>
            </div>
          </>
        )}
      </Panel>
    </div>
  );
}
