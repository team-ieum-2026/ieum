"use client";
// 주인: FE-B — 도움 요청 목록 · 상세 · 처리 기록 (11/1 개편 목업 requestsPage).
// 데이터는 상태별 목록 API(GET /api/coordinator/help-requests)를 ALL 로 한 번 받아 처리 중(접수 포함) · 상담 예정 · 연락 완료(요청 종료 포함) 탭으로 나눈다.
// C5 시안: 상세는 기본 정보 → 요청 내용 → '요청 처리' 카드(처리 상태 · 상담 예정일 · 저장 / 후속 업무) → 처리 이력 · 상담 기록 2열
// - 처리 상태 바꾸기: 진행 중 요청(접수 · 처리 중 · 상담 예정)을 처리 중 · 상담 예정(상담 일시 필수)으로 PATCH. 저장 뒤 그 탭으로 이동
// - 전화 · 문자 · 이메일은 수료생이 앱에서 고른 '선호 연락 수단', memo 는 수료생이 남긴 요청 내용
// - '연락 완료' → 확인 창 → PATCH /api/coordinator/help-requests/{id} { status: CONTACTED } 성공 뒤에만 목록 · 건수 갱신
//   CONTACTED 는 '연락 완료'이지 '요청 종료(CLOSED)'가 아니다. 요청 종료는 수료생이 앱에서 한다. 이 API 는 메모를 받지 않아 메모를 저장하는 척하지 않는다
// - 연락처(이메일) · 수료생 상담 기록은 C3 상세 API(GET)로 보여 준다. 상담 기록은 수료생 단위라 이 요청과 연결된 이력이 아니다
// - 재요청 여부: 같은 수료생이 이 요청보다 먼저 남긴 요청 수 (상태 무관)
// - 목업 모드는 목록 API JSON 이 없어 대시보드 helpRequests(접수)만 보인다
// 미연동(BE 회신 전): 처리 불가 상태, 요청별 처리 메모 · 후속 연락일 · 단계별 처리 시각, 담당자, 전화번호
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useId, useRef, useState } from "react";
import { notifyReviewQueueChanged } from "@/components/coordinator/CoordinatorSidebar";
import { apiErrorText, RefreshAction, Badge, Icon, LoadState, PageTitle, Panel, btn, toneClass, type IconName } from "@/components/coordinator/CoordUi";
import {
  closeReasonLabels,
  contactLabels,
  courseName,
  helpLabels,
  helpTitle,
  helpTone,
  helpTypeLabel,
  monthDayTime,
} from "@/components/coordinator/coordLabels";
import { IS_MOCK, MOCK_SAVE_NOTE } from "@/components/coordinator/demoAccount";
import { ApiError } from "@/lib/api/client";
import { getCoordinatorHelpRequests, updateHelpRequestStatus } from "@/lib/api/coordinatorReview";
import { getCoordinatorStudent } from "@/lib/api/coordinatorStudents";
import ContactLogDialog from "@/components/coordinator/ContactLogDialog";
import type { CoordinatorHelpRequest, HelpType } from "@/types/coordinator";
import { ACTIVE_HELP_STATUSES, type ContactLog, type ContactMethod, type ContactType, type HelpRequestStatus, type StudentHelpRequestStatus } from "@/types/student";

const CHANNELS: (ContactMethod | "ALL")[] = ["ALL", "PHONE", "SMS", "EMAIL"];
const channelIcon: Record<ContactMethod, IconName> = { PHONE: "phone", SMS: "sms", EMAIL: "mail" };
const contactTypeLabels: Record<ContactType, string> = { PHONE: "전화", MESSAGE: "문자", MEETING: "면담" };

/**
 * 상태 탭 3개 (C5 시안): 처리 중 · 상담 예정 · 연락 완료.
 * 요청을 숨기지 않도록 '처리 중' 탭에 접수(OPEN)를, '연락 완료' 탭에 요청 종료(CLOSED)를 함께 담는다.
 * 각 요청의 실제 상태는 카드 · 상세의 상태 배지로 그대로 보인다 (접수를 처리 중으로 바꿔 보이지 않음)
 */
type StatusTab = "IN_PROGRESS" | "CONSULTATION_SCHEDULED" | "CONTACTED";
const STATUS_TABS: StatusTab[] = ["IN_PROGRESS", "CONSULTATION_SCHEDULED", "CONTACTED"];
const TAB_STATUSES: Record<StatusTab, StudentHelpRequestStatus[]> = {
  IN_PROGRESS: ["OPEN", "IN_PROGRESS"],
  CONSULTATION_SCHEDULED: ["CONSULTATION_SCHEDULED"],
  CONTACTED: ["CONTACTED", "CLOSED"],
};
const tabOf = (status: StudentHelpRequestStatus): StatusTab =>
  status === "CONSULTATION_SCHEDULED" ? "CONSULTATION_SCHEDULED" : status === "CONTACTED" || status === "CLOSED" ? "CONTACTED" : "IN_PROGRESS";

const EMPTY_TEXT: Record<StatusTab, string> = {
  IN_PROGRESS: "접수 · 처리 중인 도움 요청이 없어요.",
  CONSULTATION_SCHEDULED: "상담이 잡힌 도움 요청이 없어요.",
  CONTACTED: "연락을 마친 도움 요청이 없어요.",
};

/** 확인 창을 거치는 상태 변경: 연락 완료 · 연락 완료에서 처리 중으로 되돌리기 */
type ConfirmTarget = "CONTACTED" | "IN_PROGRESS";

/** 연락 완료 전 진행 중 요청 (접수 · 처리 중 · 상담 예정): 처리 단계를 바꾸거나 연락 완료로 넘길 수 있다 */
const isActiveHelp = (r: CoordinatorHelpRequest) => ACTIVE_HELP_STATUSES.includes(r.status);

