"use client";
// 주인: FE-B — C1 오늘의 업무 (FE-B-07, 11/1 개편 목업). 데이터는 GET /api/coordinator/dashboard (docs/API.md 2-3).
// - 지표 4개: 새 도움 요청(helpRequests) · 검토 대기 서류(reviewQueue) · 후속 연락 필요(callList = 1주 무응답, 추가 확인 필요와 같은 대상)
//   · 상담 예정(도움 요청 CONSULTATION_SCHEDULED 건수, 상태별 목록 API)
// - 오늘 먼저 확인할 일: 도움 요청 + 검토 대기 서류 + 연속 패스 연락 대상(passFollowUpList)을 먼저 들어온 순으로 상위 5건 + '전체 보기' 모달.
//   reviewQueue 에 studentId 가 없어 이름으로 합치지 않고 건별 행
// - 오늘 예정된 상담: 상담 예정 도움 요청 중 상담 일시가 오늘(asOf 날짜)인 것, 시각 순
// - 서류 검토 현황 4단계: 검토 대기는 reviewQueue, 보완 필요 · 승인 완료 · 전달 완료는 상태별 서류 목록 API 로 센다. 칸을 누르면 그 탭으로
// - 수료생 관리 현황: totalStudents · statusSummary 로 계산
// 다른 탭에 갔다가 돌아오면 대시보드 · 사이드바 건수를 다시 조회한다 (그사이 들어온 도움 요청 · 제출 반영)
import Link from "next/link";
import { useEffect, useId, useRef, useState } from "react";
import { REVIEW_QUEUE_ANCHOR, notifyReviewQueueChanged } from "@/components/coordinator/CoordinatorSidebar";
import { apiErrorText, RefreshAction, Badge, Icon, LoadState, PageTitle, Panel, PanelHead, btn, type IconName } from "@/components/coordinator/CoordUi";
import { CONSULTATION_API_CONNECTED, type ConsultationSchedule } from "@/components/coordinator/demoAccount";
import type { CoordTone } from "@/components/coordinator/coordLabels";
import {
  MANAGE_ORDER,
  contactLabels,
  docLabels,
  docTone,
  helpTitle,
  helpTypeLabel,
  knownCount,
  manageChartVar,
  manageCounts,
  manageLabels,
  monthDay,
  pct,
} from "@/components/coordinator/coordLabels";
import { getCoordinatorApplications, getCoordinatorHelpRequests, getDashboard } from "@/lib/api/coordinatorReview";
import type { CoordinatorApplicationListItem, Dashboard } from "@/types/coordinator";

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

  // 상담 예정 = 도움 요청 중 CONSULTATION_SCHEDULED (상담 일시 순). 대시보드를 새로 받으면(asOf) 다시 받는다. 받기 전 · 실패면 null
  const asOf = data?.asOf ?? null;
  const [consultations, setConsultations] = useState<ConsultationSchedule[] | null>(null);
  useEffect(() => {
    if (!asOf) return;
    let ignore = false;
    getCoordinatorHelpRequests("CONSULTATION_SCHEDULED")
      .then((list) => {
        if (ignore) return;
        setConsultations(
          list
            .filter((h) => h.consultationAt)
            .map((h) => ({
              id: h.helpRequestId,
              studentId: h.studentId,
              studentName: h.studentName,
              scheduledAt: h.consultationAt as string,
              topic: helpTypeLabel(h.helpType),
            }))
            .sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt)),
        );
      })
      .catch(() => {
        if (!ignore) setConsultations(null);
      });
    return () => {
      ignore = true;
    };
  }, [asOf]);
  const today = asOf?.slice(0, 10) ?? "";
  const todayConsultations = (consultations ?? []).filter((c) => c.scheduledAt.slice(0, 10) === today);

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
    // 리뉴얼 C1: 제목 → KPI 4개 → 좌(할 일 · 상담 · 서류 현황) 1.55 : 우(수료생 관리 현황) 1. 높이는 내용만큼 (페이지 스크롤 허용)
    <div>
      <PageTitle
        title="오늘의 업무"
        action={
          <RefreshAction busy={refreshing} error={refreshError} onRefresh={refresh}>
            <span className={`${btn.base} ${btn.outline} ${btn.sm} cursor-default hover:border-(--coord-line) hover:bg-(--coord-surface)`}>
              <Icon name="calendar" className="size-3.5" />
              {data.asOf.slice(0, 10).replaceAll("-", ".")} 기준
            </span>
          </RefreshAction>
        }
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
          label="후속 연락 필요"
          value={data.callList.length}
          unit="명"
          sub="1주 무응답 · 전화 확인"
          href="/coordinator/students?status=CHECK_REQUIRED"
          icon="phone"
          tone="blue"
        />
        {/* 상담 예정 도움 요청 건수. 받기 전 · 실패면 '—' (가짜 건수 없음). 누르면 가장 빠른 상담 요청이 열린다 */}
        <Kpi
          label="상담 예정"
          value={CONSULTATION_API_CONNECTED && consultations ? consultations.length : null}
          unit="건"
          sub={consultations ? `오늘 ${todayConsultations.length}건 · 상담 일시 순` : "상담 일정을 불러오는 중"}
          href={consultations?.[0] ? `/coordinator/help-requests?id=${consultations[0].id}` : "/coordinator/help-requests"}
          icon="calendar"
          tone="gray"
        />
      </div>
      <div className="grid grid-cols-[minmax(0,1.55fr)_minmax(340px,1fr)] items-start gap-5">
        <div className="flex min-w-0 flex-col gap-5">
          <Tasks data={data} />
          <div className="grid grid-cols-2 gap-4">
            <TodayConsultations items={todayConsultations} loaded={consultations !== null} />
            <DocStatus count={data.reviewQueue.length} asOf={data.asOf} />
          </div>
        </div>
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
    href ? "hover:-translate-y-px hover:border-(--coord-line-strong) hover:shadow-[0_4px_16px_rgb(18_107_86/0.08)]" : ""
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
  doc: { badge: "서류 검토", tone: "blue", when: "제출", action: "서류 검토" },
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
    <Panel label="오늘 먼저 확인할 일">
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
        <p className="p-9 text-center text-[15px] text-(--coord-muted)">지금 확인할 업무가 없어요.</p>
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

