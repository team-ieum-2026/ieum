"use client";
// 주인: FE-B — 취업·지원 현황 (11/1 개편 목업 statsPage). 대시보드 API 한 번 + 수료생 목록 API(C2 와 같은 함수)로만 그린다.
// ① 수료생 관리 현황(도넛 + 범례 3줄) ② 취업 지표(링 3개). '취업률'은 관리 상태 기준이며 공식 취업 성과가 아니다.
// ③ 도움 요청 통계: 대시보드 helpRequests 는 접수(OPEN)만 오므로 '접수 중 요청 기준'으로만 센다.
//    처리 중 · 상담 예정 · 연락 완료 · 요청 종료 건수와 필요한 도움(요청 유형)은 API 에 없어 '미연동' (공고 · 메모로 추측 분류하지 않음)
// ④ 수료생 통합 현황: C2 목록 API(검색 · 관리 상태 · 페이지) + 지금 페이지만 C3 상세로 지원서류를 채운다. 도움 요청은 접수 중 건수
// ⑤ CSV: 현재 페이지 / 전체 명단(지금 검색 조건, 100명씩 끝까지 받아 studentId 로 중복 제거). 목록 API 값 + 접수 중 도움 요청 수만 담는다
import Link from "next/link";
import { useEffect, useState } from "react";
import { Badge, Icon, LoadState, PageTitle, Panel, PanelHead, btn, toneClass } from "@/components/coordinator/CoordUi";
import {
  MANAGE_ORDER,
  contactLabels,
  courseName,
  docLabels,
  docTone,
  helpLabels,
  helpTone,
  knownCount,
  manageChartVar,
  manageCounts,
  manageLabels,
  manageTone,
  monthDay,
  pct,
} from "@/components/coordinator/coordLabels";
import { subStatusLabels } from "@/components/student/labels";
import { ApiError } from "@/lib/api/client";
import { getDashboard } from "@/lib/api/coordinatorReview";
import { getCoordinatorStudent, getCoordinatorStudents } from "@/lib/api/coordinatorStudents";
import type { Dashboard } from "@/types/coordinator";
import type { ApplicationStatus, ContactMethod, CoordinatorStudentList, CoordinatorStudentRow, StudentStatus, SubStatus } from "@/types/student";

const R = 54;
const CIRC = 2 * Math.PI * R;
const DONUT_ORDER: StudentStatus[] = ["EMPLOYED", "SEEKING", "NEED_HELP", "CHECK_REQUIRED"];

export default function StatsScreen() {
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let ignore = false;
    getDashboard()
      .then((d) => {
        if (!ignore) setData(d);
      })
      .catch((e: unknown) => {
        if (!ignore) setError(e instanceof ApiError ? e.message : "현황을 불러오지 못했어요.");
      });
    return () => {
      ignore = true;
    };
  }, [reloadKey]);

  return (
    <>
      <PageTitle title="취업·지원 현황" description="관리 상태 분포와 취업 지표를 한눈에 살펴보세요." />
      {data ? (
        <Body data={data} />
      ) : (
        <LoadState
          error={error}
          loadingText="현황을 불러오는 중이에요…"
          onRetry={() => {
            setError(null);
            setReloadKey((k) => k + 1);
          }}
        />
      )}
    </>
  );
}

