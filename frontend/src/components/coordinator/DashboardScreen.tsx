"use client";
// 주인: FE-B — C1 오늘의 업무 (FE-B-07, 11/1 개편 목업). 데이터는 GET /api/coordinator/dashboard (docs/API.md 2-3).
// - 지표 3개: 새 도움 요청(helpRequests) · 검토 대기 서류(reviewQueue) · 후속 연락 필요(callList = 1주 무응답, 추가 확인 필요와 같은 대상)
//   목업의 '상담 예정' 지표 · '오늘 예정된 상담'은 일정 데이터가 없어 제외
// - 오늘 먼저 확인할 일: 도움 요청 + 검토 대기 서류를 먼저 들어온 순으로 상위 5건. reviewQueue 에 studentId 가 없어 이름으로 합치지 않고 건별 행
// - 서류 검토 현황: 검토 대기 건수만 (다른 단계는 건수 API 가 없어 숨김, 0건 표시 금지)
// - 수료생 관리 현황: totalStudents · statusSummary 로 계산
// 다른 탭에 갔다가 돌아오면 대시보드 · 사이드바 건수를 다시 조회한다 (그사이 들어온 도움 요청 · 제출 반영)
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { REVIEW_QUEUE_ANCHOR, notifyReviewQueueChanged } from "@/components/coordinator/CoordinatorSidebar";
import { Badge, Icon, LoadState, PageTitle, Panel, PanelHead, btn, type IconName } from "@/components/coordinator/CoordUi";
import {
  MANAGE_ORDER,
  contactLabels,
  knownCount,
  manageChartVar,
  manageCounts,
  manageLabels,
  manageTone,
  monthDay,
  pct,
} from "@/components/coordinator/coordLabels";
import { ApiError } from "@/lib/api/client";
import { getDashboard } from "@/lib/api/coordinatorReview";
import type { Dashboard } from "@/types/coordinator";

/** 오늘 먼저 확인할 일 최대 건수 */
const TASK_LIMIT = 5;

/** 탭 복귀 재조회 최소 간격. 탭을 빠르게 오가도 그사이 다시 부르지 않는다 */
const RETURN_REFRESH_GAP_MS = 5000;

export default function DashboardScreen() {
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  // 탭 복귀 재조회의 중복 방지용: 조회 중인지, 마지막 조회를 시작한 시각
  const loadingRef = useRef(false);
  const lastLoadRef = useRef(0);

  useEffect(() => {
    let ignore = false;
    loadingRef.current = true;
    lastLoadRef.current = Date.now();
    getDashboard()
      .then((d) => {
        if (!ignore) setData(d);
      })
      .catch((e: unknown) => {
        if (!ignore) setError(e instanceof ApiError ? e.message : "대시보드를 불러오지 못했어요.");
      })
      .finally(() => {
        if (!ignore) loadingRef.current = false;
      });
    return () => {
      ignore = true;
      loadingRef.current = false;
    };
  }, [reloadKey]);

  // 다른 탭에서 돌아오면 다시 조회. 조회 중이거나 방금 불렀으면 건너뛴다. 사이드바 건수도 같이 다시 센다
  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState !== "visible" || loadingRef.current) return;
      if (Date.now() - lastLoadRef.current < RETURN_REFRESH_GAP_MS) return;
      setError(null);
      setReloadKey((k) => k + 1);
      notifyReviewQueueChanged();
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => document.removeEventListener("visibilitychange", onVisible);
  }, []);

  if (!data) {
    return (
      <LoadState
        error={error}
        loadingText="오늘의 업무를 불러오는 중이에요…"
        onRetry={() => {
          setError(null);
          setReloadKey((k) => k + 1);
        }}
      />
    );
  }

  return (
    // 1440×900 에서 페이지 스크롤 없이 한눈에: 상단 막대 60 + 위아래 여백 70 + 반올림 여유
    <div className="flex min-h-[calc(100vh-140px)] flex-col">
      <PageTitle
        title="오늘의 업무"
        description="요청의 맥락을 확인하고, 필요한 지원부터 이어가세요."
        action={
          <span className="inline-flex items-center gap-2 rounded-[7px] border border-(--coord-line) bg-(--coord-surface) px-3 py-2 text-xs">
            <Icon name="calendar" className="size-4" />
            {data.asOf.slice(0, 10).replaceAll("-", ".")} 기준
          </span>
        }
      />
      <div className="mb-[22px] grid grid-cols-3 gap-3.5">
        <Kpi
          label="새 도움 요청"
          value={data.helpRequests.length}
          unit="건"
          sub="접수 상태 · 확인 후 연락"
          href="/coordinator/help-requests"
          icon="help"
          urgent
        />
        <Kpi label="검토 대기 서류" value={data.reviewQueue.length} unit="건" sub="제출본과 근거 확인" href="/coordinator/applications" icon="doc" />
        <Kpi
          label="후속 연락 필요"
          value={data.callList.length}
          unit="명"
          sub="1주 무응답 · 전화 확인"
          href="/coordinator/students?status=CHECK_REQUIRED"
          icon="phone"
        />
      </div>
      <div className="grid flex-1 grid-cols-[minmax(0,1.8fr)_minmax(300px,1fr)] gap-3.5">
        <div className="flex min-h-0 flex-col gap-3">
          <Tasks data={data} />
          <DocStatus count={data.reviewQueue.length} />
        </div>
        <ManagementCard data={data} />
      </div>
    </div>
  );
}