/**
 * 오늘 예정된 상담 (목업 .agenda). 도움 요청 중 상담 예정(CONSULTATION_SCHEDULED)이고 상담 일시가 오늘인 것, 시각 순.
 * 시각을 누르면 그 도움 요청, '기록'은 수료생 상담 기록으로 간다
 */
function TodayConsultations({ items, loaded }: { items: ConsultationSchedule[]; loaded: boolean }) {
  return (
    <Panel label="오늘 예정된 상담" className="flex flex-col">
      <PanelHead title="오늘 예정된 상담">
        {!CONSULTATION_API_CONNECTED ? <Badge>일정 미연동</Badge> : loaded && <Badge tone="green">오늘 {items.length}건</Badge>}
      </PanelHead>
      {items.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-1 p-5 text-center">
          <p className="text-[15px] text-(--coord-muted)">{loaded ? "오늘 예정된 상담이 없어요." : "상담 일정을 불러오는 중이에요…"}</p>
          <p className="text-[13px] text-(--coord-faint)">도움 요청에서 &lsquo;상담 예정&rsquo;으로 일시를 잡으면 여기에 보여요</p>
        </div>
      ) : (
        // 리뉴얼: 상담 한 건 = 회색 상자 (시각 · 이름 · 도움 유형 + 요청 열기 · 기록)
        <ul className="flex max-h-[260px] flex-col gap-2.5 overflow-y-auto p-5">
          {items.map((c) => (
            <li key={c.id} className="rounded-md border border-(--coord-line) bg-(--coord-head) p-3.5">
              <div className="flex items-center justify-between gap-2">
                <b className="truncate text-[15px] text-(--coord-green)">
                  <time dateTime={c.scheduledAt}>{c.scheduledAt.slice(11, 16)}</time> · {c.studentName}
                </b>
                {c.topic && (
                  <Badge tone="blue" small>
                    {c.topic}
                  </Badge>
                )}
              </div>
              <div className="mt-2.5 flex justify-end gap-2">
                <Link href={`/coordinator/students/${c.studentId}`} className={`${btn.base} ${btn.outline} ${btn.sm}`}>
                  기록
                </Link>
                <Link href={`/coordinator/help-requests?id=${c.id}`} className={`${btn.base} ${btn.primary} ${btn.sm}`}>
                  요청 열기
                </Link>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}

/**
 * 서류 검토 현황 4단계 (목업 .doc-counts). 검토 대기는 대시보드 reviewQueue, 나머지 3단계는 상태별 목록 API(ALL)로 센다.
 * 목록을 받기 전 · 못 받으면 '—' (0건으로 단정하지 않음). 칸을 누르면 서류 검토 화면의 그 탭이 열린다.
 * asOf 가 바뀌면(대시보드 새로고침) 다시 센다
 */
/** 서류 단계 숫자 색 (리뉴얼: 검토 대기 앰버 · 보완 필요 빨강 · 승인 완료 파랑 · 전달 완료 초록) */
const NUM_COLOR: Record<CoordTone, string> = {
  orange: "text-(--coord-chart-check)",
  red: "text-(--coord-red)",
  blue: "text-(--coord-blue)",
  green: "text-(--coord-green)",
  gray: "text-(--coord-muted)",
};

function DocStatus({ count, asOf }: { count: number; asOf: string }) {
  const [list, setList] = useState<CoordinatorApplicationListItem[] | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    let ignore = false;
    getCoordinatorApplications("ALL")
      .then((next) => {
        if (!ignore) {
          setList(next);
          setFailed(false);
        }
      })
      .catch(() => {
        if (!ignore) setFailed(true);
      });
    return () => {
      ignore = true;
    };
  }, [asOf]);
  const countOf = (status: CoordinatorApplicationListItem["status"]) => (list ? list.filter((a) => a.status === status).length : null);
  const stages: { status: CoordinatorApplicationListItem["status"]; count: number | null; tone: CoordTone }[] = [
    { status: "SUBMITTED", count, tone: docTone.SUBMITTED },
    { status: "REJECTED", count: countOf("REJECTED"), tone: docTone.REJECTED },
    { status: "APPROVED", count: countOf("APPROVED"), tone: docTone.APPROVED },
    { status: "DELIVERED", count: countOf("DELIVERED"), tone: docTone.DELIVERED },
  ];
  const pendingText = failed ? "조회 실패" : "불러오는 중";
  return (
    <Panel label="서류 검토 현황">
      <div id={REVIEW_QUEUE_ANCHOR} className="scroll-mt-20">
        <PanelHead title="서류 검토 현황">
          <Link href="/coordinator/applications" className={`${btn.base} ${btn.outline} ${btn.sm}`}>
            전체 <Icon name="arrow" className="size-3.5" />
          </Link>
        </PanelHead>
      </div>
      {/* 리뉴얼 .doc-mini-box: 회색 칸 + 상태색 숫자. 검토 대기 칸만 앰버로 강조 */}
      <ul className="grid grid-cols-4 gap-2.5 p-5">
        {stages.map((s) => {
          const box = (
            <>
              <b className={`text-xl leading-[1.3] font-extrabold ${s.count === null ? "text-(--coord-faint)" : NUM_COLOR[s.tone]}`}>{s.count ?? "—"}</b>
              <span className="mt-0.5 text-xs leading-tight tracking-[-0.3px] break-keep text-(--coord-muted)">
                {docLabels[s.status]}
                {s.count === null && <span className="sr-only"> ({pendingText})</span>}
              </span>
              {s.count === null && (
                <span aria-hidden="true" className="text-[11px] leading-none text-(--coord-faint)">
                  {pendingText}
                </span>
              )}
            </>
          );
          const cls = `flex flex-col items-center rounded-[4px] border px-1 py-2.5 text-center transition hover:border-(--coord-line-strong) ${
            s.status === "SUBMITTED" ? "border-(--coord-orange-line) bg-(--coord-orange-soft)" : "border-(--coord-line) bg-(--coord-head)"
          }`;
          return (
            <li key={s.status}>
              <Link href={`/coordinator/applications?status=${s.status}`} className={cls}>
                {box}
              </Link>
            </li>
          );
        })}
      </ul>
    </Panel>
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
      <PanelHead title="수료생 관리 현황" sub={<>관리 상태 파악률: <b className="text-(--coord-ink)">{pct(known, total).toFixed(1)}%</b></>}>
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
            직접 연락이 필요한 수료생 {check}명 / 전체 {total}명
          </b>
          <p className="mt-1 text-[13.5px] leading-[1.45] text-(--coord-attention)">
            1주 넘게 앱 응답이 없어요. 나머지 {known}명은 앱 응답이나 도움 요청으로 상태가 분류돼 있어요.
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
