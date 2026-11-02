"use client";
// 주인: FE-B — C1 오늘의 업무 (FE-B-07, 11/1 개편 목업). 데이터는 GET /api/coordinator/dashboard (docs/API.md 2-3).
// - 지표 4개: 새 도움 요청(helpRequests) · 검토 대기 서류(reviewQueue) · 후속 연락 필요(callList = 1주 무응답, 추가 확인 필요와 같은 대상)
//   · 상담 예정(상담 일정 API 없음 → '미연동' 표시, 가짜 건수 없음)
// - 오늘 먼저 확인할 일: 도움 요청 + 검토 대기 서류를 먼저 들어온 순으로 상위 5건 + '전체 보기' 모달.
//   reviewQueue 에 studentId 가 없어 이름으로 합치지 않고 건별 행
// - 오늘 예정된 상담: 일정 API 연동 전이라 빈 상태 (demoAccount.ts 의 CONSULTATION_API_CONNECTED)
// - 서류 검토 현황 4단계: 검토 대기만 실제 건수, 나머지 3단계는 건수 API 가 없어 '—' · 미연동 (0건으로 단정하지 않음)
// - 수료생 관리 현황: totalStudents · statusSummary 로 계산
// 다른 탭에 갔다가 돌아오면 대시보드 · 사이드바 건수를 다시 조회한다 (그사이 들어온 도움 요청 · 제출 반영)
import Link from "next/link";
import { useEffect, useId, useRef, useState } from "react";
import { REVIEW_QUEUE_ANCHOR, notifyReviewQueueChanged } from "@/components/coordinator/CoordinatorSidebar";
import { Badge, Icon, LoadState, PageTitle, Panel, PanelHead, btn, toneClass, type IconName } from "@/components/coordinator/CoordUi";
import { CONSULTATION_API_CONNECTED, type ConsultationSchedule } from "@/components/coordinator/demoAccount";
import type { CoordTone } from "@/components/coordinator/coordLabels";
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

  // 상담 일정 조회 API 가 아직 없어서 비어 있다. 연동되면 오늘 일정을 여기에 넣는다 (가짜 일정 없음)
  const consultations: ConsultationSchedule[] = [];

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
    <div className="flex min-h-[680px] flex-col lg:h-[calc(100vh-140px)]">
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
      <div className="mb-3.5 grid shrink-0 grid-cols-4 gap-3.5">
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
        {/* 상담 일정 조회 API 가 없어서 미연동 표시 (가짜 건수 없음). 연동되면 consultations 길이로 바꾼다 */}
        <Kpi
          label="상담 예정"
          value={CONSULTATION_API_CONNECTED ? consultations.length : null}
          unit="건"
          sub={CONSULTATION_API_CONNECTED ? "예약된 상담 준비" : "상담 일정 기능 연결 전"}
          icon="calendar"
        />
      </div>
      <div className="grid min-h-0 flex-1 grid-cols-[minmax(0,1.8fr)_minmax(300px,1fr)] gap-3.5">
        <div className="flex min-h-0 flex-col gap-[11px]">
          <Tasks data={data} />
          <div className="grid shrink-0 grid-cols-2 gap-3">
            <TodayConsultations items={consultations} />
            <DocStatus count={data.reviewQueue.length} />
          </div>
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
  /** null: 연동된 API 가 없어 건수를 모름 ('—' 표시) */
  value: number | null;
  unit: string;
  sub: string;
  /** 없으면 이동하지 않는 카드 */
  href?: string;
  icon: IconName;
  urgent?: boolean;
}) {
  const className = `relative rounded-xl border px-5 py-4 text-left transition focus-visible:outline-3 focus-visible:outline-offset-3 focus-visible:outline-(--coord-green) ${
    urgent
      ? "border-(--coord-attention-line) bg-(--coord-attention-soft) hover:border-(--coord-orange)"
      : href
        ? "border-(--coord-line) bg-(--coord-surface) hover:border-(--coord-green)"
        : "border-(--coord-line) bg-(--coord-surface)"
  }`;
  const body = (
    <>
      <span className="text-xs font-semibold">{label}</span>
      <span
        className={`absolute top-[15px] right-[17px] grid size-[30px] place-items-center rounded-lg ${
          urgent ? "bg-(--coord-orange-soft) text-(--coord-orange)" : "bg-(--coord-green-soft) text-(--coord-green-ink)"
        }`}
      >
        <Icon name={icon} />
      </span>
      <span className="mt-[11px] mb-1.5 block text-[31px] leading-[1.2] font-bold tracking-[-1.5px]">
        {value ?? <span className="text-(--coord-muted)">—</span>}
        {value !== null && <small className="ml-[5px] text-[13px] font-medium tracking-normal text-(--coord-muted)">{unit}</small>}
      </span>
      <span className="flex items-center gap-1 text-[11px] text-(--coord-muted)">
        {value === null && <Badge>미연동</Badge>} {sub} {href && <Icon name="arrow" className="size-3.5" />}
      </span>
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

/** 할 일 한 줄 (목업 .task): 아이콘 · 이름 · 종류 · 내용 · 접수/제출 시각 · 이동 버튼 */
function TaskRow({ t }: { t: Task }) {
  return (
    <li className="flex items-center gap-2.5 border-b border-(--coord-line-soft) px-4 py-2 last:border-b-0">
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
  );
}

function Tasks({ data }: { data: Dashboard }) {
  const all = buildTasks(data);
  const rows = all.slice(0, TASK_LIMIT);
  const [showAll, setShowAll] = useState(false);
  return (
    <Panel label="오늘 먼저 확인할 일" className="flex min-h-0 flex-1 flex-col">
      <PanelHead
        title={
          <>
            오늘 먼저 확인할 일 <Badge tone="green">총 {all.length}건</Badge>
          </>
        }
      >
        <span className="flex items-center gap-3">
          <span className="text-xs text-(--coord-muted)">먼저 들어온 순 · 상위 {rows.length}건</span>
          {all.length > 0 && (
            <button type="button" onClick={() => setShowAll(true)} className="flex items-center gap-1.5 text-xs font-semibold text-(--coord-green)">
              전체 보기 <Icon name="arrow" className="size-4" />
            </button>
          )}
        </span>
      </PanelHead>
      {rows.length === 0 ? (
        <p className="p-[35px] text-center text-[13px] text-(--coord-muted)">지금 확인할 업무가 없어요.</p>
      ) : (
        <ul className="min-h-0 flex-1 overflow-y-auto">
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
        className="flex max-h-[90vh] w-[720px] flex-col overflow-hidden rounded-[14px] border border-(--coord-line) bg-(--coord-surface) outline-none"
      >
        <div className="flex items-center justify-between gap-3 border-b border-(--coord-line-soft) px-[22px] py-4">
          <h2 id={titleId} className="flex items-center gap-2 text-[17px] font-bold">
            오늘 확인할 일 전체 <Badge tone="green">{tasks.length}건</Badge>
          </h2>
          <button type="button" onClick={onClose} aria-label="닫기" className="grid size-9 place-items-center rounded-lg hover:bg-(--coord-hover)">
            <Icon name="close" />
          </button>
        </div>
        <p className="px-[22px] pt-3 text-xs text-(--coord-muted)">도움 요청과 검토 대기 서류를 먼저 들어온 순으로 모두 보여 줘요.</p>
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
 * 오늘 예정된 상담 (목업 .agenda). 상담 일정 조회 API 가 없어서 지금은 빈 상태.
 * 연동되면 items 에 오늘 일정을 넘기면 된다 (가짜 일정을 실제 데이터처럼 보이지 않게)
 */
function TodayConsultations({ items }: { items: ConsultationSchedule[] }) {
  return (
    <Panel label="오늘 예정된 상담" className="flex flex-col">
      <div className="flex items-center justify-between gap-3 border-b border-(--coord-line-soft) px-[18px] py-3">
        <h2 className="text-[15px] font-bold">오늘 예정된 상담</h2>
        {!CONSULTATION_API_CONNECTED && <Badge>일정 미연동</Badge>}
      </div>
      {items.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-1 px-4 py-3 text-center">
          <p className="text-[13px] text-(--coord-muted)">예정된 상담이 없습니다.</p>
          {!CONSULTATION_API_CONNECTED && <p className="text-[11px] text-(--coord-muted)">상담 일정 기능 연결 전 · 상담 내용은 수료생 상담 기록에</p>}
        </div>
      ) : (
        <ul className="max-h-[120px] overflow-y-auto">
          {items.map((c) => (
            <li key={c.id} className="flex items-start gap-3.5 border-b border-(--coord-line-soft) px-[18px] py-3 last:border-b-0">
              <time className="border-r-2 border-(--coord-tint-line) pr-3.5 text-[13px] font-bold text-(--coord-green)">{c.scheduledAt.slice(11, 16)}</time>
              <div className="min-w-0">
                <h3 className="truncate text-xs font-bold">
                  {c.studentName}
                  {c.topic && ` · ${c.topic}`}
                </h3>
              </div>
              <Link href={`/coordinator/students/${c.studentId}`} className={`${btn.base} ${btn.outline} ${btn.sm} ml-auto`}>
                기록
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}

/**
 * 서류 검토 현황 4단계 (목업 .doc-counts). 대시보드 API 는 검토 대기(reviewQueue)만 주므로
 * 나머지 3단계는 건수 API 가 없어 '—' + 미연동으로 표시한다 (0건으로 단정하지 않음). 연동되면 count 를 채운다
 */
function DocStatus({ count }: { count: number }) {
  const stages: { label: string; count: number | null; tone: CoordTone }[] = [
    { label: "검토 대기", count, tone: "orange" },
    { label: "수정 요청", count: null, tone: "red" },
    { label: "승인 · 전달 대기", count: null, tone: "blue" },
    { label: "전달 완료", count: null, tone: "green" },
  ];
  return (
    <Panel label="서류 검토 현황">
      <div id={REVIEW_QUEUE_ANCHOR} className="flex scroll-mt-20 items-center justify-between gap-3 border-b border-(--coord-line-soft) px-[18px] py-3">
        <h2 className="text-[15px] font-bold">서류 검토 현황</h2>
        <Link href="/coordinator/applications" className="flex items-center gap-1.5 text-xs font-semibold text-(--coord-green)">
          전체 <Icon name="arrow" className="size-4" />
        </Link>
      </div>
      <ul className="grid grid-cols-4 gap-2 px-3 py-2.5">
        {stages.map((s) => {
          const box = (
            <>
              <b className="text-[22px] leading-[1.2]">{s.count ?? "—"}</b>
              <span className="text-[10.5px] font-semibold tracking-[-0.4px] whitespace-nowrap">
                {s.label}
                {s.count === null && <span className="sr-only"> (건수 미연동)</span>}
              </span>
              {s.count === null && (
                <span aria-hidden="true" className="text-[10px] leading-none opacity-80">
                  미연동
                </span>
              )}
            </>
          );
          const cls = `flex flex-col items-center gap-1 rounded-[9px] px-1 py-2 text-center ${toneClass(s.tone)} ${s.count === null ? "opacity-70" : "hover:brightness-95"}`;
          return (
            <li key={s.label}>
              {s.count !== null ? (
                <Link href="/coordinator/applications" className={cls}>
                  {box}
                </Link>
              ) : (
                <div className={cls} title="이 단계의 건수를 주는 API 가 아직 없어요">
                  {box}
                </div>
              )}
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
  return (
    <section aria-label="수료생 관리 현황" className="flex min-h-0 flex-col overflow-y-auto rounded-xl border border-(--coord-tint-line) bg-(--coord-tint) px-4 py-3">
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