function Kpi({
  label,
  value,
  unit,
  sub,
  href,
  icon,
  urgent = false,
}: {
  label: string;
  value: number;
  unit: string;
  sub: string;
  href: string;
  icon: IconName;
  urgent?: boolean;
}) {
  return (
    <Link
      href={href}
      className={`relative rounded-xl border p-5 text-left transition focus-visible:outline-3 focus-visible:outline-offset-3 focus-visible:outline-(--coord-green) ${
        urgent
          ? "border-(--coord-attention-line) bg-(--coord-attention-soft) hover:border-(--coord-orange)"
          : "border-(--coord-line) bg-(--coord-surface) hover:border-(--coord-green)"
      }`}
    >
      <span className="text-xs font-semibold">{label}</span>
      <span
        className={`absolute top-[17px] right-[17px] grid size-[30px] place-items-center rounded-lg ${
          urgent ? "bg-(--coord-orange-soft) text-(--coord-orange)" : "bg-(--coord-green-soft) text-(--coord-green-ink)"
        }`}
      >
        <Icon name={icon} />
      </span>
      <span className="mt-[13px] mb-2 block text-[31px] leading-[1.2] font-bold tracking-[-1.5px]">
        {value}
        <small className="ml-[5px] text-[13px] font-medium tracking-normal text-(--coord-muted)">{unit}</small>
      </span>
      <span className="flex items-center gap-1 text-[11px] text-(--coord-muted)">
        {sub} <Icon name="arrow" className="size-3.5" />
      </span>
    </Link>
  );
}

type Task = { kind: "help" | "doc"; key: string; at: string; name: string; text: string; href: string };

/** 도움 요청 + 검토 대기 서류를 건별로, 먼저 들어온 순 */
function buildTasks(data: Dashboard): Task[] {
  const help: Task[] = data.helpRequests.map((h) => ({
    kind: "help",
    key: `help-${h.helpRequestId}`,
    at: h.createdAt,
    name: h.studentName,
    text: [h.jobTitle, h.contactMethod && `${contactLabels[h.contactMethod]} 선호`].filter(Boolean).join(" · ") || "도움 요청",
    href: `/coordinator/help-requests?id=${h.helpRequestId}`,
  }));
  const docs: Task[] = data.reviewQueue.map((r) => ({
    kind: "doc",
    key: `doc-${r.applicationId}`,
    at: r.submittedAt,
    name: r.studentName,
    text: `${r.jobTitle} · 제출본 확인 필요`,
    href: `/coordinator/applications/${r.applicationId}`,
  }));
  return [...help, ...docs].sort((a, b) => a.at.localeCompare(b.at));
}