function Body({ data }: { data: Dashboard }) {
  const total = data.totalStudents;
  const s = data.statusSummary;
  const counts = manageCounts(s);
  const known = knownCount(total, s);
  const emp = s.EMPLOYED;
  const seeking = s.SEEKING.total + s.NEED_HELP;

  // 각 조각의 길이와, 앞 조각들 길이의 합(시작 위치)
  const lens = DONUT_ORDER.map((k) => (total ? (counts[k] / total) * CIRC : 0));
  const arcs = DONUT_ORDER.map((k, i) => ({
    k,
    dash: Math.max(0, lens[i] - (counts[k] && lens[i] > 4 ? 2 : 0)),
    offset: -lens.slice(0, i).reduce((sum, len) => sum + len, 0),
  })).filter((a) => counts[a.k] > 0);

  const legend = [
    { label: "취업 완료", color: manageChartVar.EMPLOYED, n: emp, note: <>패스 사유 &lsquo;이미 취업&rsquo; 응답 기준</> },
    {
      label: "구직 중",
      color: manageChartVar.SEEKING,
      n: seeking,
      note: (
        <>
          <i className="inline-block size-[7px] shrink-0 rounded-full" style={{ background: manageChartVar.NEED_HELP }} />이 중 도움 필요{" "}
          {s.NEED_HELP}명
        </>
      ),
    },
    { label: "추가 확인 필요", color: manageChartVar.CHECK_REQUIRED, n: s.CHECK_REQUIRED, note: <>1주 넘게 앱 응답 없음 · 전화 확인</> },
  ];

  const rings = [
    { label: "관리 상태 파악률", a: known, b: total, color: "var(--coord-chart-seeking)", note: `상태가 분류된 ${known}명 ÷ 전체 ${total}명` },
    { label: "취업률", a: emp, b: known, color: "var(--coord-chart-employed)", note: `취업 완료 ${emp}명 ÷ 상태가 분류된 ${known}명` },
    { label: "전체 기준 취업률", a: emp, b: total, color: "var(--coord-chart-total)", note: `취업 완료 ${emp}명 ÷ 전체 ${total}명 · 최소치` },
  ];

  return (
    <>
      <div className="grid grid-cols-2 gap-3">
        <Panel label="수료생 관리 현황" className="px-5 py-4">
          <div className="mb-3 flex items-baseline justify-between gap-3">
            <h2 className="text-[15px] font-bold">수료생 관리 현황</h2>
            <span className="text-xs text-(--coord-muted)">현재 등록 인원 {total}명</span>
          </div>
          <div className="flex flex-wrap items-center gap-[22px]">
            <div className="relative size-[150px] flex-none" role="img" aria-label={`관리 상태 분포: ${legend.map((l) => `${l.label} ${l.n}명`).join(", ")}`}>
              <svg viewBox="0 0 140 140" aria-hidden="true" className="block size-full">
                {arcs.map((a) => (
                  <circle
                    key={a.k}
                    cx="70"
                    cy="70"
                    r={R}
                    fill="none"
                    stroke={manageChartVar[a.k]}
                    strokeWidth="18"
                    strokeDasharray={`${a.dash.toFixed(2)} ${(CIRC - a.dash).toFixed(2)}`}
                    strokeDashoffset={a.offset.toFixed(2)}
                    transform="rotate(-90 70 70)"
                  />
                ))}
              </svg>
              <div className="absolute inset-0 flex flex-col items-center justify-center">
                <b className="text-[30px] leading-[1.1] text-(--coord-forest)">
                  {total}
                  <small className="ml-0.5 text-sm font-bold">명</small>
                </b>
                <span className="text-[11px] text-(--coord-muted)">관리 중인 수료생</span>
              </div>
            </div>
            <ul className="min-w-0 flex-1">
              {legend.map((l) => (
                <li
                  key={l.label}
                  className="grid grid-cols-[10px_minmax(0,1fr)_auto_54px] items-center gap-2 border-b border-(--coord-line) py-2.5 first:pt-0 last:border-b-0 last:pb-0"
                >
                  <i className="block size-2.5 rounded-[3px]" style={{ background: l.color }} />
                  <div>
                    <b className="block text-[13px] leading-[1.3]">{l.label}</b>
                    <small className="flex items-center gap-[5px] text-[11px] leading-[1.4] text-(--coord-muted)">{l.note}</small>
                  </div>
                  <strong className="text-right text-xl leading-[1.2] text-(--coord-forest)">{l.n}명</strong>
                  <em className="text-right text-xs text-(--coord-muted) not-italic">{pct(l.n, total)}%</em>
                </li>
              ))}
            </ul>
          </div>
        </Panel>
        <Panel label="취업 지표" className="px-5 py-4">
          <div className="mb-3 flex items-baseline justify-between gap-3">
            <h2 className="text-[15px] font-bold">취업 지표</h2>
            <span className="text-xs text-(--coord-muted)">관리 상태 기준</span>
          </div>
          <div className="grid grid-cols-3 gap-2.5 text-center">
            {rings.map((r) => (
              <div key={r.label} className="flex flex-col items-center gap-[5px]">
                <div
                  className="relative grid size-[98px] place-items-center rounded-full"
                  style={{ background: `conic-gradient(${r.color} ${pct(r.a, r.b)}%, var(--coord-chart-track) 0)` }}
                >
                  <span className="absolute inset-[10px] rounded-full bg-(--coord-surface)" />
                  <b className="relative text-base">{pct(r.a, r.b)}%</b>
                </div>
                <b className="text-[12.5px] leading-[1.3]">{r.label}</b>
                <small className="text-[11px] leading-[1.45] break-keep text-(--coord-muted)">{r.note}</small>
              </div>
            ))}
          </div>
          <p className="mt-3 text-[11px] text-(--coord-muted)">취업률은 관리 상태 기준이며 공식 취업 성과가 아니에요.</p>
        </Panel>
      </div>
      <details className="mt-3 rounded-lg border border-(--coord-tint-line) bg-(--coord-tint) px-3.5 py-2.5 text-xs leading-[1.7]">
        <summary className="cursor-pointer font-semibold">관리 상태와 집계 기준</summary>
        <p className="mt-2">
          관리 상태는 수료생 앱 응답과 도움 요청으로 서버가 분류해요. 1주 넘게 응답이 없는 수료생은 &lsquo;추가 확인 필요&rsquo;로 따로 세요. &lsquo;구직
          중&rsquo;은 운영상의 분류이며 미취업을 확정하는 값은 아니에요.
        </p>
        <p className="mt-2">이 화면의 &lsquo;구직 중&rsquo;은 도움 필요를 포함한 수예요.</p>
        <p className="mt-2">
          관리 상태 파악률 = (전체 − 추가 확인 필요) ÷ 전체 · 취업률 = 취업 완료 ÷ (전체 − 추가 확인 필요) · 전체 기준 취업률 = 취업 완료 ÷ 전체.
          취업률은 관리 상태 기준이며 공식 취업 성과가 아니에요.
        </p>
      </details>
      <HelpStats data={data} />
      <StudentRoster data={data} />
    </>
  );
}

