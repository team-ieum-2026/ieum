"use client";
// 주인: FE-B — 통계 (11/1 개편 목업 statsPage, 1차 피드백으로 메뉴 이름 '취업·지원 현황' → '통계'). 대시보드 API 로 그린다.
// ① 수료생 관리 현황(도넛 + 범례 3줄) ② 취업 지표(링 3개). '취업률'은 관리 상태 기준이며 공식 취업 성과가 아니다.
// ③ 도움 요청 통계: 처리 상태별은 상태별 목록 API(ALL)로 접수 · 처리 중 · 상담 예정 · 연락 완료 · 요청 종료 전체 건수를 센다.
//    선호 연락별 · 필요한 도움별은 대시보드 helpRequests(접수 OPEN) 기준. 유형이 없으면 '미분류' (공고 · 메모로 추측 분류하지 않음)
// 수료생 통합 현황 표는 C2 수료생 관리와 겹쳐 1차 피드백으로 뺐다. 전체 명단 CSV 는 C2 로 옮김 (studentCsv.ts)
import { useEffect, useId, useState } from "react";
import { apiErrorText, Badge, Icon, LoadState, PageTitle, Panel, PanelHead, btn } from "@/components/coordinator/CoordUi";
import { IS_MOCK } from "@/components/coordinator/demoAccount";
import {
  MANAGE_ORDER,
  contactLabels,
  HELP_TYPE_ORDER,
  helpLabels,
  helpTone,
  helpTypeLabel,
  knownCount,
  manageChartVar,
  manageCounts,
  pct,
} from "@/components/coordinator/coordLabels";
import { getCoordinatorHelpRequests, getDashboard } from "@/lib/api/coordinatorReview";
import type { CoordinatorHelpRequest, Dashboard } from "@/types/coordinator";
import type { ContactMethod, StudentHelpRequestStatus } from "@/types/student";