/** 서울 달력 날짜 "YYYY-MM-DD" */
const seoulDate = (d: Date) => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).format(d);
/**
 * 상담 예정 D-day (수정사항4): 서울 달력 날짜 차이로 센다 (시각 무관). 오늘 D-day · 앞으로 D-n · 지남 D+n.
 * 상담 예정이 아니거나 상담 일시가 없으면 null. 지나도 상태는 바꾸지 않는다 (표시만)
 */
const consultationDday = (r: CoordinatorHelpRequest): string | null => {
  if (r.status !== "CONSULTATION_SCHEDULED" || !r.consultationAt) return null;
  const at = new Date(r.consultationAt);
  if (Number.isNaN(at.getTime())) return null;
  const toDay = (ymd: string) => Date.UTC(Number(ymd.slice(0, 4)), Number(ymd.slice(5, 7)) - 1, Number(ymd.slice(8, 10)));
  const diff = Math.round((toDay(seoulDate(at)) - toDay(seoulDate(new Date()))) / 86_400_000);
  return diff === 0 ? "D-day" : diff > 0 ? `D-${diff}` : `D+${-diff}`;
};
/** 상태 배지 색: 상담 예정 당일(D-day) 주황 · 지남(D+n) 빨강 · 그 밖은 상태 색 그대로 (목록 · 상세 같은 규칙) */
const statusTone = (r: CoordinatorHelpRequest) => {
  const dday = consultationDday(r);
  return dday === "D-day" ? "orange" : dday?.startsWith("D+") ? "red" : helpTone[r.status];
};
/** 상태 배지 글자: 상담 예정이면 D-day 를 붙인다 (예: 상담 예정 D-3) */
const statusText = (r: CoordinatorHelpRequest) => {
  const dday = consultationDday(r);
  return dday ? `${helpLabels[r.status]} ${dday}` : helpLabels[r.status];
};

/** 선택한 요청의 수료생 연락처 · 상담 기록 (C3 상세 API) */
type StudentInfo = { state: "error" } | { state: "ok"; email: string | null; logs: ContactLog[] };

