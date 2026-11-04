"use client";
// 주인: FE-B — C1 오늘의 업무 (FE-B-07, 11/1 개편 목업). 데이터는 GET /api/coordinator/dashboard (docs/API.md 2-3).
// - 지표 4개: 새 도움 요청(helpRequests) · 검토 대기 서류(reviewQueue) · 후속 확인 필요(callList = CHECK_REQUIRED 와 같은 대상)
//   · 오늘 상담(남은 상담 건수 · 가장 가까운 상담 · 시간 목록 펼치기/접기)
// - 오늘 먼저 확인할 일: 도움 요청 + 검토 대기 서류 + 연속 패스 연락 대상(passFollowUpList)을 먼저 들어온 순으로 상위 5건 + '전체 보기' 모달.
//   reviewQueue 에 studentId 가 없어 이름으로 합치지 않고 건별 행
// - 오늘 상담은 상단 카드에서 확인하며, 하단 상담 · 서류 검토 현황 패널은 표시하지 않는다.
// - 수료생 관리 현황: totalStudents · statusSummary 로 계산
// 다른 탭에 갔다가 돌아오면 대시보드 · 사이드바 건수를 다시 조회한다 (그사이 들어온 도움 요청 · 제출 반영)
import Link from "next/link";
import { useEffect, useId, useRef, useState } from "react";
import { notifyReviewQueueChanged } from "@/components/coordinator/CoordinatorSidebar";
import { apiErrorText, RefreshAction, Badge, Icon, LoadState, PageTitle, Panel, PanelHead, btn, type IconName } from "@/components/coordinator/CoordUi";
import type { ConsultationSchedule } from "@/components/coordinator/demoAccount";
import type { CoordTone } from "@/components/coordinator/coordLabels";
import {
  MANAGE_ORDER,
  contactLabels,
  helpTitle,
  helpTypeLabel,
  knownCount,
  manageChartVar,
  manageCounts,
  manageLabels,
  monthDay,
  pct,
} from "@/components/coordinator/coordLabels";
import { getCoordinatorHelpRequests, getDashboard } from "@/lib/api/coordinatorReview";
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
  // 수동 '목록 새로고침': 성공하면 바꾸고, 실패하면 지금 화면을 그대로 두고 문구만 띄운다
  const [refreshing, setRefreshing] = useState(false);
  const [refreshError, setRefreshError] = useState<string | null>(null);

  const refresh = async () => {
    if (loadingRef.current) return;
    loadingRef.current = true;
    lastLoadRef.current = Date.now();
    setRefreshing(true);
    setRefreshError(null);
    try {
      setData(await getDashboard());
      notifyReviewQueueChanged(); // 사이드바 · 상단 알림 건수도 같은 시점으로
    } catch (e) {
      setRefreshError(apiErrorText(e, "오늘의 업무를 다시 불러오지 못했어요."));
    } finally {
      loadingRef.current = false;
      setRefreshing(false);
    }
  };

  useEffect(() => {
    let ignore = false;
    loadingRef.current = true;
    lastLoadRef.current = Date.now();
    getDashboard()
      .then((d) => {
        if (!ignore) setData(d);
      })
      .catch((e: unknown) => {
        if (!ignore) setError(apiErrorText(e, "대시보드를 불러오지 못했어요."));
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
    // 제목 → KPI 4개 → 좌(할 일) 1.55 : 우(수료생 관리 현황) 1. 상담 목록은 상단 카드 아래에 겹쳐 펼친다.
    <div>
      <PageTitle
        title="오늘의 업무"
        // 수정사항3: 오른쪽 위 '기준 날짜' 상자 없음 (dev 통합 때 되살아난 것을 다시 뺌)
        action={<RefreshAction busy={refreshing} error={refreshError} onRefresh={refresh} />}
      />
      <div className="mb-5 grid grid-cols-4 gap-4">
        <Kpi
          label="새 도움 요청"
          value={data.helpRequests.length}
          unit="건"
          sub="접수 상태 · 확인 후 연락"
          href="/coordinator/help-requests"
          icon="help"
          tone="amber"
        />
        <Kpi
          label="검토 대기 서류"
          value={data.reviewQueue.length}
          unit="건"
          sub="제출본과 근거 확인"
          href="/coordinator/applications"
          icon="doc"
          tone="green"
        />
        <Kpi
          label="후속 확인 필요"
          value={data.callList.length}
          unit="명"
          sub="활동 공백 · 상태 확인"
          href="/coordinator/students?status=CHECK_REQUIRED"
          icon="phone"
          tone="blue"
        />
        <TodayConsultationCard key={data.asOf} asOf={data.asOf} />
      </div>
      {/* 왼쪽 할 일은 오른쪽 수료생 관리 현황 높이까지 늘어난다 (items-stretch) */}
      <div className="grid grid-cols-[minmax(0,1.55fr)_minmax(340px,1fr)] items-stretch gap-5">
        <Tasks data={data} />
        <ManagementCard data={data} />
      </div>
    </div>
  );
}

/** KPI 색 (리뉴얼 --kpi-accent): 아이콘 칸 · 마우스를 올리면 아래쪽에 보이는 3px 막대 */
type KpiTone = "amber" | "green" | "blue" | "gray";
const KPI_TONE: Record<KpiTone, { icon: string; bar: string }> = {
  amber: { icon: "bg-(--coord-orange-tint) text-(--coord-chart-check)", bar: "bg-(--coord-chart-check)" },
  green: { icon: "bg-(--coord-green-soft) text-(--coord-green)", bar: "bg-(--coord-green)" },
  blue: { icon: "bg-(--coord-blue-soft) text-(--coord-blue)", bar: "bg-(--coord-blue)" },
  gray: { icon: "bg-(--coord-gray-soft) text-(--coord-gray)", bar: "bg-(--coord-gray)" },
};
const KPI_HOVER_CLASS = "hover:-translate-y-px hover:border-(--coord-line-strong) hover:shadow-[0_4px_16px_rgb(18_107_86/0.08)]";

function Kpi({
  label,
  value,
  unit,
  sub,
  href,
  icon,
  tone,
}: {
  label: string;
  /** null: 연동된 API 가 없어 건수를 모름 ('—' 표시) */
  value: number | null;
  unit: string;
  sub: string;
  /** 없으면 이동하지 않는 카드 */
  href?: string;
  icon: IconName;
  tone: KpiTone;
}) {
  const look = KPI_TONE[tone];
  const className = `group relative flex flex-col gap-3 overflow-hidden rounded-[10px] border border-(--coord-line) bg-(--coord-surface) px-5 pt-5 pb-4 text-left shadow-(--coord-shadow) transition focus-visible:outline-3 focus-visible:outline-offset-3 focus-visible:outline-(--coord-green) ${
    href ? KPI_HOVER_CLASS : ""
  }`;
  const body = (
    <>
      <span className="flex items-start justify-between gap-2">
        {/* 1차 피드백: 이름 · 보조 문구를 잘 보이게 키운다 (색은 그대로) */}
        <span className="text-[17px] font-bold text-(--coord-muted)">{label}</span>
        <span className={`grid size-9 shrink-0 place-items-center rounded-lg ${look.icon}`}>
          <Icon name={icon} className="size-[18px]" />
        </span>
      </span>
      <span className="block text-[40px] leading-none font-black tracking-[-1px]">
        {value ?? <span className="text-(--coord-faint)">—</span>}
        {value !== null && <small className="ml-0.5 text-lg font-bold tracking-normal text-(--coord-muted)">{unit}</small>}
      </span>
      <span className="-mt-1 flex items-center gap-1 text-[15px] font-semibold text-(--coord-muted) group-hover:text-(--coord-green)">
        {href && <Icon name="arrow" className="size-3.5" />}
        {value === null && <Badge small>미연동</Badge>} {sub}
      </span>
      {href && <span aria-hidden="true" className={`absolute inset-x-0 bottom-0 h-[3px] opacity-0 transition group-hover:opacity-100 ${look.bar}`} />}
    </>
  );
  return href ? (
    <Link href={href} className={className}>
      {body}
    </Link>
  ) : (
    <div className={className}>{body}</div>
  );
}

type TaskKind = "help" | "doc" | "pass";
type Task = { kind: TaskKind; key: string; at: string; name: string; text: string; href: string };

/** 할 일 종류별 표시: 배지 · 시각 이름 · 버튼 (리뉴얼: 서류 검토 파랑 · 도움 요청 빨강, 아바타는 도움 요청만 빨강) */
const TASK_LOOK: Record<TaskKind, { badge: string; tone: CoordTone; when: string; action: string }> = {
  help: { badge: "도움 요청", tone: "red", when: "접수", action: "요청 확인" },
  doc: { badge: "서류 검토", tone: "blue", when: "검토 요청", action: "서류 검토" },
  pass: { badge: "연락 필요", tone: "orange", when: "마지막 패스", action: "연락 기록" },
};

/** 도움 요청 + 검토 대기 서류 + 연속 패스 연락 대상(백엔드 판정 passFollowUpList)을 건별로, 먼저 들어온 순 */
function buildTasks(data: Dashboard): Task[] {
  const help: Task[] = data.helpRequests.map((h) => ({
    kind: "help",
    key: `help-${h.helpRequestId}`,
    at: h.createdAt,
    name: h.studentName,
    text: [helpTypeLabel(h.helpType), helpTitle(h), h.contactMethod && `${contactLabels[h.contactMethod]} 선호`].filter(Boolean).join(" · "),
    href: `/coordinator/help-requests?id=${h.helpRequestId}`,
  }));
  // 3회 조건은 프론트에서 계산하지 않고 서버 목록 그대로. 연락 기록을 남기면 서버가 목록에서 뺀다
  const pass: Task[] = (data.passFollowUpList ?? []).map((p) => ({
    kind: "pass",
    key: `pass-${p.studentId}`,
    at: p.lastPassAt ?? data.asOf,
    name: p.studentName,
    text: `추천 공고 연속 패스 ${p.consecutivePassCount}회 · 통화 · 상담 기록을 남겨 주세요`,
    href: `/coordinator/students/${p.studentId}?log=new`,
  }));
  const docs: Task[] = data.reviewQueue.map((r) => ({
    kind: "doc",
    key: `doc-${r.applicationId}`,
    at: r.submittedAt,
    name: r.studentName,
    text: `${r.jobTitle} · 제출본 확인 필요`,
    href: `/coordinator/applications/${r.applicationId}`,
  }));
  return [...help, ...docs, ...pass].sort((a, b) => a.at.localeCompare(b.at));
}

/** 할 일 한 줄 (리뉴얼 .task-item-row): 이름 · 종류 배지 · 내용 / 접수·제출 시각 · 이동 버튼. 1차 피드백: 이름 아바타 없음 */
function TaskRow({ t }: { t: Task }) {
  const look = TASK_LOOK[t.kind];
  return (
    <li className="flex items-center justify-between gap-4 border-b border-(--coord-line) px-5 py-3.5 transition last:border-b-0 hover:bg-(--coord-hover)">
      <div className="flex min-w-0 flex-1 items-center">
        <div className="min-w-0 flex-1">
          <h3 className="flex items-center gap-2 text-[15.5px] font-extrabold">
            {t.name} <Badge tone={look.tone}>{look.badge}</Badge>
          </h3>
          <p className="mt-[3px] truncate text-[13.5px] text-(--coord-muted)">{t.text}</p>
        </div>
      </div>
      <div className="flex shrink-0 flex-col items-end gap-1.5">
        <span className="text-[12.5px] text-(--coord-faint)">
          {monthDay(t.at)} {t.at.slice(11, 16)} {look.when}
        </span>
        <Link href={t.href} className={`${btn.base} ${btn.outline} ${btn.sm}`}>
          {look.action}
        </Link>
      </div>
    </li>
  );
}

function Tasks({ data }: { data: Dashboard }) {
  const all = buildTasks(data);
  const rows = all.slice(0, TASK_LIMIT);
  const [showAll, setShowAll] = useState(false);
  return (
    <Panel label="오늘 먼저 확인할 일" className="flex min-w-0 flex-col">
      <PanelHead
        title={
          <>
            오늘 먼저 확인할 일 <Badge tone={all.length > 0 ? "red" : "gray"}>총 {all.length}건 대기</Badge>
          </>
        }
      >
        <span className="flex shrink-0 items-center gap-2">
          <span className="text-[13px] text-(--coord-faint)">먼저 들어온 순 · 상위 {rows.length}건</span>
          {all.length > 0 && (
            <button type="button" onClick={() => setShowAll(true)} className={`${btn.base} ${btn.outline} ${btn.sm}`}>
              전체 보기 <Icon name="arrow" className="size-3.5" />
            </button>
          )}
        </span>
      </PanelHead>
      {rows.length === 0 ? (
        <p className="flex flex-1 items-center justify-center p-9 text-center text-[15px] text-(--coord-muted)">지금 확인할 업무가 없어요.</p>
      ) : (
        <ul>
          {rows.map((t) => (
            <TaskRow key={t.key} t={t} />
          ))}
        </ul>
      )}
      {showAll && <AllTasksModal tasks={all} onClose={() => setShowAll(false)} />}
    </Panel>
  );
}

/** '전체 보기' 모달: 도움 요청 + 검토 대기 서류 전체 (먼저 들어온 순). 배경 · Esc 로 닫힌다 */
function AllTasksModal({ tasks, onClose }: { tasks: Task[]; onClose: () => void }) {
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
  return (
    <div className="fixed inset-0 z-30 flex items-center justify-center bg-(--coord-forest)/40 p-6 backdrop-blur-[3px]" onClick={onClose}>
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onClick={(e) => e.stopPropagation()}
        className="flex max-h-[90vh] w-[760px] flex-col overflow-hidden rounded-[10px] border border-(--coord-line) bg-(--coord-surface) shadow-[0_20px_40px_rgb(0_0_0/0.16)] outline-none"
      >
        <div className="flex items-center justify-between gap-3 border-b border-(--coord-line) px-5 py-4">
          <h2 id={titleId} className="flex items-center gap-2 text-[17px] font-extrabold">
            오늘 확인할 일 전체 <Badge tone="red">{tasks.length}건</Badge>
          </h2>
          <button type="button" onClick={onClose} aria-label="닫기" className="grid size-9 place-items-center rounded-full hover:bg-(--coord-page)">
            <Icon name="close" />
          </button>
        </div>
        <p className="border-b border-(--coord-line) px-5 py-2.5 text-[13.5px] text-(--coord-muted)">도움 요청과 검토 대기 서류를 먼저 들어온 순으로 모두 보여 줘요.</p>
        <ul className="min-h-0 flex-1 overflow-y-auto">
          {tasks.map((t) => (
            <TaskRow key={t.key} t={t} />
          ))}
        </ul>
      </div>
    </div>
  );
}

const CONSULTATION_DAY = new Intl.DateTimeFormat("en-CA", {
  timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit",
});
const CONSULTATION_TIME = new Intl.DateTimeFormat("en-GB", {
  timeZone: "Asia/Seoul", hour: "2-digit", minute: "2-digit", hour12: false,
});
type ConsultationState =
  | { status: "loading" | "error" }
  | { status: "loaded"; items: ConsultationSchedule[] };

/** 오늘 남은 건수와 가장 가까운 상담을 표시한다. 시간이 지난 일정은 건수와 목록에서 자동으로 뺀다. */
function TodayConsultationCard({ asOf }: { asOf: string }) {
  const [state, setState] = useState<ConsultationState>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);
  const [open, setOpen] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  const boxRef = useRef<HTMLElement>(null);
  const toggleRef = useRef<HTMLButtonElement>(null);
  const listId = useId();

  useEffect(() => {
    let ignore = false;
    getCoordinatorHelpRequests("CONSULTATION_SCHEDULED")
      .then((list) => {
        if (ignore) return;
        const today = CONSULTATION_DAY.format(new Date(asOf));
        const items = list
          .filter((h) => h.status === "CONSULTATION_SCHEDULED" && h.consultationAt && Number.isFinite(Date.parse(h.consultationAt)))
          .map((h) => ({
            id: h.helpRequestId,
            studentId: h.studentId,
            studentName: h.studentName,
            scheduledAt: h.consultationAt as string,
            topic: helpTypeLabel(h.helpType),
          }))
          .filter((item) => CONSULTATION_DAY.format(new Date(item.scheduledAt)) === today)
          .sort((a, b) => Date.parse(a.scheduledAt) - Date.parse(b.scheduledAt));
        setState({ status: "loaded", items });
      })
      .catch(() => {
        if (!ignore) setState({ status: "error" });
      });
    return () => { ignore = true; };
  }, [asOf, attempt]);

  // 상담 시각이 지나면 새로고침 없이 제외하고, 다른 탭에서 돌아온 순간에도 현재 시각을 반영한다.
  useEffect(() => {
    const updateNow = () => setNow(Date.now());
    const timer = setInterval(updateNow, 1000);
    document.addEventListener("visibilitychange", updateNow);
    window.addEventListener("focus", updateNow);
    return () => {
      clearInterval(timer);
      document.removeEventListener("visibilitychange", updateNow);
      window.removeEventListener("focus", updateNow);
    };
  }, []);

  useEffect(() => {
    if (!open) return;
    const onDown = (event: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(event.target as Node)) setOpen(false);
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setOpen(false);
        toggleRef.current?.focus();
      }
    };
    document.addEventListener("mousedown", onDown);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const todayItems = state.status === "loaded" ? state.items : [];
  const items = todayItems.filter((item) => Date.parse(item.scheduledAt) > now);
  const next = items[0];
  const showList = open && items.length > 0;
  const emptyText = todayItems.length ? "오늘 남은 상담이 없어요." : "오늘 예정된 상담이 없어요.";

  return (
    <section ref={boxRef} aria-label="오늘 상담" className={"group relative flex min-w-0 flex-col gap-3 rounded-[10px] border border-(--coord-line) bg-(--coord-surface) px-5 pt-5 pb-4 shadow-(--coord-shadow) transition " + KPI_HOVER_CLASS + (showList ? " z-20" : "")}>
      <Link href={next ? "/coordinator/help-requests?id=" + next.id : "/coordinator/help-requests"} aria-label="오늘 상담 요청 열기" className="absolute inset-0 z-0 overflow-hidden rounded-[10px] focus-visible:outline-3 focus-visible:outline-offset-3 focus-visible:outline-(--coord-green)">
        <span aria-hidden="true" className={"pointer-events-none absolute inset-x-0 bottom-0 h-[3px] opacity-0 transition group-hover:opacity-100 " + KPI_TONE.gray.bar} />
      </Link>
      <div className="flex items-start justify-between gap-2">
        <h2 className="text-[17px] font-bold text-(--coord-muted)">오늘 상담</h2>
        <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-(--coord-gray-soft) text-(--coord-gray)">
          <Icon name="calendar" className="size-[18px]" />
        </span>
      </div>
      <div className="flex items-end justify-between gap-2">
        <p aria-live="polite" className="text-[40px] leading-none font-black tracking-[-1px]">
          {state.status === "loaded" ? <>{items.length}<small className="ml-0.5 text-lg font-bold tracking-normal text-(--coord-muted)">건 남음</small></> : <span className="text-(--coord-faint)">—</span>}
        </p>
        {state.status === "error" ? (
          <button type="button" onClick={() => { setState({ status: "loading" }); setAttempt((value) => value + 1); }} className="relative z-10 py-1 text-[13px] font-bold text-(--coord-green) underline underline-offset-4 focus-visible:outline-2 focus-visible:outline-(--coord-green)">
            다시 시도
          </button>
        ) : (
          <button ref={toggleRef} type="button" aria-expanded={showList} aria-controls={listId} disabled={state.status !== "loaded" || items.length === 0} onClick={() => setOpen((value) => !value)} className="relative z-10 inline-flex shrink-0 items-center gap-1 py-1 text-[13px] font-bold text-(--coord-green) hover:underline focus-visible:outline-2 focus-visible:outline-(--coord-green) disabled:cursor-default disabled:text-(--coord-faint) disabled:no-underline">
            {showList ? "접기" : "시간 목록 보기"}
            <Icon name="chevron" className={"size-3.5 transition-transform" + (showList ? " rotate-180" : "")} />
          </button>
        )}
      </div>
      <div className="-mt-1 flex h-[21px] min-w-0 items-center text-[14px] font-semibold text-(--coord-muted) group-hover:text-(--coord-green)">
        {state.status === "loading" ? <p role="status">상담 일정을 불러오는 중…</p>
          : state.status === "error" ? <p role="alert" className="truncate text-(--coord-red)" title="상담 일정을 불러오지 못했어요.">상담 일정을 불러오지 못했어요.</p>
          : next ? (
            <span className="flex min-w-0 items-center gap-1.5">
              <span className="truncate">{next.studentName}</span>
              <time dateTime={next.scheduledAt} className="shrink-0">{CONSULTATION_TIME.format(new Date(next.scheduledAt))}</time>
            </span>
          ) : <p className="truncate" title={emptyText}>{emptyText}</p>}
      </div>
      {showList && state.status === "loaded" && (
        <section id={listId} aria-label="오늘 상담 시간 목록" className="absolute top-full right-0 z-20 mt-2 w-[360px] overflow-hidden rounded-[10px] border border-(--coord-line) bg-(--coord-surface) text-(--coord-ink) shadow-[0_12px_32px_rgb(0_0_0/0.14)]">
          <div className="flex items-center justify-between gap-2 border-b border-(--coord-line) px-4 py-3">
            <h3 className="text-[14px] font-bold">오늘 상담 시간 목록</h3>
            <Badge tone="green" small>{items.length}건</Badge>
          </div>
          <ul className="max-h-[320px] overflow-y-auto">
            {items.map((item) => (
              <li key={item.id} className="flex items-center gap-3 border-b border-(--coord-line) px-4 py-3 last:border-b-0">
                  <time dateTime={item.scheduledAt} className="shrink-0 text-[14px] font-extrabold text-(--coord-green)">{CONSULTATION_TIME.format(new Date(item.scheduledAt))}</time>
                  <span className="min-w-0 flex-1">
                    <b className="block truncate text-[14px]">{item.studentName}</b>
                    <span className="block truncate text-[12px] text-(--coord-muted)">{item.topic}</span>
                  </span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </section>
  );
}

function ManagementCard({ data }: { data: Dashboard }) {
  const total = data.totalStudents;
  const counts = manageCounts(data.statusSummary);
  const known = knownCount(total, data.statusSummary);
  const check = data.statusSummary.CHECK_REQUIRED;
  // 연락 대상 이름 칩: 대시보드 callList(1주 무응답) 실제 데이터에서 오래된 순으로 2명까지
  const callChips = [...data.callList].sort((a, b) => b.daysSinceResponse - a.daysSinceResponse).slice(0, 2);
  return (
    <Panel label="수료생 관리 현황">
      {/* 제목 아래 '관리 상태 파악률' 줄 없음 (같은 값은 아래 큰 %로 보여 준다). 제목만 조금 크게 · 배지와 세로 가운데 */}
      <PanelHead title={<span className="text-[19px]">수료생 관리 현황</span>}>
        <Badge tone="green">총 {total}명 관리 중</Badge>
      </PanelHead>
      <div className="p-5">
        <div className="flex items-baseline justify-between gap-3">
          <strong className="text-[32px] leading-none font-extrabold text-(--coord-green)">
            {Math.round(pct(known, total))}
            <small className="text-lg">%</small>
          </strong>
          <span className="text-right text-[13px] text-(--coord-muted)">
            상태 분류된 수료생 <b className="text-(--coord-ink)">{known}명</b> / 전체 {total}명
          </span>
        </div>
        {/* 상태 막대 (리뉴얼 .status-meter-wrap: 10px 알약) */}
        <div
          className="mt-3.5 mb-4 flex h-2.5 overflow-hidden rounded-full bg-(--coord-chart-track)"
          role="img"
          aria-label={`관리 상태 분포: ${MANAGE_ORDER.map((s) => `${manageLabels[s]} ${counts[s]}명`).join(", ")}`}
        >
          {total > 0 &&
            MANAGE_ORDER.filter((s) => counts[s] > 0).map((s) => (
              <span key={s} className="h-full" style={{ width: `${pct(counts[s], total)}%`, background: manageChartVar[s] }} />
            ))}
        </div>
        <ul className="flex flex-col gap-2.5">
          {MANAGE_ORDER.map((s) => {
            const warn = s === "CHECK_REQUIRED";
            return (
              <li key={s}>
                <Link
                  href={`/coordinator/students?status=${s}`}
                  className={`flex items-center justify-between rounded-[4px] border px-2.5 py-2 text-sm font-semibold transition hover:border-(--coord-line-strong) ${
                    warn ? "border-(--coord-orange-line) bg-(--coord-orange-soft) text-(--coord-orange)" : "border-transparent bg-(--coord-head)"
                  }`}
                >
                  <span className={`flex items-center gap-2 ${warn ? "font-bold" : ""}`}>
                    <span aria-hidden="true" className="size-2.5 rounded-full" style={{ background: manageChartVar[s] }} />
                    {manageLabels[s]}
                  </span>
                  <span className="flex items-center gap-1">
                    <b>{counts[s]}명</b> ({Math.round(pct(counts[s], total))}%)
                    <Icon name="arrow" className="size-3.5" />
                  </span>
                </Link>
              </li>
            );
          })}
        </ul>
        {/* 연락 필요 안내 (리뉴얼 .alert-callout-box) */}
        <div className="mt-3.5 rounded-md border border-(--coord-attention-line) bg-(--coord-orange-soft) p-3.5">
          <b className="flex items-center gap-1.5 text-[14.5px] font-extrabold text-(--coord-attention)">
            <Icon name="info" className="size-4" />
            후속 확인이 필요한 수료생 {check}명
          </b>
          <p className="mt-1 text-[13.5px] leading-[1.45] text-(--coord-attention)">
            최근 활동 공백 또는 추천 공고 부재 등으로 현재 상태 확인이 필요해요.
          </p>
          <div className="mt-2.5 flex flex-wrap gap-1.5">
            {callChips.map((c) => (
              <Link key={c.studentId} href={`/coordinator/students/${c.studentId}`} className={`${btn.base} ${btn.outline} px-2! py-1! text-xs!`}>
                {c.studentName} ({c.daysSinceResponse}일 전)
              </Link>
            ))}
            <Link href="/coordinator/students?status=CHECK_REQUIRED" className={`${btn.base} ${btn.outline} px-2! py-1! text-xs!`}>
              전체 {check}명 보기 <Icon name="arrow" className="size-3" />
            </Link>
          </div>
        </div>
        <Link href="/coordinator/stats" className="mt-3 inline-flex items-center gap-1.5 text-[13.5px] font-bold text-(--coord-green) hover:underline">
          통계 보기 <Icon name="arrow" className="size-3.5" />
        </Link>
      </div>
    </Panel>
  );
}