const R = 54;
const CIRC = 2 * Math.PI * R;
/** 도넛 조각 = 대표 상태 4분류 (합계 = 전체 수료생). 도움 요청은 상태가 아니어서 넣지 않는다 */
const DONUT_ORDER = MANAGE_ORDER;

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
        if (!ignore) setError(apiErrorText(e, "현황을 불러오지 못했어요."));
      });
    return () => {
      ignore = true;
    };
  }, [reloadKey]);

  return (
    <>
      <PageTitle title="통계" />
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
  // 집계 기준 설명은 기본으로 접어 두고 '취업 지표' 옆 버튼으로만 펼친다
  const [showBasis, setShowBasis] = useState(false);
  const basisId = useId();
  const total = data.totalStudents;
  const s = data.statusSummary;
  const counts = manageCounts(s);
  const known = knownCount(total, s);
  const emp = s.EMPLOYED;
  const seeking = counts.SEEKING;

  // 각 조각의 길이와, 앞 조각들 길이의 합(시작 위치)
  const lens = DONUT_ORDER.map((k) => (total ? (counts[k] / total) * CIRC : 0));
  const arcs = DONUT_ORDER.map((k, i) => ({
    k,
    dash: Math.max(0, lens[i] - (counts[k] && lens[i] > 4 ? 2 : 0)),
    offset: -lens.slice(0, i).reduce((sum, len) => sum + len, 0),
  })).filter((a) => counts[a.k] > 0);

  const legend = [
    { label: "취업 완료", color: manageChartVar.EMPLOYED, n: emp, noteText: "패스 사유 '이미 취업' 응답 기준" },
    { label: "구직 중", color: manageChartVar.SEEKING, n: seeking, noteText: "기관 추천 지원 중 · 패스 후 대기 · 기타 포함" },
    { label: "휴식", color: manageChartVar.RESTING, n: counts.RESTING, noteText: "수료생이 '휴식'을 선택 · 연락 기록은 유지" },
    { label: "후속 확인 필요", color: manageChartVar.CHECK_REQUIRED, n: s.CHECK_REQUIRED, noteText: "1주 넘게 앱 응답 없음 · 상태 확인" },
  ];

  // 리뉴얼 .gauge-circle-box: 회색 상자 + 큰 숫자 (파악률 초록 · 파악 기준 취업률 파랑 · 전체 기준 회색). 계산식은 그대로
  const rings = [
    { label: "관리 상태 파악률", a: known, b: total, color: "text-(--coord-green)", note: `분류 ${known}명 ÷ 전체 ${total}명` },
    { label: "취업률 (파악 기준)", a: emp, b: known, color: "text-(--coord-blue)", note: `취업 ${emp}명 ÷ 파악 ${known}명` },
    { label: "전체 기준 취업률", a: emp, b: total, color: "text-(--coord-muted)", note: `취업 ${emp}명 ÷ 전체 ${total}명 · 최소치` },
  ];

  return (
    <>
      <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1.35fr)] gap-5">
        <Panel label="수료생 관리 현황">
          <PanelHead title="수료생 관리 현황">
            <Badge tone="green">현재 등록 인원 {total}명</Badge>
          </PanelHead>
          <div className="flex items-center gap-5 p-5">
            <div className="relative size-[130px] flex-none" role="img" aria-label={`관리 상태 분포: ${legend.map((l) => `${l.label} ${l.n}명`).join(", ")}`}>
              <svg viewBox="0 0 140 140" aria-hidden="true" className="block size-full">
                <circle cx="70" cy="70" r={R} fill="none" stroke="var(--coord-chart-track)" strokeWidth="14" />
                {arcs.map((a) => (
                  <circle
                    key={a.k}
                    cx="70"
                    cy="70"
                    r={R}
                    fill="none"
                    stroke={manageChartVar[a.k]}
                    strokeWidth="14"
                    strokeDasharray={`${a.dash.toFixed(2)} ${(CIRC - a.dash).toFixed(2)}`}
                    strokeDashoffset={a.offset.toFixed(2)}
                    transform="rotate(-90 70 70)"
                  />
                ))}
              </svg>
              <div className="absolute inset-0 flex flex-col items-center justify-center">
                <b className="text-xl leading-none font-extrabold">{total}명</b>
                <span className="mt-1 text-[11px] text-(--coord-faint)">관리 중</span>
              </div>
            </div>
            <ul className="flex min-w-0 flex-1 flex-col gap-2 text-[13.5px]">
              {legend.map((l) => {
                const warn = l.color === manageChartVar.CHECK_REQUIRED;
                return (
                  // 분류 설명(note)은 줄을 늘리지 않게 마우스 안내 · 화면 낭독기용으로 둔다 (같은 설명이 '집계 기준'에도 있음)
                  <li key={l.label} className="flex items-center justify-between gap-3">
                    <span className="flex min-w-0 items-center gap-1.5" title={l.noteText}>
                      <i aria-hidden="true" className="block size-2.5 shrink-0 rounded-full" style={{ background: l.color }} />
                      {l.label}
                      <span className="sr-only"> ({l.noteText})</span>
                    </span>
                    <b className={`shrink-0 whitespace-nowrap ${warn ? "text-(--coord-chart-check)" : ""}`}>
                      {l.n}명 ({Math.round(pct(l.n, total))}%)
                    </b>
                  </li>
                );
              })}
            </ul>
          </div>
        </Panel>
        <Panel label="취업 지표">
          <PanelHead title="취업 지표">
            <button
              type="button"
              aria-expanded={showBasis}
              aria-controls={basisId}
              onClick={() => setShowBasis((v) => !v)}
              className={`${btn.base} ${btn.outline} ${btn.sm} ${showBasis ? "border-(--coord-green)! bg-(--coord-green-soft)! text-(--coord-green-ink)" : ""}`}
            >
              집계 기준 <Icon name="info" className="size-3.5" />
            </button>
          </PanelHead>
          <div className="p-5">
            <div className="grid grid-cols-3 gap-4 text-center">
              {rings.map((r) => (
                <div key={r.label} className="flex flex-col items-center justify-center rounded-md border border-(--coord-line) bg-(--coord-head) px-3 py-4">
                  <b className={`text-[30px] leading-none font-extrabold ${r.color}`}>{pct(r.a, r.b)}%</b>
                  <span className="mt-2 text-sm font-bold">{r.label}</span>
                  <small className="mt-0.5 text-xs break-keep text-(--coord-faint)">{r.note}</small>
                </div>
              ))}
            </div>
            <p className="mt-3 text-center text-xs text-(--coord-faint)">취업률은 관리 상태 기준이며 공식 취업 성과가 아니에요.</p>
          </div>
        </Panel>
      </div>
      {/* 닫혀 있으면 아무것도 그리지 않아 여백이 생기지 않는다 */}
      <div
        id={basisId}
        role="region"
        aria-label="관리 상태와 집계 기준"
        hidden={!showBasis}
        className="mt-5 rounded-md border border-(--coord-tint-line) bg-(--coord-tint) px-4 py-3 text-[13.5px] leading-[1.7]"
      >
        <b>관리 상태와 집계 기준</b>
        <p className="mt-2">
          관리 상태는 수료생 앱 응답과 도움 요청으로 서버가 분류해요. 1주 넘게 응답이 없는 수료생은 &lsquo;후속 확인 필요&rsquo;로 따로 세요. &lsquo;구직
          중&rsquo;은 운영상의 분류이며 미취업을 확정하는 값은 아니에요.
        </p>
        <p className="mt-2">
          대표 상태 4분류(취업 완료 · 구직 중 · 휴식 · 후속 확인 필요)의 합은 전체 인원과 같아요. 도움 요청은 상태가 아니라 아래 &lsquo;도움 요청
          통계&rsquo;에서 건수로 따로 세요.
        </p>
        <p className="mt-2">
          관리 상태 파악률 = (전체 − 후속 확인 필요) ÷ 전체 · 취업률 = 취업 완료 ÷ (전체 − 후속 확인 필요) · 전체 기준 취업률 = 취업 완료 ÷ 전체.
          취업률은 관리 상태 기준이며 공식 취업 성과가 아니에요.
        </p>
      </div>
      <HelpStats data={data} />
    </>
  );
}