export default function HelpRequestsScreen() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const selectedId = Number(searchParams.get("id")) || null;

  const [list, setList] = useState<CoordinatorHelpRequest[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  // 고른 탭. 고르기 전에는 ?id 요청의 상태 탭(대시보드 · 알림에서 들어온 경우), 없으면 처리 중(접수 포함)
  const [pickedTab, setTab] = useState<StatusTab | null>(null);
  const [channel, setChannel] = useState<ContactMethod | "ALL">("ALL");
  const [infos, setInfos] = useState<Record<number, StudentInfo>>({});
  const [confirming, setConfirming] = useState<{ request: CoordinatorHelpRequest; target: ConfirmTarget } | null>(null);
  // C5 에서 바로 여는 상담 기록 추가 창 (C3 와 같은 창 · 같은 API)
  const [logFor, setLogFor] = useState<{ studentId: number; name: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [done, setDone] = useState<{ name: string; studentId: number } | null>(null);
  const [saved, setSaved] = useState<string | null>(null);

  // 수동 '목록 새로고침': 선택한 요청(?id) · 선호 연락 필터는 그대로 두고 목록만 바꾼다. 실패하면 지금 목록을 그대로 둔다
  const [refreshing, setRefreshing] = useState(false);
  const [refreshError, setRefreshError] = useState<string | null>(null);

  const refresh = async () => {
    if (refreshing) return;
    setRefreshing(true);
    setRefreshError(null);
    try {
      setList(await getCoordinatorHelpRequests("ALL"));
      setInfos({}); // 선택한 수료생의 연락처 · 상담 기록도 다시 받는다
      notifyReviewQueueChanged(); // 사이드바 · 상단 알림 건수도 같은 시점으로
    } catch (e) {
      setRefreshError(apiErrorText(e, "도움 요청을 다시 불러오지 못했어요."));
    } finally {
      setRefreshing(false);
    }
  };

  useEffect(() => {
    let ignore = false;
    getCoordinatorHelpRequests("ALL")
      .then((next) => {
        if (!ignore) setList(next);
      })
      .catch((e: unknown) => {
        if (!ignore) setError(apiErrorText(e, "도움 요청을 불러오지 못했어요."));
      });
    return () => {
      ignore = true;
    };
  }, [reloadKey]);

  const selectedStatus = list?.find((r) => r.helpRequestId === selectedId)?.status;
  const tab: StatusTab = pickedTab ?? (selectedStatus ? tabOf(selectedStatus) : "IN_PROGRESS");
  const inTab = sortRequests((list ?? []).filter((r) => TAB_STATUSES[tab].includes(r.status)), tab);
  const filtered = inTab.filter((r) => channel === "ALL" || r.contactMethod === channel);
  const selected = filtered.find((r) => r.helpRequestId === selectedId) ?? filtered[0] ?? null;
  const selectedStudentId = selected?.studentId ?? null;
  const info = selectedStudentId === null ? undefined : infos[selectedStudentId];

  // 선택한 요청의 수료생 연락처 · 상담 기록을 한 번만 불러온다 (읽기만)
  useEffect(() => {
    if (selectedStudentId === null || infos[selectedStudentId]) return;
    let ignore = false;
    getCoordinatorStudent(selectedStudentId)
      .then((d) => {
        if (!ignore) setInfos((m) => ({ ...m, [selectedStudentId]: { state: "ok", email: d.student.email, logs: d.contactLogs } }));
      })
      .catch(() => {
        if (!ignore) setInfos((m) => ({ ...m, [selectedStudentId]: { state: "error" } }));
      });
    return () => {
      ignore = true;
    };
  }, [selectedStudentId, infos]);

  if (!list) {
    return (
      <>
        <Title />
        <LoadState
          error={error}
          loadingText="도움 요청을 불러오는 중이에요…"
          onRetry={() => {
            setError(null);
            setReloadKey((k) => k + 1);
          }}
        />
      </>
    );
  }

  const select = (id: number) => router.replace(`${pathname}?id=${id}`, { scroll: false });
  const pickTab = (next: StatusTab) => {
    setTab(next);
    if (selectedId !== null) router.replace(pathname, { scroll: false }); // 다른 탭의 요청 선택은 풀고 첫 요청을 보여 준다
  };
  /** 같은 수료생이 접수 상태로 남긴 요청 수 */
  const openCount = (studentId: number) => list.filter((r) => r.studentId === studentId && r.status === "OPEN").length;
  /** 같은 수료생이 이 요청보다 먼저 남긴 요청 수 (상태 무관) = 재요청 여부 */
  const earlierCount = (r: CoordinatorHelpRequest) =>
    list.filter((o) => o.studentId === r.studentId && o.helpRequestId !== r.helpRequestId && o.createdAt < r.createdAt).length;

  const closeConfirm = () => {
    if (busy) return;
    setConfirming(null);
    setActionError(null);
  };

  /** 연락 완료(CONTACTED) 또는 연락 완료 → 처리 중(IN_PROGRESS) 되돌리기. 서버는 요청 종료(CLOSED)만 막는다 */
  const changeStatus = async (r: CoordinatorHelpRequest, target: ConfirmTarget) => {
    setBusy(true);
    setActionError(null);
    try {
      await updateHelpRequestStatus(r.helpRequestId, { status: target });
      setConfirming(null);
      setNotice(null);
      if (target === "CONTACTED") {
        setSaved(null);
        setDone({ name: r.studentName, studentId: r.studentId });
        router.replace(pathname, { scroll: false });
      } else {
        // 되돌린 요청은 선택을 그대로 두고 처리 중 탭으로 옮긴다
        setDone(null);
        setSaved(`${r.studentName} 님 요청을 '처리 중'(으)로 되돌렸어요.`);
        setTab("IN_PROGRESS");
      }
      setReloadKey((k) => k + 1);
      notifyReviewQueueChanged();
    } catch (e) {
      if (e instanceof ApiError && (e.status === 404 || e.status === 409)) {
        // 이미 다른 곳에서 처리된 요청: 창을 닫고 목록을 새로 불러온다
        setConfirming(null);
        setNotice("이미 처리됐거나 없는 요청이에요. 목록을 새로 불러왔어요.");
        setReloadKey((k) => k + 1);
        notifyReviewQueueChanged();
      } else {
        // 실패하면 상태는 그대로 '접수'. 창에 오류를 보여 주고 다시 시도할 수 있게 둔다
        setActionError(
          e instanceof ApiError ? e.message : `${helpLabels[target]}(으)로 바꾸지 못했어요. 요청 상태는 그대로 '${helpLabels[r.status]}'예요. 다시 시도해 주세요.`,
        );
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <Title action={<RefreshAction busy={refreshing} error={refreshError} onRefresh={refresh} />} />
      {done && (
        <p role="status" className="mb-4 flex items-center justify-between gap-3 rounded-md border border-(--coord-green-line) bg-(--coord-green-soft) px-4 py-3 text-sm text-(--coord-green-ink)">
          <span>
            {done.name} 님 요청을 <b>연락 완료</b>로 바꿨어요. 상담 내용은 수료생 상담 기록에 남겨 주세요.
            {IS_MOCK && <b className="ml-2 text-(--coord-attention)">{MOCK_SAVE_NOTE}</b>}
          </span>
          <button type="button" onClick={() => setLogFor({ studentId: done.studentId, name: done.name })} className={`${btn.base} ${btn.outline} ${btn.sm}`}>
            상담 기록 쓰기
          </button>
        </p>
      )}
      {saved && (
        <p role="status" className="mb-4 rounded-md border border-(--coord-green-line) bg-(--coord-green-soft) px-4 py-3 text-sm text-(--coord-green-ink)">
          {saved}
          {IS_MOCK && <b className="ml-2 text-(--coord-attention)">{MOCK_SAVE_NOTE}</b>}
        </p>
      )}
      {notice && (
        <p role="status" className="mb-4 rounded-md border border-(--coord-attention-line) bg-(--coord-attention-soft) px-4 py-3 text-sm text-(--coord-attention)">
          {notice}
        </p>
      )}
      {/* 리뉴얼 .inbox-split-layout: 왼쪽 목록(화면에 붙어 따라오고 안에서 스크롤) : 오른쪽 상세(내용만큼 길어져 긴 요청 · 기록도 잘리지 않음).
          1차 피드백: 목록 35% · 상세 65% */}
      <div className="grid grid-cols-[minmax(0,35fr)_minmax(0,65fr)] items-start gap-5">
        <Panel label="도움 요청 목록" className="sticky top-[76px] flex max-h-[calc(100vh-96px)] flex-col">
          <div role="tablist" aria-label="요청 상태" className="flex shrink-0 flex-wrap gap-1.5 border-b border-(--coord-line) px-4 py-3.5">
            {STATUS_TABS.map((status) => (
              <button
                key={status}
                type="button"
                role="tab"
                aria-selected={status === tab}
                onClick={() => pickTab(status)}
                className={`${btn.base} ${status === tab ? btn.primary : btn.outline} ${btn.sm} ${btn.ctl}`}
              >
                {helpLabels[status]}{" "}
                {/* 목업 목록은 접수만 만들어 다른 상태가 0건처럼 보이므로 목업 모드에서는 '—' (실제 BE 에서는 실제 건수) */}
                <span className="font-extrabold">{IS_MOCK && status !== "IN_PROGRESS" ? "—" : list.filter((r) => TAB_STATUSES[status].includes(r.status)).length}</span>
              </button>
            ))}
            {IS_MOCK && <span className="w-full text-xs text-(--coord-faint)">목업 모드는 접수 요청만 있어요</span>}
          </div>
          <div className="flex shrink-0 flex-wrap items-center gap-x-1 gap-y-1 border-b border-(--coord-line) bg-(--coord-head) px-3 py-2 text-[12.5px] text-(--coord-muted)">
            <span className="mr-1 font-semibold">선호 연락:</span>
            <div className="flex flex-wrap gap-0.5" role="group" aria-label="선호 연락 필터">
              {CHANNELS.map((ch) => (
                <button
                  key={ch}
                  type="button"
                  aria-pressed={channel === ch}
                  onClick={() => setChannel(ch)}
                  className={`rounded-[4px] px-2 py-1 focus-visible:outline-3 focus-visible:outline-(--coord-green) ${
                    channel === ch ? "bg-(--coord-surface) font-bold text-(--coord-green) shadow-(--coord-shadow)" : "hover:text-(--coord-ink)"
                  }`}
                >
                  {ch === "ALL" ? "전체" : contactLabels[ch]} {inTab.filter((r) => ch === "ALL" || r.contactMethod === ch).length}
                </button>
              ))}
            </div>
          </div>
          {filtered.length === 0 ? (
            <p className="p-9 text-center text-[15px] text-(--coord-muted)">{inTab.length === 0 ? EMPTY_TEXT[tab] : "이 조건의 요청이 없어요."}</p>
          ) : (
            <ul className="min-h-0 flex-1 overflow-y-auto">
              {filtered.map((r) => {
                const active = r.helpRequestId === selected?.helpRequestId;
                const sameStudent = openCount(r.studentId);
                return (
                  <li key={r.helpRequestId}>
                    {/* 선택 표시는 왼쪽 4px 그림자(테두리 대신)라 고를 때 글자가 밀리지 않는다 */}
                    <button
                      type="button"
                      aria-current={active ? "true" : undefined}
                      onClick={() => select(r.helpRequestId)}
                      className={`block w-full border-b border-(--coord-line) px-4 py-3.5 text-left transition focus-visible:outline-3 focus-visible:-outline-offset-3 focus-visible:outline-(--coord-green) ${
                        active ? "bg-(--coord-tint) shadow-[inset_4px_0_var(--coord-green)]" : "hover:bg-(--coord-head)"
                      }`}
                    >
                      <span className="mb-1 flex items-center justify-between gap-2">
                        <span className="flex min-w-0 items-baseline gap-2">
                          <b className="shrink-0 text-[15.5px] font-extrabold">{r.studentName}</b>
                          <span className="truncate text-xs text-(--coord-faint)">{courseName(r.courseLabel)}</span>
                        </span>
                        <Badge tone={statusTone(r)} small>
                          {statusText(r)}
                        </Badge>
                      </span>
                      <span className="block truncate text-[12.5px] font-bold text-(--coord-green)">
                        {helpTypeLabel(r.helpType)} · <span className={r.jobTitle ? "" : "font-semibold text-(--coord-muted)"}>{helpTitle(r)}</span>
                      </span>
                      <span className="mt-[3px] mb-1.5 block truncate text-[13.5px] text-(--coord-muted)">{r.memo ? `"${r.memo}"` : "남긴 메모가 없어요."}</span>
                      <span className="flex flex-wrap items-center justify-between gap-x-2 gap-y-1 text-xs text-(--coord-faint)">
                        <ChannelChip method={r.contactMethod} />
                        {r.status === "OPEN" && sameStudent > 1 && <span className="font-semibold text-(--coord-red)">같은 수료생 접수 {sameStudent}건</span>}
                        <span className="ml-auto">
                          {r.status === "CLOSED" && r.closedAt
                            ? `${monthDayTime(r.closedAt)} 종료`
                            : r.status === "CONSULTATION_SCHEDULED" && r.consultationAt
                              ? `${monthDayTime(r.consultationAt)} 상담`
                              : `${monthDayTime(r.createdAt)} 접수`}
                        </span>
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </Panel>

        <Panel label="요청 상세">
          {selected ? (
            <>
              <div className="flex items-center justify-between gap-3 border-b border-(--coord-line) px-5 py-4">
                <h2 className="flex items-center gap-2 text-[17px] font-extrabold">
                  요청 상세 <Badge tone={statusTone(selected)}>{statusText(selected)}</Badge>
                </h2>
                <Link href={`/coordinator/students/${selected.studentId}`} className={`${btn.base} ${btn.outline} ${btn.sm}`}>
                  수료생 프로필 보기
                </Link>
              </div>
              <div className="flex flex-col gap-4 p-5">
                {/* 리뉴얼: 기본 정보 2열 회색 상자 (요청자 · 과정 · 공고 · 시각 …), 아래 줄에 선호 연락 · 연락처 */}
                <dl className="grid grid-cols-2 gap-x-6 gap-y-2.5 rounded-[4px] border border-(--coord-line) bg-(--coord-head) p-3.5 text-sm">
                  <Meta label="요청자">
                    <b>{selected.studentName}</b>
                  </Meta>
                  <Meta label="교육과정">{courseName(selected.courseLabel)}</Meta>
                  <Meta label="필요한 도움">
                    <HelpTypeChip type={selected.helpType} />
                  </Meta>
                  <Meta label="관련 공고">{selected.jobTitle ? <b className="font-semibold">{selected.jobTitle}</b> : <span className="text-(--coord-muted)">공고 없는 일반 도움 요청</span>}</Meta>
                  <Meta label="접수 시각">{monthDayTime(selected.createdAt)}</Meta>
                  <Meta label="재요청 여부">
                    {earlierCount(selected) > 0 ? <Badge tone="orange" small>재요청 · 이전 {earlierCount(selected)}건</Badge> : "첫 요청"}
                    {openCount(selected.studentId) > 1 && <span className="ml-1.5 text-xs text-(--coord-muted)">접수 중 {openCount(selected.studentId)}건</span>}
                  </Meta>
                  {selected.consultationAt && (
                    <Meta label="상담 일시">
                      <b className="text-(--coord-green)">{monthDayTime(selected.consultationAt)}</b>
                      {selected.status !== "CONSULTATION_SCHEDULED" && <span className="ml-1.5 text-xs text-(--coord-muted)">(지난 상담 예정)</span>}
                    </Meta>
                  )}
                  {selected.status === "CLOSED" && (
                    <Meta label="종료">
                      {selected.closedAt ? monthDayTime(selected.closedAt) : "—"} · 수료생이 직접 종료
                      {selected.closeReason && <span className="block text-xs text-(--coord-muted)">사유: {closeReasonLabels[selected.closeReason]}</span>}
                    </Meta>
                  )}
                  <Meta label="담당자">
                    <Unlinked title="요청별 담당자 필드가 아직 없어요" />
                  </Meta>
                  <div className="col-span-2 flex flex-wrap items-center gap-x-4 gap-y-1.5 border-t border-(--coord-line) pt-2.5">
                    <span className="flex items-center gap-1.5">
                      <span className="text-(--coord-muted)">선호 연락:</span>
                      <ChannelChip method={selected.contactMethod} />
                    </span>
                    <span className="flex items-center gap-1.5">
                      <span className="text-(--coord-muted)">이메일:</span>
                      {!info ? (
                        <span className="text-(--coord-faint)">불러오는 중…</span>
                      ) : info.state === "error" ? (
                        <span className="text-(--coord-muted)">조회 실패</span>
                      ) : (
                        <b className="font-semibold">{info.email ?? "등록 안 됨"}</b>
                      )}
                    </span>
                    <span className="flex items-center gap-1.5">
                      <span className="text-(--coord-muted)">전화:</span>
                      <Unlinked title="전화번호를 주는 API 가 아직 없어요" />
                    </span>
                    <span className="text-xs text-(--coord-faint)">수료생이 직접 지정한 연락 방식이에요.</span>
                  </div>
                </dl>

                <section aria-label="요청 내용">
                  <h3 className="mb-1.5 text-[15px] font-extrabold">요청 내용</h3>
                  {/* 긴 원문도 자르지 않는다 (줄바꿈 유지) */}
                  {/* 수료생이 쓴 문장이 먼저 보이도록 연한 민트 배경 · 조금 큰 글씨 */}
                  <p className="rounded-[4px] border border-(--coord-green-line) bg-(--coord-tint) px-4 py-3.5 text-[16.5px] leading-[1.6] font-semibold break-words whitespace-pre-wrap">
                    {selected.memo ? `"${selected.memo}"` : <span className="text-(--coord-muted)">남긴 메모가 없어요.</span>}
                  </p>
                </section>

                {/* C5 시안: '요청 처리' 카드 = 위 처리 상태 · 상담 예정일 · 저장(주요 행동) / 아래 '후속 업무' 상담 기록 쓰기 · 연락 완료.
                    처리 상태 저장은 진행 중 요청(접수 · 처리 중 · 상담 예정)에서만. 연락 완료 · 요청 종료 요청은 후속 업무만 */}
                <section aria-label="요청 처리" className="overflow-hidden rounded-[6px] border border-(--coord-green-line)">
                  <h3 className="border-b border-(--coord-green-line) bg-(--coord-tint) px-4 py-2.5 text-[15px] font-extrabold">요청 처리</h3>
                  {isActiveHelp(selected) && (
                    <ProgressForm
                      key={`${selected.helpRequestId}:${selected.status}:${selected.consultationAt ?? ""}`}
                      request={selected}
                      onSaved={(status) => {
                        setTab(tabOf(status));
                        setDone(null);
                        setNotice(null);
                        setSaved(`${selected.studentName} 님 요청을 '${helpLabels[status]}'(으)로 바꿨어요.`);
                        setReloadKey((k) => k + 1);
                        notifyReviewQueueChanged();
                      }}
                      onConflict={() => {
                        setNotice("이미 처리됐거나 없는 요청이에요. 목록을 새로 불러왔어요.");
                        setReloadKey((k) => k + 1);
                        notifyReviewQueueChanged();
                      }}
                    />
                  )}
                  <div className={`flex items-center justify-between gap-3 px-4 py-3 ${isActiveHelp(selected) ? "border-t border-(--coord-line)" : ""}`}>
                    <h4 className="text-[13.5px] font-extrabold">후속 업무</h4>
                    <div className="flex gap-2.5">
                      {/* C5 를 벗어나지 않고 C3 와 같은 기록 추가 창을 연다 (수료생 상담 기록, 이 요청과 연결되지 않음) */}
                      <button
                        type="button"
                        aria-haspopup="dialog"
                        onClick={() => setLogFor({ studentId: selected.studentId, name: selected.studentName })}
                        className={`${btn.base} ${btn.outline} ${btn.ctl}`}
                      >
                        <Icon name="pencil" className="size-4" /> 상담 기록 쓰기
                      </button>
                      {/* 연락 완료한 요청은 처리 중으로 되돌릴 수 있다 (서버는 요청 종료만 막음) */}
                      {selected.status === "CONTACTED" && (
                        <button
                          type="button"
                          onClick={() => {
                            setActionError(null);
                            setConfirming({ request: selected, target: "IN_PROGRESS" });
                          }}
                          aria-haspopup="dialog"
                          className={`${btn.base} ${btn.outline} ${btn.ctl}`}
                        >
                          <Icon name="back" className="size-4" /> 처리 중으로 되돌리기
                        </button>
                      )}
                      {isActiveHelp(selected) && (
                        <button
                          type="button"
                          onClick={() => {
                            setActionError(null);
                            setConfirming({ request: selected, target: "CONTACTED" });
                          }}
                          aria-haspopup="dialog"
                          aria-label={`${selected.studentName} 도움 요청 연락 완료`}
                          className={`${btn.base} ${btn.outline} ${btn.ctl} border-(--coord-green)! text-(--coord-green-ink)!`}
                        >
                          <Icon name="checkCircle" className="size-4" /> 연락 완료
                        </button>
                      )}
                    </div>
                  </div>
                </section>
                <p className="-mt-1 text-[13px] leading-[1.6] text-(--coord-faint)">
                  {selected.status === "CONTACTED" && "이미 연락을 마친 요청이에요. 요청 종료는 수료생이 앱에서 해요. "}
                  {selected.status === "CLOSED" && "수료생이 종료한 요청이에요. 기록으로만 남아요. "}
                  실제 전화·문자·이메일 발송은 이음에서 하지 않아요. &lsquo;상담 기록 쓰기&rsquo;는 수료생 상담 기록에 남고, 이 요청과 연결되지는 않아요.
                </p>

                {/* 처리 이력 + 수료생 상담 기록: 리뉴얼 타임라인 모양 (점 + 세로선) */}
                <section aria-label="처리 이력" className="grid grid-cols-2 gap-5 border-t border-(--coord-line) pt-4">
                  <div className="min-w-0">
                    <div className="mb-2.5 flex items-center justify-between gap-2">
                      <h3 className="text-[15px] font-extrabold">이 요청의 처리 이력</h3>
                      <Unlinked title="요청별 처리 이력 API 가 아직 없어요" />
                    </div>
                    <ol className="flex flex-col gap-3 border-l-2 border-dashed border-(--coord-line) pl-4 text-[13.5px]">
                      <TimelineItem at={monthDayTime(selected.createdAt)}>수료생 앱에서 도움 요청 접수</TimelineItem>
                      {selected.status === "IN_PROGRESS" && <TimelineItem at="시각 기록 없음">매니저 처리 중</TimelineItem>}
                      {selected.consultationAt && <TimelineItem at={monthDayTime(selected.consultationAt)}>상담 예정</TimelineItem>}
                      {selected.status === "CONTACTED" && <TimelineItem at="시각 기록 없음">매니저 연락 완료</TimelineItem>}
                      {selected.status === "CLOSED" && (
                        <TimelineItem at={selected.closedAt ? monthDayTime(selected.closedAt) : "—"}>수료생이 요청 종료</TimelineItem>
                      )}
                    </ol>
                  </div>
                  <div className="min-w-0">
                    <h3 className="text-[15px] font-extrabold">수료생 상담 기록 (참고)</h3>
                    <p className="mt-0.5 text-xs text-(--coord-faint)">수료생 전체 상담 기록이에요. 이 요청과 연결된 기록은 아니에요.</p>
                    <ContactLogs info={info} studentId={selected.studentId} />
                  </div>
                </section>
              </div>
            </>
          ) : (
            <p className="p-9 text-center text-[15px] text-(--coord-muted)">요청을 선택해 주세요.</p>
          )}
        </Panel>
      </div>

      {confirming && (
        <ContactedDialog
          request={confirming.request}
          target={confirming.target}
          busy={busy}
          error={actionError}
          onConfirm={() => changeStatus(confirming.request, confirming.target)}
          onClose={closeConfirm}
        />
      )}
      {logFor && (
        <ContactLogDialog
          studentId={logFor.studentId}
          studentName={logFor.name}
          onClose={() => setLogFor(null)}
          onSaved={(log) => {
            // 아래 '수료생 상담 기록(참고)'에 바로 보이게 넣는다 (선택한 도움 요청은 그대로)
            setInfos((m) => {
              const cur = m[logFor.studentId];
              if (cur?.state !== "ok") {
                const next = { ...m };
                delete next[logFor.studentId]; // 아직 못 받았으면 다시 불러온다
                return next;
              }
              return { ...m, [logFor.studentId]: { ...cur, logs: [log, ...cur.logs] } };
            });
            setNotice(null);
            setSaved(`${logFor.name} 님 상담 기록을 저장했어요. 이 도움 요청과 연결된 기록은 아니에요.`);
            setLogFor(null);
            notifyReviewQueueChanged();
          }}
        />
      )}
    </>
  );
}

function Title({ action }: { action?: React.ReactNode }) {
  return <PageTitle title="도움 요청" action={action} />;
}

/** 처리 중(접수 포함) 탭은 먼저 접수된 순(오래 기다린 요청부터), 상담 예정은 상담이 빠른 순, 연락 완료(종료 포함) 탭은 최근 순 */
const sortRequests = (list: CoordinatorHelpRequest[], tab: StatusTab) =>
  [...list].sort((a, b) =>
    tab === "CONSULTATION_SCHEDULED"
      ? (a.consultationAt ?? "").localeCompare(b.consultationAt ?? "")
      : tab === "IN_PROGRESS"
        ? a.createdAt.localeCompare(b.createdAt)
        : b.createdAt.localeCompare(a.createdAt),
  );

/** 처리 단계 선택지 (연락 완료는 아래 '연락 완료' 확인 창으로만) */
const PROGRESS_CHOICES: HelpRequestStatus[] = ["IN_PROGRESS", "CONSULTATION_SCHEDULED"];

/** "2026-11-05T14:00:00+09:00" → datetime-local 값 "2026-11-05T14:00" (서버가 +09:00 으로 준다) */
const toLocalInput = (iso: string | null) => (iso ? iso.slice(0, 16) : "");

/**
 * 요청 1건의 처리 단계 바꾸기: 처리 중 · 상담 예정(상담 일시 필수). PATCH /api/coordinator/help-requests/{id}.
 * 저장 성공 뒤에만 onSaved(상태 배지 · 목록 · 처리 이력은 목록을 다시 불러와 바뀐다), 404 · 409 면 onConflict
 */
function ProgressForm({
  request,
  onSaved,
  onConflict,
}: {
  request: CoordinatorHelpRequest;
  onSaved: (status: HelpRequestStatus) => void;
  onConflict: () => void;
}) {
  const [target, setTarget] = useState<HelpRequestStatus>(request.status === "CONSULTATION_SCHEDULED" ? "CONSULTATION_SCHEDULED" : "IN_PROGRESS");
  const [when, setWhen] = useState(toLocalInput(request.status === "CONSULTATION_SCHEDULED" ? request.consultationAt : null));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const scheduling = target === "CONSULTATION_SCHEDULED";
  const unchanged = target === request.status && (!scheduling || when === toLocalInput(request.consultationAt));
  const canSave = !busy && !unchanged && (!scheduling || when !== "");

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSave) return;
    setBusy(true);
    setError(null);
    try {
      await updateHelpRequestStatus(request.helpRequestId, scheduling ? { status: target, consultationAt: `${when}:00+09:00` } : { status: target });
      onSaved(target);
    } catch (err) {
      if (err instanceof ApiError && (err.status === 404 || err.status === 409)) onConflict();
      else setError(err instanceof ApiError ? err.message : "처리 상태를 저장하지 못했어요. 다시 시도해 주세요.");
    } finally {
      setBusy(false);
    }
  };

  return (
    // C5 시안: 왼쪽 처리 상태(라디오) | 가운데 상담 예정일(상담 예정일 때만) | 오른쪽 저장. 연락 완료는 아래 후속 업무의 확인 창으로만
    <form onSubmit={save} aria-label="처리 상태 바꾸기" className="px-4 py-3.5 text-sm">
      <div className="flex items-stretch gap-5">
        <fieldset className="flex shrink-0 flex-col gap-2" disabled={busy}>
          <legend className="mb-2 text-[13.5px] font-extrabold">처리 상태</legend>
          {PROGRESS_CHOICES.map((s) => (
            <label key={s} className="flex cursor-pointer items-center gap-2 font-semibold">
              <input
                type="radio"
                name={`progress-${request.helpRequestId}`}
                value={s}
                checked={target === s}
                onChange={() => setTarget(s)}
                className="size-4 accent-(--coord-green)"
              />
              {helpLabels[s]}
              {request.status === s && <span className="text-xs font-semibold text-(--coord-muted)">(현재)</span>}
            </label>
          ))}
        </fieldset>
        <div className="min-w-0 flex-1 border-l border-(--coord-line) pl-5">
          {scheduling ? (
            <label className="flex flex-col gap-1.5 text-[13.5px] font-extrabold">
              상담 예정일 (필수)
              <input
                type="datetime-local"
                required
                value={when}
                onChange={(e) => setWhen(e.target.value)}
                disabled={busy}
                className="w-full max-w-[260px] rounded-[4px] border border-(--coord-line) bg-(--coord-surface) px-3 py-[7px] text-[14.5px] font-normal text-(--coord-ink)"
              />
            </label>
          ) : (
            <p className="text-[13px] text-(--coord-faint)">&lsquo;상담 예정&rsquo;을 고르면 상담 일시를 정할 수 있어요.</p>
          )}
        </div>
        {/* 저장 전에는 상태 배지 · 목록이 그대로다 (저장 성공 뒤에만 바뀜) */}
        <div className="flex shrink-0 items-end">
          <button
            type="submit"
            disabled={!canSave}
            aria-busy={busy}
            title={unchanged ? "지금 상태와 같아요" : undefined}
            className={`${btn.base} ${btn.primary} ${btn.ctl} px-7`}
          >
            {busy ? "저장 중…" : "저장"}
          </button>
        </div>
      </div>
      {error && (
        <p role="alert" className="mt-2 text-[13.5px] text-(--coord-red)">
          {error}
        </p>
      )}
    </form>
  );
}

/** 기본 정보 한 칸: "이름: 값" */
function Meta({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex min-w-0 items-center gap-1.5">
      <dt className="shrink-0 text-(--coord-muted)">{label}:</dt>
      <dd className="min-w-0">{children}</dd>
    </div>
  );
}

/** 타임라인 한 줄 (점 + 시각 + 내용) */
function TimelineItem({ at, children }: { at: string; children: React.ReactNode }) {
  return (
    <li className="relative">
      <span aria-hidden="true" className="absolute top-1.5 -left-[22px] size-2.5 rounded-full border-2 border-(--coord-surface) bg-(--coord-green)" />
      <span className="block text-xs font-semibold text-(--coord-faint)">{at}</span>
      {children}
    </li>
  );
}

/** API 가 없어 보여 줄 수 없는 값 */
function Unlinked({ title }: { title: string }) {
  return (
    <span title={title} className="rounded-full border border-(--coord-line) bg-(--coord-surface) px-2 py-0.5 text-[11px] font-semibold text-(--coord-faint)">
      미연동
    </span>
  );
}

/** 도움 요청 유형 칩 (서버 helpType 한글 이름). null · 키 없음은 '미분류' 회색 */
function HelpTypeChip({ type }: { type: HelpType | null | undefined }) {
  return (
    <Badge tone={type ? "red" : "gray"} small>
      {helpTypeLabel(type)}
    </Badge>
  );
}

/** 선호 연락 수단 칩. 고르지 않았으면 '선호 연락 없음' */
function ChannelChip({ method }: { method: ContactMethod | null }) {
  if (!method) return <span className="text-xs text-(--coord-faint)">선호 연락 없음</span>;
  return (
    <span className={`inline-flex items-center gap-1 rounded-full py-0.5 pr-2.5 pl-1.5 text-xs font-bold whitespace-nowrap ${toneClass("green")}`}>
      <Icon name={channelIcon[method]} className="size-3" />
      {contactLabels[method]} 선호
    </span>
  );
}

/** 수료생 상담 기록 최근 3건 (C3 상세 API) */
function ContactLogs({ info, studentId }: { info: StudentInfo | undefined; studentId: number }) {
  if (!info) return <p className="mt-2.5 text-[13.5px] text-(--coord-faint)">불러오는 중…</p>;
  if (info.state === "error") return <p className="mt-2.5 text-[13.5px] text-(--coord-muted)">상담 기록을 불러오지 못했어요.</p>;
  if (info.logs.length === 0) return <p className="mt-2.5 text-[13.5px] text-(--coord-faint)">남긴 상담 기록이 없어요.</p>;
  const recent = [...info.logs].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 3);
  return (
    <>
      {/* 기록 내용은 자르지 않는다 (줄바꿈 유지) */}
      <ol className="mt-2.5 flex flex-col gap-3 border-l-2 border-dashed border-(--coord-line) pl-4 text-[13.5px]">
        {recent.map((l) => (
          <li key={l.contactLogId} className="relative">
            <span aria-hidden="true" className="absolute top-1.5 -left-[22px] size-2.5 rounded-full border-2 border-(--coord-surface) bg-(--coord-brand-bright)" />
            <span className="block text-xs font-semibold text-(--coord-faint)">
              {monthDayTime(l.createdAt)} · {contactTypeLabels[l.contactType]}
            </span>
            <p className="leading-[1.55] break-words whitespace-pre-wrap">{l.note}</p>
          </li>
        ))}
      </ol>
      {info.logs.length > 3 && (
        <Link href={`/coordinator/students/${studentId}`} className="mt-2.5 inline-block text-[13.5px] font-bold text-(--coord-green) hover:underline">
          상담 기록 {info.logs.length}건 모두 보기
        </Link>
      )}
    </>
  );
}

/** '연락 완료' · '처리 중으로 되돌리기' 확인 창. 확인해야만 상태 변경 API 를 부른다. 배경 · Esc 로 닫힌다 */
function ContactedDialog({
  request,
  target,
  busy,
  error,
  onConfirm,
  onClose,
}: {
  request: CoordinatorHelpRequest;
  target: ConfirmTarget;
  busy: boolean;
  error: string | null;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const titleId = useId();
  const panelRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    panelRef.current?.focus();
  }, []);
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-30 flex items-center justify-center bg-(--coord-forest)/40 backdrop-blur-[3px]" onClick={onClose}>
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onClick={(e) => e.stopPropagation()}
        className="flex w-[500px] flex-col gap-3.5 rounded-[10px] border border-(--coord-line) bg-(--coord-surface) p-6 shadow-[0_20px_40px_rgb(0_0_0/0.16)] outline-none"
      >
        <h2 id={titleId} className="text-xl font-extrabold">
          {target === "CONTACTED" ? "연락 완료로 바꿀까요?" : "처리 중으로 되돌릴까요?"}
        </h2>
        <dl className="grid grid-cols-[84px_1fr] gap-y-1.5 rounded-[4px] border border-(--coord-line) bg-(--coord-head) px-4 py-3 text-sm">
          <dt className="text-(--coord-muted)">요청자</dt>
          <dd className="font-semibold">
            {request.studentName} · {courseName(request.courseLabel)}
          </dd>
          <dt className="text-(--coord-muted)">필요한 도움</dt>
          <dd>
            {helpTypeLabel(request.helpType)} · {helpTitle(request)}
          </dd>
          <dt className="text-(--coord-muted)">선호 연락</dt>
          <dd>{request.contactMethod ? contactLabels[request.contactMethod] : "선호 연락 없음"}</dd>
          <dt className="text-(--coord-muted)">접수</dt>
          <dd>{monthDayTime(request.createdAt)}</dd>
        </dl>
        <ul className="list-disc pl-5 text-sm leading-[1.75] text-(--coord-muted)">
          <li>
            요청 상태가 <b className="text-(--coord-ink)">{helpLabels[request.status]} → {helpLabels[target]}</b>(으)로 바뀌고 {helpLabels[target]} 탭으로 옮겨져요.
          </li>
          {target === "CONTACTED" ? (
            <>
              <li>연락 완료는 요청 종료가 아니에요. 필요하면 &lsquo;처리 중으로 되돌리기&rsquo;로 되돌릴 수 있어요.</li>
              <li>처리 메모는 함께 저장되지 않아요. 상담 내용은 수료생 상담 기록에 따로 남겨 주세요.</li>
            </>
          ) : (
            <li>다시 연락하거나 상담을 잡아야 할 때 써요. 수료생에게 따로 알리지 않아요.</li>
          )}
        </ul>
        {error && (
          <p role="alert" className="text-[13px] text-(--coord-red)">
            {error}
          </p>
        )}
        <div className="mt-2 flex gap-2">
          <button type="button" onClick={onClose} disabled={busy} className={`${btn.base} ${btn.outline} flex-1 py-3`}>
            취소
          </button>
          <button type="button" onClick={onConfirm} disabled={busy} aria-busy={busy} className={`${btn.base} ${btn.primary} flex-1 py-3`}>
            {busy ? "처리 중…" : target === "CONTACTED" ? "연락 완료로 바꾸기" : "처리 중으로 되돌리기"}
          </button>
        </div>
      </div>
    </div>
  );
}
