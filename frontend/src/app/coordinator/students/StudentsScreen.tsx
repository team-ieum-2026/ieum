"use client";
// 주인: FE-B — C2 수료생 관리 (11/1 역할 조정으로 FE-B, 11/1 개편 목업 studentTable7).
// - 상단 요약: 취업 완료 · 구직 중 · 도움 필요 · 추가 확인 필요 (대시보드 statusSummary). 누르면 그 상태로 거른다
// - 목록 7열 (GET /api/coordinator/students, 한 페이지 10명): 수료생 / 교육과정 / 현재 상태 / 최근 응답 / 추천 공고 응답 / 도움 요청 / 서류
//   · 추천 공고 응답 · 서류: 목록 API 에 없어 지금 페이지 10명만 C3 상세 API 를 병렬로 불러 채운다 (실패하면 '조회 실패')
//   · 도움 요청: 대시보드 API 의 접수(OPEN) 목록을 수료생 번호로 맞춘다 (연락 완료 이력은 목록 API 가 없어 보여 주지 않음)
//   교육과정은 끝의 ' N기'를 떼어 표시
// - 필터: 이름 검색(q) · 관리 상태(status · subStatus)만. 지금 검색 조건은 C3 에서 돌아올 때 다시 쓰도록 sessionStorage 에 둔다
// - 필터를 바꿔도 열 너비 · 행 높이는 같게 (table-fixed + 고정 행 높이 + 줄임표)
// - 수료생 등록: 등록 API 가 없어 입력 폼만 있고 저장은 막아 둔다 (가짜 저장 없음)
import Link from "next/link";
import { useEffect, useId, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { apiErrorText, Badge, Icon, LoadState, PageTitle, Panel, PanelHead, btn } from "@/components/coordinator/CoordUi";
import {
  MANAGE_ORDER,
  courseName,
  docLabels,
  docTone,
  manageCounts,
  manageLabels,
  manageTone,
  monthDay,
  studentExtra,
  responseTagLabels,
  responseTone,
} from "@/components/coordinator/coordLabels";
import { subStatusLabels } from "@/components/student/labels";
import { getDashboard } from "@/lib/api/coordinatorReview";
import { getCoordinatorStudent, getCoordinatorStudents } from "@/lib/api/coordinatorStudents";
import type { CoordStudentStatus, Dashboard } from "@/types/coordinator";
import type { ApplicationStatus, CoordinatorStudentList, ResponseType, StudentStatus, SubStatus } from "@/types/student";

const PAGE_SIZE = 10;

/** C3 의 '수료생 목록으로'가 검색 조건 · 페이지를 되살릴 때 쓰는 키 */
export const STUDENTS_QUERY_KEY = "ieum:coord-students-query";

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

/** 목업 열 비율 12 / 15 / 14 / 19 / 15 / 13 / 12 */
const COLUMNS: [string, string][] = [
  ["수료생", "w-[12%]"],
  ["교육과정", "w-[15%]"],
  ["현재 상태", "w-[14%]"],
  ["최근 응답", "w-[19%]"],
  ["추천 공고 응답", "w-[15%]"],
  ["도움 요청", "w-[13%]"],
  ["서류", "w-[12%]"],
];

/** 한 수료생의 추천 응답 · 서류 (C3 상세 API 에서 뽑음). error: 그 수료생 상세 조회 실패 */
type RowExtra =
  | { state: "loading" }
  | { state: "error" }
  | {
      state: "ok";
      /** 가장 최근 추천의 응답별 개수 (추천 이력이 없으면 null) */
      responses: Partial<Record<ResponseType, number>> | null;
      docs: { applicationId: number; status: ApplicationStatus }[];
    };

export default function StudentsScreen() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const statusValue = searchParams.get("status");
  const status = statusValue && Object.hasOwn(manageLabels, statusValue) ? (statusValue as CoordStudentStatus) : undefined;
  const subStatusValue = searchParams.get("subStatus");
  const subStatus =
    status === "SEEKING" && subStatusValue && Object.hasOwn(subStatusLabels, subStatusValue) ? (subStatusValue as SubStatus) : undefined;
  const q = searchParams.get("q")?.trim() ?? "";
  const pageValue = Number(searchParams.get("page"));
  const page = Number.isSafeInteger(pageValue) && pageValue >= 1 ? pageValue : 1;

  const [summary, setSummary] = useState<Dashboard | null>(null);
  const [list, setList] = useState<CoordinatorStudentList | null>(null);
  const [extras, setExtras] = useState<Record<number, RowExtra>>({});
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [registering, setRegistering] = useState(false);

  // 지금 검색 조건을 기억해 C3 에서 '수료생 목록으로' 돌아올 때 되살린다
  useEffect(() => {
    try {
      sessionStorage.setItem(STUDENTS_QUERY_KEY, searchParams.toString());
    } catch {
      // 저장소를 못 쓰면 기본 목록으로 돌아간다
    }
  }, [searchParams]);

  useEffect(() => {
    let ignore = false;
    getDashboard()
      .then((d) => {
        if (!ignore) setSummary(d);
      })
      .catch(() => {
        // 요약을 못 받아도 목록은 보여 준다 (요약 칸 · 도움 요청 열만 '조회 실패')
      });
    return () => {
      ignore = true;
    };
  }, [reloadKey]);

  useEffect(() => {
    let ignore = false;
    // 목록 API 는 status=RESTING 을 지원한다 (docs/API.md). FE-A 쿼리 타입에 RESTING 이 아직 없어 여기서만 넘긴다
    getCoordinatorStudents({ status: status as StudentStatus | undefined, subStatus, q, page, size: PAGE_SIZE })
      .then((d) => {
        if (!ignore) {
          setList(d);
          setError(null);
        }
      })
      .catch((e: unknown) => {
        if (!ignore) setError(apiErrorText(e, "수료생 목록을 불러오지 못했어요."));
      });
    return () => {
      ignore = true;
    };
  }, [status, subStatus, q, page, reloadKey]);

  // 지금 페이지(최대 10명)만 상세를 병렬로 불러 추천 응답 · 서류 열을 채운다
  useEffect(() => {
    if (!list) return;
    let ignore = false;
    const ids = list.items.map((s) => s.studentId);
    Promise.allSettled(ids.map((id) => getCoordinatorStudent(id))).then((results) => {
      if (ignore) return;
      const next: Record<number, RowExtra> = {};
      results.forEach((r, i) => {
        if (r.status === "rejected") {
          next[ids[i]] = { state: "error" };
          return;
        }
        const latest = r.value.history[0];
        const responses = latest
          ? latest.items.reduce<Partial<Record<ResponseType, number>>>((acc, item) => ({ ...acc, [item.response]: (acc[item.response] ?? 0) + 1 }), {})
          : null;
        const docs = r.value.history
          .flatMap((h) => h.items)
          .filter((item) => item.applicationId && item.applicationStatus)
          .map((item) => ({ applicationId: item.applicationId as number, status: item.applicationStatus as ApplicationStatus }));
        next[ids[i]] = { state: "ok", responses, docs };
      });
      setExtras(next);
    });
    return () => {
      ignore = true;
    };
  }, [list]);

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
      <PageTitle
        title="수료생 관리"
        description="최근 응답과 요청을 먼저 보고, 필요한 다음 행동으로 이어가세요."
        action={
          <button type="button" onClick={() => setRegistering(true)} className={`${btn.base} ${btn.primary} px-4 py-3 text-sm`}>
            <span aria-hidden="true">＋</span> 수료생 등록
          </button>
        }
      />
      {counts && (
        <div role="group" aria-label="관리 상태 요약" className="mb-3 grid shrink-0 grid-cols-4 overflow-hidden rounded-[10px] border border-(--coord-line) bg-(--coord-surface)">
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
        <div className="flex shrink-0 flex-wrap items-end gap-[9px] border-b border-(--coord-line) px-5 py-3">
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
              <table className="w-full min-w-[1040px] table-fixed border-collapse text-left text-xs">
                <caption className="sr-only">수료생 목록: 수료생, 교육과정, 현재 상태, 최근 응답, 추천 공고 응답, 도움 요청, 서류</caption>
                <thead className="sticky top-0 z-[1] bg-(--coord-head) text-[11px] text-(--coord-muted)">
                  <tr>
                    {COLUMNS.map(([label, w]) => (
                      <th key={label} scope="col" className={`${w} border-b border-(--coord-line-soft) px-[17px] py-3 font-medium`}>
                        {label}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {list.items.map((s) => {
                    const extra = extras[s.studentId] ?? { state: "loading" };
                    const help = summary?.helpRequests.filter((h) => h.studentId === s.studentId) ?? null;
                    // 11/2 추가 값: 서버 openHelpRequestCount 우선 (없는 이전 응답은 대시보드 helpRequests 로 센다) · 연속 패스 연락 대상은 서버 판정 그대로
                    const ext = studentExtra(s);
                    const helpCount = ext.openHelpRequestCount ?? help?.length ?? null;
                    return (
                      <tr key={s.studentId} className="h-16 border-b border-(--coord-line-soft) hover:bg-(--coord-hover)">
                        <td className="overflow-hidden px-[17px]">
                          <Link
                            href={`/coordinator/students/${s.studentId}`}
                            className="block truncate text-[13px] font-bold underline-offset-4 hover:text-(--coord-green) hover:underline focus-visible:outline-2 focus-visible:outline-(--coord-green)"
                          >
                            {s.name}
                          </Link>
                        </td>
                        <td className="px-[17px] text-(--coord-ink)" title={s.courseLabel}>
                          {/* 과정 이름이 길어 두 줄까지 보인다 (행 높이는 그대로) */}
                          <span className="line-clamp-2 leading-[1.45]">{courseName(s.courseLabel)}</span>
                        </td>
                        <td className="overflow-hidden px-[17px]">
                          <Badge tone={manageTone[s.status]}>{manageLabels[s.status]}</Badge>
                          {ext.needsPassFollowUp ? (
                            <span className="mt-[3px] block">
                              <Badge tone="blue">연락 필요 · 연속 패스 {ext.consecutivePassCount ?? "—"}회</Badge>
                            </span>
                          ) : (
                            s.subStatus && <span className="mt-[3px] block truncate text-[10px] text-(--coord-muted)">{subStatusLabels[s.subStatus]}</span>
                          )}
                        </td>
                        <td className="overflow-hidden px-[17px]">
                          <b className={`text-xs ${s.status === "CHECK_REQUIRED" ? "text-(--coord-orange)" : "text-(--coord-green)"}`}>
                            {s.lastResponseAt ? `${monthDay(s.lastResponseAt)} 응답` : "응답 기록 없음"}
                          </b>
                          {s.hasNote && <span className="mt-[3px] block truncate text-[10px] text-(--coord-muted)">비고 있음</span>}
                        </td>
                        <td className="overflow-hidden px-[17px]">
                          <ResponseCell extra={extra} />
                        </td>
                        <td className="overflow-hidden px-[17px]">
                          {helpCount === null ? (
                            <span className="text-(--coord-muted)">조회 실패</span>
                          ) : helpCount > 0 ? (
                            <Link
                              href={help?.[0] ? `/coordinator/help-requests?id=${help[0].helpRequestId}` : "/coordinator/help-requests"}
                              className="font-semibold whitespace-nowrap text-(--coord-green) hover:underline"
                            >
                              접수 {helpCount}건
                            </Link>
                          ) : (
                            <span className="text-(--coord-muted)">접수 없음</span>
                          )}
                        </td>
                        <td className="overflow-hidden px-[17px]">
                          <DocCell extra={extra} />
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
              {list.items.length === 0 && (
                <p className="p-[35px] text-center text-[13px] text-(--coord-muted)">조건에 맞는 수료생이 없어요. 필터를 초기화해 보세요.</p>
              )}
            </div>
            <div className="flex shrink-0 items-center justify-between border-t border-(--coord-line-soft) px-5 py-3 text-[11px] text-(--coord-muted)">
              <span role="status">
                {list.total}명 중 {list.total ? (page - 1) * list.size + 1 : 0}–{(page - 1) * list.size + list.items.length}명
                <span className="ml-3">도움 요청 열은 접수 중인 요청만 보여 줘요</span>
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
      {registering && <RegisterModal onClose={() => setRegistering(false)} />}
    </div>
  );
}

const RESPONSE_ORDER: ResponseType[] = ["APPLY", "PASS", "HELP", "NONE"];

/** 가장 최근 추천의 응답 개수 칩 (목업: 지원 1 · 패스 1 · 미응답 1) */
function ResponseCell({ extra }: { extra: RowExtra }) {
  if (extra.state === "loading") return <span className="text-(--coord-muted)">불러오는 중…</span>;
  if (extra.state === "error") return <span className="text-(--coord-muted)">조회 실패</span>;
  if (!extra.responses) return <span className="text-(--coord-muted)">추천 없음</span>;
  const responses = extra.responses;
  return (
    // 칩이 많으면 두 줄까지 (행 높이 64px 안)
    <span className="flex max-h-[52px] flex-wrap gap-1 overflow-hidden">
      {RESPONSE_ORDER.filter((r) => responses[r]).map((r) => (
        <Badge key={r} tone={responseTone[r]}>
          {responseTagLabels[r]} {responses[r]}
        </Badge>
      ))}
    </span>
  );
}

/** 가장 최근 지원서 상태 (여러 건이면 +N) */
function DocCell({ extra }: { extra: RowExtra }) {
  if (extra.state === "loading") return <span className="text-(--coord-muted)">불러오는 중…</span>;
  if (extra.state === "error") return <span className="text-(--coord-muted)">조회 실패</span>;
  const [first, ...rest] = extra.docs;
  if (!first) return <span className="text-(--coord-muted)">제출 없음</span>;
  return (
    <Link href={`/coordinator/applications/${first.applicationId}`} className="inline-flex items-center gap-1 hover:brightness-95">
      <Badge tone={docTone[first.status]}>{docLabels[first.status]}</Badge>
      {rest.length > 0 && <span className="text-[10px] text-(--coord-muted)">+{rest.length}</span>}
    </Link>
  );
}

/**
 * 수료생 등록 폼 (목업 registerModal). 수료생 등록 API 가 아직 없어서 입력만 받고 저장은 막는다.
 * API 가 생기면 onSubmit 에서 호출하고 목록을 다시 불러오면 된다 (가짜 저장 없음)
 */
function RegisterModal({ onClose }: { onClose: () => void }) {
  const titleId = useId();
  const panelRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    panelRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);
  const fields: { name: string; label: string; type?: string; required?: boolean }[] = [
    { name: "name", label: "이름", required: true },
    { name: "email", label: "이메일", type: "email", required: true },
    { name: "course", label: "교육과정", required: true },
    { name: "completedOn", label: "수료일", type: "date", required: true },
    { name: "desiredJob", label: "희망 직무" },
    { name: "preferredRegion", label: "희망 지역" },
  ];
  return (
    <div className="fixed inset-0 z-30 flex items-center justify-center bg-(--coord-forest)/40 p-6 backdrop-blur-[3px]" onClick={onClose}>
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onClick={(e) => e.stopPropagation()}
        className="w-[600px] overflow-hidden rounded-[14px] border border-(--coord-line) bg-(--coord-surface) outline-none"
      >
        <div className="flex items-center justify-between gap-3 border-b border-(--coord-line-soft) px-6 py-4">
          <h2 id={titleId} className="text-[19px] font-bold">
            수료생 등록
          </h2>
          <button type="button" onClick={onClose} aria-label="닫기" className="grid size-9 place-items-center rounded-lg hover:bg-(--coord-hover)">
            <Icon name="close" />
          </button>
        </div>
        <form className="p-6" onSubmit={(e) => e.preventDefault()}>
          <p role="note" className="mb-4 rounded-lg border border-(--coord-attention-line) bg-(--coord-attention-soft) px-3.5 py-2.5 text-xs leading-[1.7] text-(--coord-attention)">
            <b>등록 API 미연동</b> · 수료생 등록 기능이 아직 서버에 없어서 입력한 내용은 저장되지 않아요. 지금은 시드 데이터로 등록된 수료생만 관리할 수 있어요.
          </p>
          <div className="grid grid-cols-2 gap-[15px]">
            {fields.map((f) => (
              <label key={f.name} className="flex flex-col gap-1.5 text-xs">
                <span className="font-semibold">
                  {f.label}
                  {f.required && <span className="ml-0.5 text-(--coord-red)">*</span>}
                </span>
                <input name={f.name} type={f.type ?? "text"} required={f.required} className="rounded-lg border border-(--coord-line) bg-(--coord-surface) px-3 py-2.5 text-[13px]" />
              </label>
            ))}
          </div>
          <div className="mt-6 flex justify-end gap-2">
            <button type="button" onClick={onClose} className={`${btn.base} ${btn.outline}`}>
              닫기
            </button>
            <button type="submit" disabled aria-disabled="true" title="등록 API 미연동" className={`${btn.base} ${btn.primary}`}>
              등록 (API 미연동)
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