// ---- ③ 도움 요청 통계 ----

/** 목업 처리 상태 순서. status 가 없는 줄은 서버에 없는 상태값이라 미연동 */
const HELP_STATES: { label: string; status?: StudentHelpRequestStatus }[] = [
  { label: helpLabels.OPEN, status: "OPEN" },
  { label: helpLabels.IN_PROGRESS, status: "IN_PROGRESS" },
  { label: helpLabels.CONSULTATION_SCHEDULED, status: "CONSULTATION_SCHEDULED" },
  { label: helpLabels.CONTACTED, status: "CONTACTED" },
  { label: helpLabels.CLOSED, status: "CLOSED" },
];
const CONTACT_ORDER: (ContactMethod | "NONE")[] = ["PHONE", "SMS", "EMAIL", "NONE"];

function HelpStats({ data }: { data: Dashboard }) {
  // 처리 상태별은 상태별 목록 API(ALL)로 전체 요청을 센다. 실패하면 대시보드의 접수 건수만 보여 준다
  const [all, setAll] = useState<CoordinatorHelpRequest[] | null>(null);
  const [allFailed, setAllFailed] = useState(false);
  useEffect(() => {
    let ignore = false;
    getCoordinatorHelpRequests("ALL")
      .then((list) => {
        if (!ignore) setAll(list);
      })
      .catch(() => {
        if (!ignore) setAllFailed(true);
      });
    return () => {
      ignore = true;
    };
  }, [data]);
  const statusCount = (status: StudentHelpRequestStatus) =>
    all ? all.filter((h) => h.status === status).length : status === "OPEN" ? data.helpRequests.length : null;
  const statusTotal = all ? all.length : data.helpRequests.length;
  const allStudentCount = all ? new Set(all.map((h) => h.studentId)).size : null;

  const open = data.helpRequests;
  // 건수(요청)와 인원(학생)을 따로 센다: 한 학생이 여러 건을 낼 수 있다
  const studentCount = new Set(open.map((h) => h.studentId)).size;
  const byContact = CONTACT_ORDER.map((m) => ({
    m,
    n: open.filter((h) => (m === "NONE" ? h.contactMethod === null : h.contactMethod === m)).length,
  }));
  // 필요한 도움별: 서버 helpType 그대로 건수 집계, null · 키 없음은 '미분류' (공고 · 메모로 추측하지 않음)
  const byType = [...HELP_TYPE_ORDER, null].map((t) => ({ t, n: open.filter((h) => (h.helpType ?? null) === t).length }));
  return (
    <Panel label="도움 요청 통계" className="mt-5">
      <PanelHead
        title="도움 요청 통계"
        sub="처리 상태별은 지금까지 들어온 전체 요청, 선호 연락별 · 필요한 도움별은 접수 중인 요청만 건수로 집계해요."
      >
        <span className="shrink-0 rounded-full bg-(--coord-red-soft) px-3.5 py-[5px] text-[13px] font-bold whitespace-nowrap text-(--coord-red)">
          접수 중 요청 {open.length}건 · 수료생 {studentCount}명 기준
        </span>
      </PanelHead>
      {/* 리뉴얼 .help-stats-grid: 3열 1.1 : 1 : 1.1, 간격 32px */}
      <div className="grid grid-cols-[1.1fr_1fr_1.1fr] gap-7 px-5 pt-5 pb-6">
        <StatColumn
          title="처리 상태별"
          note={all ? `전체 ${all.length}건 · 수료생 ${allStudentCount}명` : undefined}
          caution={IS_MOCK ? "목업에는 접수 중인 요청만 있어요. 다른 상태 건수는 실제 BE 연결 시 보여요." : undefined}
        >
          {HELP_STATES.map(({ label, status }) => {
            const n = status ? statusCount(status) : null;
            // 목업 목록은 접수(OPEN)만 만들어 다른 상태가 0건처럼 보이므로, 목업 모드에서는 0 대신 '목업 없음'
            if (IS_MOCK && status && status !== "OPEN") {
              return (
                <StatRow
                  key={label}
                  label={<span className="text-(--coord-muted)">{label}</span>}
                  unlinked="목업 데이터에 없는 상태예요 (실제 BE 에서는 상태별 목록 API 로 셉니다)"
                  placeholder="— 목업 없음"
                />
              );
            }
            if (status && n !== null) {
              return (
                <StatRow
                  key={label}
                  label={
                    <Badge tone={helpTone[status]} small>
                      {label}
                    </Badge>
                  }
                  n={n}
                  total={statusTotal}
                />
              );
            }
            const loading = status && !all && !allFailed;
            return (
              <StatRow
                key={label}
                label={<span className="text-(--coord-muted)">{label}</span>}
                unlinked={!status ? "서버에 없는 상태값이에요 (BE 확정 전)" : loading ? "불러오는 중" : "상태별 목록을 불러오지 못했어요"}
                placeholder={loading ? "불러오는 중…" : allFailed && status ? "조회 실패" : undefined}
              />
            );
          })}
        </StatColumn>
        <StatColumn title="선호 연락별" note={`접수 중 ${open.length}건 중`}>
          {byContact.map(({ m, n }) => (
            <StatRow key={m} label={m === "NONE" ? "선호 연락 선택 안 함" : contactLabels[m]} n={n} total={open.length} />
          ))}
        </StatColumn>
        <StatColumn title="필요한 도움별" note={`접수 중 ${open.length}건 중`}>
          {byType.map(({ t, n }) => (
            <StatRow key={t ?? "NONE"} label={t ? helpTypeLabel(t) : <span className="text-(--coord-muted)">미분류</span>} n={n} total={open.length} />
          ))}
        </StatColumn>
      </div>
    </Panel>
  );
}