// ---- ③ 도움 요청 통계 ----

/** 목업 처리 상태. 서버 목록은 접수(OPEN)만 있다 */
const HELP_STATES: { label: string; connected: boolean }[] = [
  { label: helpLabels.OPEN, connected: true },
  { label: "처리 중", connected: false },
  { label: "상담 예정", connected: false },
  { label: helpLabels.CONTACTED, connected: false },
  { label: "요청 종료", connected: false },
];
const CONTACT_ORDER: (ContactMethod | "NONE")[] = ["PHONE", "SMS", "EMAIL", "NONE"];
const HELP_TOPICS = ["이력서 첨삭", "공고 같이 보기", "면접 준비", "진로 상담", "기타"];

function HelpStats({ data }: { data: Dashboard }) {
  const open = data.helpRequests;
  const byContact = CONTACT_ORDER.map((m) => ({
    m,
    n: open.filter((h) => (m === "NONE" ? h.contactMethod === null : h.contactMethod === m)).length,
  }));
  return (
    <Panel label="도움 요청 통계" className="mt-3">
      <PanelHead title="도움 요청 통계" sub="접수 중인 요청만 집계해요. 연락 완료 · 종료된 요청은 목록 API 가 없어 포함되지 않아요.">
        <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${toneClass("red")}`}>접수 중 요청 {open.length}건 기준</span>
      </PanelHead>
      <div className="grid grid-cols-3 divide-x divide-(--coord-line-soft)">
        <StatColumn title="처리 상태별">
          {HELP_STATES.map((s) =>
            s.connected ? (
              <StatRow key={s.label} label={<Badge tone={helpTone.OPEN}>{s.label}</Badge>} n={open.length} total={open.length} />
            ) : (
              <StatRow key={s.label} label={<span className="text-(--coord-muted)">{s.label}</span>} unlinked="상태별 목록 API 가 없어요" />
            ),
          )}
        </StatColumn>
        <StatColumn title="선호 연락별" note={`접수 중 ${open.length}건 중`}>
          {byContact.map(({ m, n }) => (
            <StatRow key={m} label={m === "NONE" ? "선호 연락 선택 안 함" : contactLabels[m]} n={n} total={open.length} />
          ))}
        </StatColumn>
        <StatColumn title="필요한 도움별" note="요청 유형 필드 없음">
          {HELP_TOPICS.map((t) => (
            <StatRow key={t} label={<span className="text-(--coord-muted)">{t}</span>} unlinked="도움 요청에 요청 유형 값이 없어 분류하지 않아요" />
          ))}
        </StatColumn>
      </div>
    </Panel>
  );
}

function StatColumn({ title, note, children }: { title: string; note?: string; children: React.ReactNode }) {
  return (
    <div className="px-5 py-3.5">
      <div className="mb-2 flex items-baseline justify-between gap-2">
        <h3 className="text-[13px] font-bold">{title}</h3>
        {note && <span className="text-[11px] text-(--coord-muted)">{note}</span>}
      </div>
      <ul className="flex flex-col gap-1.5">{children}</ul>
    </div>
  );
}

/** 통계 한 줄: 이름 · 막대 · 건수. unlinked 이면 '— · 미연동' */
function StatRow({ label, n = 0, total = 0, unlinked }: { label: React.ReactNode; n?: number; total?: number; unlinked?: string }) {
  return (
    <li className="grid grid-cols-[110px_minmax(0,1fr)_64px] items-center gap-2.5 text-xs">
      <span className="truncate">{label}</span>
      {/* 미연동은 막대를 그리지 않는다 (빈 막대가 0건처럼 보이지 않게) */}
      {unlinked ? (
        <span />
      ) : (
        <span className="h-1.5 overflow-hidden rounded-full bg-(--coord-chart-track)">
          <span className="block h-full rounded-full bg-(--coord-chart-total)" style={{ width: `${pct(n, total)}%` }} />
        </span>
      )}
      {unlinked ? (
        <span title={unlinked} className="text-right text-[11px] text-(--coord-muted)">
          — · 미연동
        </span>
      ) : (
        <b className="text-right text-[13px]">
          {n}
          <small className="ml-0.5 text-[11px] font-medium text-(--coord-muted)">건</small>
        </b>
      )}
    </li>
  );
}

// ---- ④ 수료생 통합 현황 ----

const PAGE_SIZE = 10;
/** 목록 API 가 허용하는 가장 큰 페이지 크기 (BE: size ≤ 100) */
const EXPORT_PAGE_SIZE = 100;

/** 관리 상태 필터 값: "EMPLOYED" · "SEEKING" · "SEEKING:APPLYING" … (C2 와 같은 값) */
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

const ROSTER_COLUMNS: [string, string][] = [
  ["수료생", "w-[13%]"],
  ["교육과정", "w-[22%]"],
  ["관리 상태", "w-[16%]"],
  ["최근 응답 · 활동", "w-[17%]"],
  ["도움 요청", "w-[14%]"],
  ["지원서류", "w-[18%]"],
];

/** 한 수료생의 지원서류 (C3 상세 API) */
type DocExtra = { state: "error" } | { state: "ok"; docs: { applicationId: number; status: ApplicationStatus }[] };

type Query = { status?: StudentStatus; subStatus?: SubStatus; q: string; page: number };

function StudentRoster({ data }: { data: Dashboard }) {
  const [query, setQuery] = useState<Query>({ q: "", page: 1 });
  const [list, setList] = useState<CoordinatorStudentList | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [docs, setDocs] = useState<Record<number, DocExtra>>({});
  const [exporting, setExporting] = useState(false);
  const [exportMessage, setExportMessage] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;
    getCoordinatorStudents({ status: query.status, subStatus: query.subStatus, q: query.q, page: query.page, size: PAGE_SIZE })
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
  }, [query, reloadKey]);

  // 지금 페이지에서 아직 안 불러온 수료생만 상세를 불러 지원서류 열을 채운다 (페이지를 오가도 다시 부르지 않음)
  useEffect(() => {
    if (!list) return;
    const ids = list.items.map((s) => s.studentId).filter((id) => !docs[id]);
    if (ids.length === 0) return;
    let ignore = false;
    Promise.allSettled(ids.map((id) => getCoordinatorStudent(id))).then((results) => {
      if (ignore) return;
      const next: Record<number, DocExtra> = {};
      results.forEach((r, i) => {
        next[ids[i]] =
          r.status === "rejected"
            ? { state: "error" }
            : {
                state: "ok",
                docs: r.value.history
                  .flatMap((h) => h.items)
                  .filter((item) => item.applicationId && item.applicationStatus)
                  .map((item) => ({ applicationId: item.applicationId as number, status: item.applicationStatus as ApplicationStatus })),
              };
      });
      setDocs((m) => ({ ...m, ...next }));
    });
    return () => {
      ignore = true;
    };
  }, [list, docs]);

  const openHelp = (studentId: number) => data.helpRequests.filter((h) => h.studentId === studentId);
  const filterValue = query.status ? (query.subStatus ? `${query.status}:${query.subStatus}` : query.status) : "";
  const lastPage = list ? Math.max(1, Math.ceil(list.total / list.size)) : 1;
  const conditionLabel = [FILTERS.find((f) => f.value === filterValue)?.label.replace("　└ ", "") ?? "전체", query.q && `"${query.q}"`]
    .filter(Boolean)
    .join(" · ");

  const exportAll = async () => {
    setExporting(true);
    setExportMessage(null);
    try {
      // 100명씩 끝까지 받는다. 받는 중 인원이 바뀌어도 studentId 로 중복을 빼고, 빈 페이지가 오면 멈춘다
      const rows = new Map<number, CoordinatorStudentRow>();
      let total = 0;
      for (let page = 1; ; page++) {
        const d = await getCoordinatorStudents({ status: query.status, subStatus: query.subStatus, q: query.q, page, size: EXPORT_PAGE_SIZE });
        total = d.total;
        d.items.forEach((s) => rows.set(s.studentId, s));
        if (d.items.length === 0 || page * EXPORT_PAGE_SIZE >= d.total) break;
      }
      const all = [...rows.values()];
      downloadCsv(all, openHelp, `수료생_전체명단_${today()}.csv`);
      setExportMessage(
        all.length === total ? `전체 명단 ${all.length}명을 내려받았어요.` : `서버 인원 ${total}명 중 ${all.length}명을 받았어요. 받는 중 명단이 바뀌었을 수 있어요.`,
      );
    } catch (e) {
      setExportMessage(e instanceof ApiError ? e.message : "전체 명단을 받지 못했어요. 파일은 만들지 않았어요.");
    } finally {
      setExporting(false);
    }
  };

  return (
    <Panel label="수료생 통합 현황" className="mt-3 flex h-[560px] flex-col">
      <PanelHead title="수료생 통합 현황" sub="관리 상태 · 최근 응답 · 접수 중 도움 요청 · 지원서류를 한 줄로 봐요. 이름을 누르면 수료생 상세로 이동해요.">
        <div className="flex items-center gap-2">
          <button
            type="button"
            disabled={!list || list.items.length === 0}
            onClick={() => list && downloadCsv(list.items, openHelp, `수료생_명단_${query.page}쪽_${today()}.csv`)}
            className={`${btn.base} ${btn.outline} ${btn.sm}`}
          >
            현재 페이지 CSV
          </button>
          <button type="button" disabled={exporting || !list} onClick={exportAll} aria-busy={exporting} className={`${btn.base} ${btn.primary} ${btn.sm}`}>
            {exporting ? "받는 중…" : `전체 명단 CSV (${conditionLabel})`}
          </button>
        </div>
      </PanelHead>
      <div className="flex shrink-0 flex-wrap items-end gap-[9px] border-b border-(--coord-line) px-5 py-3">
        <form
          className="flex items-center gap-2"
          onSubmit={(event) => {
            event.preventDefault();
            const q = String(new FormData(event.currentTarget).get("q") ?? "").trim();
            setQuery((cur) => ({ ...cur, q, page: 1 }));
          }}
        >
          <input
            key={query.q}
            name="q"
            type="search"
            defaultValue={query.q}
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
            onChange={(event) => {
              const [s, sub] = event.target.value.split(":");
              setQuery((cur) => ({ ...cur, status: (s || undefined) as StudentStatus | undefined, subStatus: sub as SubStatus | undefined, page: 1 }));
            }}
            className="rounded-lg border border-(--coord-line) bg-(--coord-surface) px-2.5 py-2 text-xs font-normal text-(--coord-ink)"
          >
            {FILTERS.map((f) => (
              <option key={f.value} value={f.value}>
                {f.label}
              </option>
            ))}
          </select>
        </label>
        <button type="button" onClick={() => setQuery({ q: "", page: 1 })} className="py-2 text-xs font-semibold text-(--coord-green)">
          초기화
        </button>
        {exportMessage && (
          <p role="status" className="ml-auto self-center text-[11px] text-(--coord-muted)">
            {exportMessage}
          </p>
        )}
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
              <caption className="sr-only">수료생 통합 현황: 수료생, 교육과정, 관리 상태, 최근 응답 · 활동, 도움 요청, 지원서류</caption>
              <thead className="sticky top-0 z-[1] bg-(--coord-head) text-[11px] text-(--coord-muted)">
                <tr>
                  {ROSTER_COLUMNS.map(([label, w]) => (
                    <th key={label} scope="col" className={`${w} border-b border-(--coord-line-soft) px-[17px] py-2.5 font-medium`}>
                      {label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {list.items.map((s) => {
                  const help = openHelp(s.studentId);
                  return (
                    <tr key={s.studentId} className="h-[54px] border-b border-(--coord-line-soft) hover:bg-(--coord-hover)">
                      <td className="overflow-hidden px-[17px]">
                        <Link
                          href={`/coordinator/students/${s.studentId}`}
                          className="block truncate text-[13px] font-bold underline-offset-4 hover:text-(--coord-green) hover:underline"
                        >
                          {s.name}
                        </Link>
                      </td>
                      <td className="px-[17px]" title={s.courseLabel}>
                        <span className="line-clamp-2 leading-[1.45]">{courseName(s.courseLabel)}</span>
                      </td>
                      <td className="overflow-hidden px-[17px]">
                        <Badge tone={manageTone[s.status]}>{manageLabels[s.status]}</Badge>
                        {s.subStatus && <span className="ml-1.5 text-[10px] text-(--coord-muted)">{subStatusLabels[s.subStatus]}</span>}
                      </td>
                      <td className="overflow-hidden px-[17px]">
                        <b className={s.status === "CHECK_REQUIRED" ? "text-(--coord-orange)" : "text-(--coord-green)"}>
                          {s.lastResponseAt ? `${monthDay(s.lastResponseAt)} 응답` : "응답 기록 없음"}
                        </b>
                        {s.hasNote && <span className="ml-1.5 text-[10px] text-(--coord-muted)">비고 있음</span>}
                      </td>
                      <td className="overflow-hidden px-[17px]">
                        {help.length > 0 ? (
                          <Link href={`/coordinator/help-requests?id=${help[0].helpRequestId}`} className="font-semibold text-(--coord-green) hover:underline">
                            접수 {help.length}건
                          </Link>
                        ) : (
                          <span className="text-(--coord-muted)">접수 없음</span>
                        )}
                      </td>
                      <td className="overflow-hidden px-[17px]">
                        <DocCell extra={docs[s.studentId]} />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            {list.items.length === 0 && <p className="p-[35px] text-center text-[13px] text-(--coord-muted)">조건에 맞는 수료생이 없어요.</p>}
          </div>
          <div className="flex shrink-0 items-center justify-between border-t border-(--coord-line-soft) px-5 py-2.5 text-[11px] text-(--coord-muted)">
            <span role="status">
              {list.total}명 중 {list.total ? (query.page - 1) * list.size + 1 : 0}–{(query.page - 1) * list.size + list.items.length}명
              <span className="ml-3">도움 요청 열은 접수 중인 요청만 보여 줘요</span>
            </span>
            <div className="flex items-center gap-3">
              <button
                type="button"
                disabled={query.page <= 1}
                onClick={() => setQuery((cur) => ({ ...cur, page: cur.page - 1 }))}
                className={`${btn.base} ${btn.outline} ${btn.sm}`}
              >
                이전
              </button>
              <span>
                {query.page} / {lastPage}
              </span>
              <button
                type="button"
                disabled={query.page >= lastPage}
                onClick={() => setQuery((cur) => ({ ...cur, page: cur.page + 1 }))}
                className={`${btn.base} ${btn.outline} ${btn.sm}`}
              >
                다음
              </button>
            </div>
          </div>
        </>
      )}
    </Panel>
  );
}

/** 가장 최근 지원서 상태 (여러 건이면 +N) */
function DocCell({ extra }: { extra: DocExtra | undefined }) {
  if (!extra) return <span className="text-(--coord-muted)">불러오는 중…</span>;
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

// ---- ⑤ CSV ----

const today = () => {
  const d = new Date();
  return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, "0")}${String(d.getDate()).padStart(2, "0")}`;
};

/** 목록 API 값 + 접수 중 도움 요청 수만 담는다. 엑셀에서 한글이 깨지지 않게 BOM 을 붙인다 */
function downloadCsv(rows: CoordinatorStudentRow[], openHelp: (studentId: number) => unknown[], fileName: string) {
  const cell = (v: string | number) => {
    const s = String(v);
    // 쉼표 · 따옴표 · 줄바꿈은 따옴표로 감싸고, 수식으로 읽히는 첫 글자는 막는다
    const safe = /^[=+\-@]/.test(s) ? `'${s}` : s;
    return /[",\n\r]/.test(safe) ? `"${safe.replace(/"/g, '""')}"` : safe;
  };
  const header = ["이름", "교육과정", "수료일", "관리 상태", "세부 상태", "최근 응답일", "비고 있음", "접수 중 도움 요청"];
  const lines = rows.map((s) =>
    [
      s.name,
      s.courseLabel,
      s.completedOn,
      manageLabels[s.status],
      s.subStatus ? subStatusLabels[s.subStatus] : "",
      s.lastResponseAt ? s.lastResponseAt.slice(0, 10) : "",
      s.hasNote ? "예" : "아니요",
      openHelp(s.studentId).length,
    ]
      .map(cell)
      .join(","),
  );
  const blob = new Blob(["﻿" + [header.join(","), ...lines].join("\r\n")], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = fileName;
  a.click();
  URL.revokeObjectURL(url);
}
