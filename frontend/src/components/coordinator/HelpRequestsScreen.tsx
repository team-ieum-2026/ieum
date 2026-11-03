"use client";
// 주인: FE-B — 도움 요청 목록 · 상세 · 처리 기록 (11/1 개편 목업 requestsPage).
// 데이터는 상태별 목록 API(GET /api/coordinator/help-requests)를 ALL 로 한 번 받아 접수 · 처리 중 · 상담 예정 · 연락 완료 · 요청 종료 탭으로 나눈다.
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
import type { CoordinatorHelpRequest, HelpType } from "@/types/coordinator";
import { ACTIVE_HELP_STATUSES, type ContactLog, type ContactMethod, type ContactType, type HelpRequestStatus, type StudentHelpRequestStatus } from "@/types/student";

const CHANNELS: (ContactMethod | "ALL")[] = ["ALL", "PHONE", "SMS", "EMAIL"];
const channelIcon: Record<ContactMethod, IconName> = { PHONE: "phone", SMS: "sms", EMAIL: "mail" };
const contactTypeLabels: Record<ContactType, string> = { PHONE: "전화", MESSAGE: "문자", MEETING: "면담" };

/** 목업 요청 상태 탭 순서 */
const STATUS_TABS: StudentHelpRequestStatus[] = ["OPEN", "IN_PROGRESS", "CONSULTATION_SCHEDULED", "CONTACTED", "CLOSED"];

const EMPTY_TEXT: Record<StudentHelpRequestStatus, string> = {
  OPEN: "접수된 도움 요청이 없어요.",
  IN_PROGRESS: "처리 중인 도움 요청이 없어요.",
  CONSULTATION_SCHEDULED: "상담이 잡힌 도움 요청이 없어요.",
  CONTACTED: "연락을 마친 도움 요청이 없어요.",
  CLOSED: "종료된 도움 요청이 없어요.",
};