/** 통계 한 열 (리뉴얼 .help-stats-col). caution: 집계 범위 주의 문구 (목업 모드 등) */
function StatColumn({ title, note, caution, children }: { title: string; note?: string; caution?: string; children: React.ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-3">
      <div className="flex items-center justify-between gap-2 border-b border-(--coord-line-soft) pb-2">
        <h3 className="text-[14.5px] font-extrabold">{title}</h3>
        {note && <span className="text-[12.5px] font-medium whitespace-nowrap text-(--coord-faint)">{note}</span>}
      </div>
      <ul className="flex flex-col gap-3">{children}</ul>
      {caution && <p className="text-xs leading-[1.4] text-(--coord-attention)">{caution}</p>}
    </div>
  );
}

/** 통계 한 줄 (리뉴얼 .help-stats-item): 이름 100px · 8px 막대 · 건수. unlinked 이면 '— 미연동' (placeholder 가 있으면 그 문구) */
function StatRow({
  label,
  n = 0,
  total = 0,
  unlinked,
  placeholder,
}: {
  label: React.ReactNode;
  n?: number;
  total?: number;
  unlinked?: string;
  placeholder?: string;
}) {
  return (
    <li className="flex min-h-[26px] items-center gap-3 text-[13.5px]">
      {/* 이름 칸은 가장 긴 '선호 연락 선택 안 함'이 한 줄에 들어가는 폭 (글자 크기는 그대로) */}
      <span className="w-[132px] shrink-0 font-semibold whitespace-nowrap text-(--coord-muted)">{label}</span>
      {/* 미연동은 막대를 그리지 않는다 (빈 막대가 0건처럼 보이지 않게) */}
      {unlinked ? (
        <span title={unlinked} className="flex-1 pr-1 text-right text-[12.5px] text-(--coord-chart-resting)">
          {placeholder ?? "— 미연동"}
        </span>
      ) : (
        <>
          <span className="flex h-2 flex-1 overflow-hidden rounded-[4px] bg-(--coord-line-soft)">
            <span className="block h-full rounded-[4px] bg-(--coord-green) transition-[width] duration-300" style={{ width: `${pct(n, total)}%` }} />
          </span>
          <b className="w-11 shrink-0 text-right">{n}건</b>
        </>
      )}
    </li>
  );
}