function Tasks({ data }: { data: Dashboard }) {
  const all = buildTasks(data);
  const rows = all.slice(0, TASK_LIMIT);
  return (
    <Panel label="오늘 먼저 확인할 일" className="flex flex-1 flex-col">
      <PanelHead
        title={
          <>
            오늘 먼저 확인할 일 <Badge tone="green">총 {all.length}건</Badge>
          </>
        }
      >
        <span className="text-xs text-(--coord-muted)">먼저 들어온 순 · 상위 {rows.length}건</span>
      </PanelHead>
      {rows.length === 0 ? (
        <p className="p-[35px] text-center text-[13px] text-(--coord-muted)">지금 확인할 업무가 없어요.</p>
      ) : (
        <ul className="flex-1">
          {rows.map((t) => (
            <li key={t.key} className="flex items-center gap-2.5 border-b border-(--coord-line-soft) px-4 py-2.5 last:border-b-0">
              <span
                className={`grid size-[31px] shrink-0 place-items-center rounded-[9px] ${
                  t.kind === "help" ? "bg-(--coord-red-soft) text-(--coord-red)" : "bg-(--coord-green-soft) text-(--coord-green-ink)"
                }`}
              >
                <Icon name={t.kind === "help" ? "help" : "doc"} />
              </span>
              <div className="min-w-0 flex-1">
                <h3 className="flex items-center gap-[7px] text-[13px] font-bold">
                  {t.name} <Badge tone={t.kind === "help" ? "red" : "orange"}>{t.kind === "help" ? "도움 요청" : "서류 검토"}</Badge>
                </h3>
                <p className="mt-1 truncate text-xs text-(--coord-muted)">{t.text}</p>
              </div>
              <span className="mr-2 flex shrink-0 flex-col items-end leading-[1.35]">
                <b className="text-xs font-semibold">
                  {monthDay(t.at)} {t.at.slice(11, 16)}
                </b>
                <small className="text-[10px] text-(--coord-muted)">{t.kind === "help" ? "접수" : "제출"}</small>
              </span>
              <Link href={t.href} className={`${btn.base} ${btn.outline} ${btn.sm}`}>
                {t.kind === "help" ? "요청 확인" : "서류 검토"}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}

function DocStatus({ count }: { count: number }) {
  return (
    <Panel label="서류 검토 현황">
      <div id={REVIEW_QUEUE_ANCHOR} className="flex scroll-mt-20 items-center justify-between gap-3 px-[22px] py-4">
        <h2 className="text-[15px] font-bold">서류 검토 현황</h2>
        <Link
          href="/coordinator/applications"
          className="flex min-w-[140px] flex-col items-center gap-1 rounded-[9px] bg-(--coord-orange-soft) px-6 py-2 text-(--coord-orange) hover:brightness-95"
        >
          <b className="text-[22px] leading-[1.2]">{count}</b>
          <span className="text-[11px] font-semibold">검토 대기</span>
        </Link>
        <Link href="/coordinator/applications" className="flex items-center gap-1.5 text-xs font-semibold text-(--coord-green)">
          전체 <Icon name="arrow" className="size-4" />
        </Link>
      </div>
    </Panel>
  );
}

function ManagementCard({ data }: { data: Dashboard }) {
  const total = data.totalStudents;
  const counts = manageCounts(data.statusSummary);
  const known = knownCount(total, data.statusSummary);
  const check = data.statusSummary.CHECK_REQUIRED;
  return (
    <section aria-label="수료생 관리 현황" className="flex flex-col rounded-xl border border-(--coord-tint-line) bg-(--coord-tint) px-4 py-3">
      <div className="flex items-center justify-between gap-4">
        <h2 className="text-[15px] font-bold">수료생 관리 현황</h2>
        <span className="text-xs text-(--coord-muted)">관리 중 수료생 {total}명</span>
      </div>
      <div className="my-[7px] flex items-center justify-between border-b border-(--coord-tint-line) pb-2.5">
        <div>
          <span className="text-xs text-(--coord-muted)">관리 상태 파악률</span>
          <strong className="mt-[3px] block text-[30px] leading-[1.2]">
            {pct(known, total)}
            <small className="ml-[3px] text-[15px]">%</small>
          </strong>
        </div>
        <p className="text-right text-[11px] leading-[1.7] text-(--coord-muted)">
          상태가 분류된 수료생
          <br />
          <b className="text-(--coord-ink)">
            {known}명 / 전체 {total}명
          </b>
        </p>
      </div>
      {total > 0 && (
        <div
          className="my-3 flex h-[15px] overflow-hidden rounded-[5px]"
          role="img"
          aria-label={`관리 상태 분포: ${MANAGE_ORDER.map((s) => `${manageLabels[s]} ${counts[s]}명`).join(", ")}`}
        >
          {MANAGE_ORDER.filter((s) => counts[s] > 0).map((s) => (
            <span key={s} style={{ width: `${pct(counts[s], total)}%`, background: manageChartVar[s] }} />
          ))}
        </div>
      )}
      {MANAGE_ORDER.map((s) => (
        <Link key={s} href={`/coordinator/students?status=${s}`} className="flex h-[44px] w-full items-center rounded-md hover:bg-(--coord-hover)">
          <Badge tone={manageTone[s]}>{manageLabels[s]}</Badge>
          <b className="ml-auto flex items-center gap-2 text-[13px]">
            {counts[s]}명
            <em className="min-w-11 text-right text-[11px] font-medium text-(--coord-muted) not-italic">{pct(counts[s], total)}%</em>
            <Icon name="arrow" className="size-4" />
          </b>
        </Link>
      ))}
      <Link
        href="/coordinator/students?status=CHECK_REQUIRED"
        className="mt-3.5 mb-2.5 block rounded-lg border border-(--coord-attention-line) bg-(--coord-attention-soft) p-2.5"
      >
        <b className="block text-[13px] text-(--coord-attention)">
          직접 연락이 필요한 수료생 {check}명 <span className="text-[11px] font-normal">/ 전체 {total}명</span>
        </b>
        <span className="mt-[5px] flex items-center gap-1 text-[11px] text-(--coord-attention)">
          1주 넘게 앱 응답이 없어요. 나머지 {known}명은 앱 응답이나 도움 요청으로 상태가 분류돼 있어요.
          <Icon name="arrow" className="size-[13px]" />
        </span>
      </Link>
      <p className="my-2 text-[11px] leading-normal text-(--coord-muted)">
        관리 상태 파악률은 앱 응답 · 도움 요청으로 상태가 분류된 비율이에요. 취업 여부를 직접 확인한 비율은 아니에요.
      </p>
      <Link href="/coordinator/stats" className="mt-auto flex items-center gap-1.5 text-xs font-semibold text-(--coord-green)">
        취업·지원 현황 보기 <Icon name="arrow" className="size-4" />
      </Link>
    </section>
  );
}
