"use client";
// 주인: FE-B — 취업·지원 현황 (11/1 개편 목업, 신규 화면). 대시보드 API 의 totalStudents · statusSummary 로만 계산한다.
// ① 수료생 관리 현황(도넛 + 범례 3줄) ② 취업 지표(링 3개). '취업률'은 관리 상태 기준이며 공식 취업 성과가 아니다.
// 도움 요청 통계 · 수료생별 통합 명단 · 교육과정별 비교 · CSV 는 데이터 부족 · C2 와 중복이라 제외.
import { useEffect, useState } from "react";
import { LoadState, PageTitle, Panel } from "@/components/coordinator/CoordUi";
import { knownCount, manageChartVar, manageCounts, pct } from "@/components/coordinator/coordLabels";
import { ApiError } from "@/lib/api/client";
import { getDashboard } from "@/lib/api/coordinatorReview";
import type { Dashboard } from "@/types/coordinator";
import type { StudentStatus } from "@/types/student";

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
      <details className="mt-5 rounded-lg border border-(--coord-tint-line) bg-(--coord-tint) px-3.5 py-3 text-xs leading-[1.7]">
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
    </>
  );
}