/** 연락 완료 전 진행 중 요청 (접수 · 처리 중 · 상담 예정): 처리 단계를 바꾸거나 연락 완료로 넘길 수 있다 */
const isActiveHelp = (r: CoordinatorHelpRequest) => ACTIVE_HELP_STATUSES.includes(r.status);

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
  // 고른 탭. 고르기 전에는 ?id 요청의 상태 탭(대시보드 · 알림에서 들어온 경우), 없으면 접수
  const [pickedTab, setTab] = useState<StudentHelpRequestStatus | null>(null);
  const [channel, setChannel] = useState<ContactMethod | "ALL">("ALL");
  const [infos, setInfos] = useState<Record<number, StudentInfo>>({});
  const [confirming, setConfirming] = useState<CoordinatorHelpRequest | null>(null);
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

  const tab = pickedTab ?? list?.find((r) => r.helpRequestId === selectedId)?.status ?? "OPEN";
  const inTab = sortRequests((list ?? []).filter((r) => r.status === tab), tab);
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
  const pickTab = (next: StudentHelpRequestStatus) => {
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

  const markContacted = async (r: CoordinatorHelpRequest) => {
    setBusy(true);
    setActionError(null);
    try {
      await updateHelpRequestStatus(r.helpRequestId, { status: "CONTACTED" });
      setConfirming(null);
      setNotice(null);
      setSaved(null);
      setDone({ name: r.studentName, studentId: r.studentId });
      router.replace(pathname, { scroll: false });
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
        setActionError(e instanceof ApiError ? e.message : `연락 완료로 바꾸지 못했어요. 요청 상태는 그대로 '${helpLabels[r.status]}'예요. 다시 시도해 주세요.`);
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <Title action={<RefreshAction busy={refreshing} error={refreshError} onRefresh={refresh} />} />
      <p className="mb-[18px] rounded-md border border-(--coord-tint-line) bg-(--coord-tint) px-[18px] py-3 text-[13.5px] leading-[1.6] text-(--coord-green-ink)">
        <b>수료생이 선호하는 연락 방법:</b> 전화·문자·이메일은 수료생이 앱에서 직접 고른 <b>선호 연락 방법</b>이에요. 요청은 모두 수료생 앱으로
        접수되고, 요청마다 이 방법으로 연락해 주세요.
      </p>
      {done && (
        <p role="status" className="mb-4 flex items-center justify-between gap-3 rounded-md border border-(--coord-green-line) bg-(--coord-green-soft) px-4 py-3 text-sm text-(--coord-green-ink)">
          <span>
            {done.name} 님 요청을 <b>연락 완료</b>로 바꿨어요. 상담 내용은 수료생 상담 기록에 남겨 주세요.
            {IS_MOCK && <b className="ml-2 text-(--coord-attention)">{MOCK_SAVE_NOTE}</b>}
          </span>
          <Link href={`/coordinator/students/${done.studentId}?log=new`} className={`${btn.base} ${btn.outline} ${btn.sm}`}>
            상담 기록 쓰기
          </Link>
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
      {/* 리뉴얼 .inbox-split-layout: 왼쪽 목록 360px(화면에 붙어 따라오고 안에서 스크롤) : 오른쪽 상세(내용만큼 길어져 긴 요청 · 기록도 잘리지 않음) */}
      <div className="grid grid-cols-[360px_minmax(0,1fr)] items-start gap-5">
        <Panel label="도움 요청 목록" className="sticky top-[76px] flex max-h-[calc(100vh-96px)] flex-col">
          <div role="tablist" aria-label="요청 상태" className="flex shrink-0 flex-wrap gap-1.5 border-b border-(--coord-line) px-4 py-3.5">
            {STATUS_TABS.map((status) => (
              <button
                key={status}
                type="button"
                role="tab"
                aria-selected={status === tab}
                onClick={() => pickTab(status)}
                className={`${btn.base} ${status === tab ? btn.primary : btn.outline} px-2.5! py-1.5! text-[12.5px]!`}
              >
                {helpLabels[status]}{" "}
                {/* 목업 목록은 접수만 만들어 다른 상태가 0건처럼 보이므로 목업 모드에서는 '—' (실제 BE 에서는 실제 건수) */}
                <span className="font-extrabold">{IS_MOCK && status !== "OPEN" ? "—" : list.filter((r) => r.status === status).length}</span>
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
                        <Badge tone={helpTone[r.status]} small>
                          {helpLabels[r.status]}
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
                  요청 상세 <Badge tone={helpTone[selected.status]}>{helpLabels[selected.status]}</Badge>
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
                  <h3 className="mb-1.5 text-[13.5px] font-extrabold text-(--coord-muted)">요청 내용</h3>
                  {/* 긴 원문도 자르지 않는다 (줄바꿈 유지) */}
                  <p className="rounded-[4px] border border-(--coord-line) bg-(--coord-surface) px-4 py-3.5 text-[15px] leading-[1.6] break-words whitespace-pre-wrap">
                    {selected.memo ? `"${selected.memo}"` : <span className="text-(--coord-muted)">남긴 메모가 없어요.</span>}
                  </p>
                </section>

                {/* 요청 1건의 처리 단계: 처리 중 · 상담 예정(일시)은 저장된다. 처리 메모 · 후속 연락일은 API 가 없어 미연동 */}
                {isActiveHelp(selected) && (
                  <ProgressForm
                    key={`${selected.helpRequestId}:${selected.status}:${selected.consultationAt ?? ""}`}
                    request={selected}
                    onSaved={(status) => {
                      setTab(status);
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

                <div className="flex gap-2.5 border-t border-(--coord-line) pt-4">
                  <Link href={`/coordinator/students/${selected.studentId}?log=new`} className={`${btn.base} ${btn.outline} flex-1`}>
                    상담 기록 쓰기
                  </Link>
                  {isActiveHelp(selected) && (
                    <button
                      type="button"
                      onClick={() => {
                        setActionError(null);
                        setConfirming(selected);
                      }}
                      aria-haspopup="dialog"
                      aria-label={`${selected.studentName} 도움 요청 연락 완료`}
                      className={`${btn.base} ${btn.primary} flex-1`}
                    >
                      연락 완료
                    </button>
                  )}
                </div>
                <p className="-mt-2 text-[13px] leading-[1.6] text-(--coord-faint)">
                  {selected.status === "CONTACTED" && "이미 연락을 마친 요청이에요. 요청 종료는 수료생이 앱에서 해요. "}
                  {selected.status === "CLOSED" && "수료생이 종료한 요청이에요. 기록으로만 남아요. "}
                  실제 전화·문자·이메일 발송은 이음에서 하지 않아요. &lsquo;상담 기록 쓰기&rsquo;는 수료생 상세(C3)의 상담 기록으로 이동해요.
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
                      {selected.status === "IN_PROGRESS" && <TimelineItem at="시각 기록 없음">코디네이터 처리 중</TimelineItem>}
                      {selected.consultationAt && <TimelineItem at={monthDayTime(selected.consultationAt)}>상담 예정</TimelineItem>}
                      {selected.status === "CONTACTED" && <TimelineItem at="시각 기록 없음">코디네이터 연락 완료</TimelineItem>}
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
        <ContactedDialog request={confirming} busy={busy} error={actionError} onConfirm={() => markContacted(confirming)} onClose={closeConfirm} />
      )}
    </>
  );
}

function Title({ action }: { action?: React.ReactNode }) {
  return <PageTitle title="도움 요청" description="필요한 도움부터 확인하고, 관련 서류와 이전 연락 기록을 이어 보세요." action={action} />;
}

/** 접수 · 처리 중 탭은 먼저 접수된 순(오래 기다린 요청부터), 상담 예정은 상담이 빠른 순, 연락 완료 · 종료 탭은 최근 순 */
const sortRequests = (list: CoordinatorHelpRequest[], tab: StudentHelpRequestStatus) =>
  [...list].sort((a, b) =>
    tab === "CONSULTATION_SCHEDULED"
      ? (a.consultationAt ?? "").localeCompare(b.consultationAt ?? "")
      : tab === "OPEN" || tab === "IN_PROGRESS"
        ? a.createdAt.localeCompare(b.createdAt)
        : b.createdAt.localeCompare(a.createdAt),
  );

/** 처리 단계 선택지 (연락 완료는 아래 '연락 완료' 확인 창으로만) */
const PROGRESS_CHOICES: HelpRequestStatus[] = ["IN_PROGRESS", "CONSULTATION_SCHEDULED"];

/** "2026-11-05T14:00:00+09:00" → datetime-local 값 "2026-11-05T14:00" (서버가 +09:00 으로 준다) */
const toLocalInput = (iso: string | null) => (iso ? iso.slice(0, 16) : "");

/**
 * 요청 1건의 처리 단계 바꾸기: 처리 중 · 상담 예정(상담 일시 필수). PATCH /api/coordinator/help-requests/{id}.
 * 처리 메모 · 후속 연락일은 저장 API 가 없어 미연동으로 둔다. 저장 성공 뒤에만 onSaved, 404 · 409 면 onConflict
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
    // 리뉴얼 '요청 처리 및 상담 메모' 영역. 상태 선택은 실제 API 가 받는 처리 중 · 상담 예정만 (연락 완료는 아래 확인 창)
    <section aria-label="처리 상태 바꾸기" className="border-t border-(--coord-line) pt-4">
      <h3 className="text-sm font-extrabold">요청 처리 · 상태 바꾸기</h3>
      <form onSubmit={save} className="mt-2.5 flex flex-col gap-3 text-sm">
        <div className="flex flex-wrap items-end gap-3">
          <fieldset className="flex flex-wrap gap-1.5" disabled={busy}>
            <legend className="sr-only">처리 상태</legend>
            {PROGRESS_CHOICES.map((s) => (
              <label
                key={s}
                className={`cursor-pointer rounded-[4px] border px-3.5 py-2 font-bold transition has-focus-visible:outline-3 has-focus-visible:outline-(--coord-green) ${
                  target === s
                    ? "border-(--coord-green) bg-(--coord-green-soft) text-(--coord-green-ink)"
                    : "border-(--coord-line) bg-(--coord-surface) text-(--coord-muted) hover:border-(--coord-line-strong)"
                }`}
              >
                <input type="radio" name={`progress-${request.helpRequestId}`} value={s} checked={target === s} onChange={() => setTarget(s)} className="sr-only" />
                {helpLabels[s]}
                {request.status === s && <span className="ml-1 text-xs font-semibold">(현재)</span>}
              </label>
            ))}
          </fieldset>
          {scheduling && (
            <label className="flex flex-col gap-1 text-[13px] font-semibold text-(--coord-muted)">
              상담 일시 (필수)
              <input
                type="datetime-local"
                required
                value={when}
                onChange={(e) => setWhen(e.target.value)}
                disabled={busy}
                className="rounded-[4px] border border-(--coord-line) bg-(--coord-surface) px-3 py-[7px] text-[14.5px] font-normal text-(--coord-ink)"
              />
            </label>
          )}
        </div>
        {/* 처리 메모 · 후속 연락일: 저장 API 가 없어 입력을 막아 둔다 (저장하는 척하지 않음) */}
        <fieldset disabled className="grid grid-cols-[minmax(0,1fr)_200px] gap-3" title="처리 메모 · 후속 연락일 저장 API 가 아직 없어요">
          <label className="flex flex-col gap-1 text-[13px] font-semibold text-(--coord-muted)">
            <span className="flex items-center gap-1.5">
              처리 메모 <Unlinked title="처리 메모 저장 API 가 아직 없어요" />
            </span>
            <textarea
              rows={2}
              placeholder="저장 기능 연결 전이에요. 상담 내용은 수료생 상담 기록에 남겨 주세요."
              className="resize-none rounded-[4px] border border-(--coord-line) bg-(--coord-head) px-3 py-2 text-[14.5px] font-normal"
            />
          </label>
          <label className="flex flex-col gap-1 text-[13px] font-semibold text-(--coord-muted)">
            <span className="flex items-center gap-1.5">
              후속 연락일 <Unlinked title="후속 연락일 저장 API 가 아직 없어요" />
            </span>
            <input type="date" className="rounded-[4px] border border-(--coord-line) bg-(--coord-head) px-3 py-2 text-[14.5px] font-normal" />
          </label>
        </fieldset>
        {error && (
          <p role="alert" className="text-[13.5px] text-(--coord-red)">
            {error}
          </p>
        )}
        <div className="flex justify-end">
          <button type="submit" disabled={!canSave} aria-busy={busy} className={`${btn.base} ${btn.primary}`}>
            {busy ? "저장 중…" : unchanged ? "지금 상태와 같아요" : `'${helpLabels[target]}'(으)로 저장`}
          </button>
        </div>
      </form>
    </section>
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
            <span aria-hidden="true" className="absolute top-1.5 -left-[22px] size-2.5 rounded-full border-2 border-(--coord-surface) bg-(--coord-blue)" />
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

/** '연락 완료' 확인 창. 확인해야만 상태 변경 API 를 부른다. 배경 · Esc 로 닫힌다 */
function ContactedDialog({
  request,
  busy,
  error,
  onConfirm,
  onClose,
}: {
  request: CoordinatorHelpRequest;
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
          연락 완료로 바꿀까요?
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
            요청 상태가 <b className="text-(--coord-ink)">{helpLabels[request.status]} → 연락 완료</b>로 바뀌고 연락 완료 탭으로 옮겨져요.
          </li>
          <li>연락 완료는 요청 종료가 아니에요. 요청 종료는 아직 이 화면에서 할 수 없어요.</li>
          <li>처리 메모는 함께 저장되지 않아요. 상담 내용은 수료생 상담 기록에 따로 남겨 주세요.</li>
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
            {busy ? "처리 중…" : "연락 완료로 바꾸기"}
          </button>
        </div>
      </div>
    </div>
  );
}
