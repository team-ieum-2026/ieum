"use client";
// 주인: FE-B — 도움 요청 목록 · 상세 · 처리 기록 (11/1 개편 목업 requestsPage). 데이터는 대시보드 API 의 helpRequests(접수 OPEN 만).
// - 전화 · 문자 · 이메일은 수료생이 앱에서 고른 '선호 연락 수단', memo 는 수료생이 남긴 요청 내용
// - '연락 완료' → 확인 창 → PATCH /api/coordinator/help-requests/{id} { status: CONTACTED } 성공 뒤에만 목록 · 건수 갱신
//   CONTACTED 는 '연락 완료'이지 '요청 종료(CLOSED)'가 아니다. 이 API 는 메모를 받지 않아 메모를 저장하는 척하지 않는다
// - 연락처(이메일) · 수료생 상담 기록은 C3 상세 API(GET)로 보여 준다. 상담 기록은 수료생 단위라 이 요청과 연결된 이력이 아니다
// 미연동(BE 회신 전): 요청 상태 필터(OPEN 외 목록), 처리 중 · 상담 예정 · 처리 불가 상태, 요청별 처리 메모 · 후속 연락일 · 상담 예정일 ·
//   처리 이력, 담당자, 재요청 여부, 전화번호
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useId, useRef, useState } from "react";
import { notifyReviewQueueChanged } from "@/components/coordinator/CoordinatorSidebar";
import { Badge, Icon, LoadState, PageTitle, Panel, btn, toneClass, type IconName } from "@/components/coordinator/CoordUi";
import { contactLabels, courseName, helpLabels, helpTone, monthDayTime } from "@/components/coordinator/coordLabels";
import { IS_MOCK, MOCK_SAVE_NOTE } from "@/components/coordinator/demoAccount";
import { ApiError } from "@/lib/api/client";
import { getDashboard, updateHelpRequestStatus } from "@/lib/api/coordinatorReview";
import { getCoordinatorStudent } from "@/lib/api/coordinatorStudents";
import type { DashboardHelpRequest } from "@/types/coordinator";
import type { ContactLog, ContactMethod, ContactType } from "@/types/student";

const CHANNELS: (ContactMethod | "ALL")[] = ["ALL", "PHONE", "SMS", "EMAIL"];
const channelIcon: Record<ContactMethod, IconName> = { PHONE: "phone", SMS: "sms", EMAIL: "mail" };
const contactTypeLabels: Record<ContactType, string> = { PHONE: "전화", MESSAGE: "문자", MEETING: "면담" };

/** 목업 요청 상태 탭. 실제 목록은 접수(OPEN)만 있다 */
const STATUS_TABS: { label: string; connected: boolean; reason?: string }[] = [
  { label: "접수", connected: true },
  { label: "처리 중", connected: false, reason: "서버에 없는 상태값이에요 (BE 확정 전)" },
  { label: "상담 예정", connected: false, reason: "서버에 없는 상태값이에요 (BE 확정 전)" },
  { label: "연락 완료", connected: false, reason: "연락 완료(CONTACTED) 목록을 주는 API 가 아직 없어요" },
  { label: "요청 종료", connected: false, reason: "요청 종료(CLOSED) 목록을 주는 API 가 아직 없어요" },
];

/** 처리 기록 폼의 상태 선택지. 저장 API 가 없어 모두 선택할 수 없다 */
const RECORD_STATUSES = ["처리 중", "상담 예정", "연락 완료", "요청 종료", "처리 불가 · 연락 안 됨"];

/** 선택한 요청의 수료생 연락처 · 상담 기록 (C3 상세 API) */
type StudentInfo = { state: "error" } | { state: "ok"; email: string | null; logs: ContactLog[] };

export default function HelpRequestsScreen() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const selectedId = Number(searchParams.get("id")) || null;

  const [list, setList] = useState<DashboardHelpRequest[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [channel, setChannel] = useState<ContactMethod | "ALL">("ALL");
  const [infos, setInfos] = useState<Record<number, StudentInfo>>({});
  const [confirming, setConfirming] = useState<DashboardHelpRequest | null>(null);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [done, setDone] = useState<{ name: string; studentId: number } | null>(null);

  useEffect(() => {
    let ignore = false;
    getDashboard()
      .then((d) => {
        // 먼저 접수된 순
        if (!ignore) setList([...d.helpRequests].sort((a, b) => a.createdAt.localeCompare(b.createdAt)));
      })
      .catch((e: unknown) => {
        if (!ignore) setError(e instanceof ApiError ? e.message : "도움 요청을 불러오지 못했어요.");
      });
    return () => {
      ignore = true;
    };
  }, [reloadKey]);

  const filtered = (list ?? []).filter((r) => channel === "ALL" || r.contactMethod === channel);
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
  /** 같은 수료생이 접수 상태로 남긴 요청 수 (재요청 여부가 아님) */
  const openCount = (studentId: number) => list.filter((r) => r.studentId === studentId).length;

  const closeConfirm = () => {
    if (busy) return;
    setConfirming(null);
    setActionError(null);
  };

  const markContacted = async (r: DashboardHelpRequest) => {
    setBusy(true);
    setActionError(null);
    try {
      await updateHelpRequestStatus(r.helpRequestId, { status: "CONTACTED" });
      setConfirming(null);
      setNotice(null);
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
        setActionError(e instanceof ApiError ? e.message : "연락 완료로 바꾸지 못했어요. 요청 상태는 그대로 '접수'예요. 다시 시도해 주세요.");
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <Title />
      <p className="mb-4 rounded-lg border border-(--coord-tint-line) bg-(--coord-tint) px-3.5 py-3 text-xs leading-[1.7]">
        <b>수료생이 선호하는 연락 방법</b>　전화·문자·이메일은 수료생이 앱에서 직접 고른 <b>선호 연락 방법</b>이에요. 요청은 모두
        수료생 앱으로 접수되고, 요청마다 이 방법으로 연락해 주세요.
      </p>
      {done && (
        <p role="status" className="mb-4 flex items-center justify-between gap-3 rounded-lg bg-(--coord-green-soft) px-3.5 py-3 text-xs text-(--coord-green-ink)">
          <span>
            {done.name} 님 요청을 <b>연락 완료</b>로 바꿨어요. 상담 내용은 수료생 상담 기록에 남겨 주세요.
            {IS_MOCK && <b className="ml-2 text-(--coord-attention)">{MOCK_SAVE_NOTE}</b>}
          </span>
          <Link href={`/coordinator/students/${done.studentId}?log=new`} className="font-bold underline-offset-4 hover:underline">
            상담 기록 쓰기
          </Link>
        </p>
      )}
      {notice && (
        <p role="status" className="mb-4 rounded-lg bg-(--coord-attention-soft) px-3.5 py-3 text-xs text-(--coord-attention)">
          {notice}
        </p>
      )}
      {/* 목업처럼 화면 높이에 맞추고 목록 · 상세가 각자 안에서 스크롤 (상단 막대 60 + 여백 70 + 제목 · 안내 약 150) */}
      <div className="grid min-h-[480px] grid-cols-[minmax(0,1.2fr)_minmax(380px,1fr)] gap-5 lg:h-[calc(100vh-290px)]">
        <Panel label="도움 요청 목록" className="flex min-h-0 flex-col">
          <div role="tablist" aria-label="요청 상태" className="flex flex-wrap items-center gap-1 border-b border-(--coord-line) px-5 pt-3.5 pb-3">
            {STATUS_TABS.map((t) =>
              t.connected ? (
                <span
                  key={t.label}
                  role="tab"
                  aria-selected="true"
                  className="rounded-[7px] bg-(--coord-green-soft) px-3.5 py-2 text-xs font-semibold text-(--coord-forest)"
                >
                  {t.label} <b className="ml-1 text-[10px]">{list.length}</b>
                </span>
              ) : (
                <button
                  key={t.label}
                  type="button"
                  role="tab"
                  aria-selected="false"
                  disabled
                  title={t.reason}
                  className="cursor-not-allowed rounded-[7px] px-3 py-2 text-xs text-(--coord-muted) opacity-70"
                >
                  {t.label} <span className="ml-0.5 text-[10px]">미연동</span>
                </button>
              ),
            )}
          </div>
          <div className="flex flex-wrap items-center gap-2.5 border-b border-(--coord-line) px-5 py-3">
            <span className="text-[11px] font-bold text-(--coord-muted)">선호 연락</span>
            <div className="flex flex-wrap gap-1" role="group" aria-label="선호 연락 필터">
              {CHANNELS.map((ch) => (
                <button
                  key={ch}
                  type="button"
                  aria-pressed={channel === ch}
                  onClick={() => setChannel(ch)}
                  className={`rounded-[7px] px-3 py-1.5 text-xs focus-visible:outline-3 focus-visible:outline-(--coord-green) ${
                    channel === ch ? "bg-(--coord-green-soft) font-semibold text-(--coord-forest)" : "text-(--coord-muted) hover:bg-(--coord-hover)"
                  }`}
                >
                  {ch === "ALL" ? "전체" : contactLabels[ch]}
                  <b className="ml-1.5 text-[10px]">{list.filter((r) => ch === "ALL" || r.contactMethod === ch).length}</b>
                </button>
              ))}
            </div>
          </div>
          {filtered.length === 0 ? (
            <p className="p-[35px] text-center text-[13px] text-(--coord-muted)">
              {list.length === 0 ? "접수된 도움 요청이 없어요." : "이 조건의 요청이 없어요."}
            </p>
          ) : (
            <ul className="min-h-0 flex-1 overflow-y-auto">
              {filtered.map((r) => {
                const active = r.helpRequestId === selected?.helpRequestId;
                const sameStudent = openCount(r.studentId);
                return (
                  <li key={r.helpRequestId}>
                    <button
                      type="button"
                      aria-current={active ? "true" : undefined}
                      onClick={() => select(r.helpRequestId)}
                      className={`grid w-full grid-cols-[1fr_auto] gap-3 border-b border-(--coord-line) px-5 py-4 text-left focus-visible:outline-3 focus-visible:-outline-offset-3 focus-visible:outline-(--coord-green) ${
                        active ? "bg-(--coord-tint) shadow-[inset_3px_0_var(--coord-green)]" : "hover:bg-(--coord-hover)"
                      }`}
                    >
                      <div className="min-w-0">
                        <p className="flex items-center gap-2">
                          <b className="text-sm">{r.studentName}</b>
                          <span className="text-[11px] text-(--coord-muted)">{courseName(r.courseLabel)}</span>
                        </p>
                        <h3 className="mt-1.5 mb-1 truncate text-[13px] font-bold">{r.jobTitle ?? "추천 공고 도움 요청"}</h3>
                        <p className="flex flex-wrap items-center gap-2 text-[11px] text-(--coord-muted)">
                          <ChannelChip method={r.contactMethod} />
                          <span>{monthDayTime(r.createdAt)} 접수</span>
                          {sameStudent > 1 && <span className="font-semibold text-(--coord-red)">같은 수료생 접수 {sameStudent}건</span>}
                        </p>
                      </div>
                      <div>
                        <Badge tone={helpTone.OPEN}>{helpLabels.OPEN}</Badge>
                      </div>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </Panel>

        <Panel label="요청 상세" className="min-h-0 overflow-y-auto">
          {selected ? (
            <>
              <div className="flex items-center justify-between gap-3 border-b border-(--coord-line-soft) px-[22px] py-4">
                <h2 className="text-[15px] font-bold">요청 상세</h2>
                <Link href={`/coordinator/students/${selected.studentId}`} className={`${btn.base} ${btn.outline} ${btn.sm}`}>
                  수료생 보기
                </Link>
              </div>
              <div className="p-[22px]">
                <dl className="grid grid-cols-[84px_1fr] items-center gap-x-3 gap-y-2.5 text-[12.5px]">
                  <dt className="text-(--coord-muted)">요청자</dt>
                  <dd>
                    <b>{selected.studentName}</b>
                  </dd>
                  <dt className="text-(--coord-muted)">교육과정</dt>
                  <dd>{courseName(selected.courseLabel)}</dd>
                  <dt className="text-(--coord-muted)">요청 상태</dt>
                  <dd>
                    <Badge tone={helpTone.OPEN}>{helpLabels.OPEN}</Badge>
                  </dd>
                  <dt className="text-(--coord-muted)">담당자</dt>
                  <dd>
                    <Unlinked title="요청별 담당자 필드가 아직 없어요" />
                  </dd>
                  <dt className="text-(--coord-muted)">필요한 도움</dt>
                  <dd>{selected.jobTitle ? `추천 공고 · ${selected.jobTitle}` : "추천 공고 도움 요청"}</dd>
                  <dt className="text-(--coord-muted)">재요청 여부</dt>
                  <dd className="flex flex-wrap items-center gap-2">
                    <Unlinked title="지난 요청(연락 완료 · 종료) 목록이 없어 재요청인지 알 수 없어요" />
                    {openCount(selected.studentId) > 1 && (
                      <span className="text-[11px] text-(--coord-muted)">접수 중인 요청 {openCount(selected.studentId)}건</span>
                    )}
                  </dd>
                  <dt className="text-(--coord-muted)">선호 연락</dt>
                  <dd>
                    <ChannelChip method={selected.contactMethod} />
                  </dd>
                  <dt className="text-(--coord-muted)">연락처</dt>
                  <dd className="flex flex-wrap items-center gap-x-3 gap-y-1">
                    <span>
                      이메일{" "}
                      {!info ? (
                        <span className="text-(--coord-muted)">불러오는 중…</span>
                      ) : info.state === "error" ? (
                        <span className="text-(--coord-muted)">조회 실패</span>
                      ) : (
                        <b>{info.email ?? "등록 안 됨"}</b>
                      )}
                    </span>
                    <span>
                      전화 <Unlinked title="전화번호를 주는 API 가 아직 없어요" />
                    </span>
                  </dd>
                  <dt className="text-(--coord-muted)">접수 시각</dt>
                  <dd>{monthDayTime(selected.createdAt)}</dd>
                </dl>
                <h3 className="mt-4 text-[13px] font-bold">요청 내용</h3>
                <p className="mt-2 border-l-3 border-(--coord-chart-total) bg-(--coord-tint) px-3 py-2.5 text-xs leading-[1.7] whitespace-pre-wrap">
                  {selected.memo ?? "남긴 메모가 없어요."}
                </p>

                <div className="my-[18px] h-px bg-(--coord-line)" />

                {/* 요청 1건의 처리 기록: 저장 API 가 없어 입력은 막고 미연동으로 안내 */}
                <section aria-label="요청별 처리 기록">
                  <div className="flex items-center justify-between gap-2">
                    <h3 className="text-[13px] font-bold">요청별 처리 기록</h3>
                    <Unlinked title="요청별 처리 기록 저장 API 가 아직 없어요" />
                  </div>
                  <p className="mt-2 rounded-lg bg-(--coord-head) px-3 py-2.5 text-[11px] leading-[1.6] text-(--coord-muted)">
                    요청별 처리 메모 · 후속 연락일 · 상담 예정일을 저장하는 API 가 아직 없어 입력할 수 없어요. 지금은 아래 &lsquo;연락 완료&rsquo;로
                    상태만 바꾸고, 상담 내용은 수료생 상담 기록에 남겨 주세요.
                  </p>
                  <fieldset disabled className="mt-3 grid grid-cols-2 gap-x-3 gap-y-2.5 text-xs opacity-60">
                    <label className="col-span-2 flex flex-col gap-1 text-(--coord-muted)">
                      처리 상태
                      <select className="rounded-lg border border-(--coord-line) bg-(--coord-surface) px-2.5 py-2 text-(--coord-ink)" defaultValue="">
                        <option value="">선택 (미연동)</option>
                        {RECORD_STATUSES.map((s) => (
                          <option key={s}>{s}</option>
                        ))}
                      </select>
                    </label>
                    <label className="col-span-2 flex flex-col gap-1 text-(--coord-muted)">
                      처리 메모
                      <textarea
                        rows={2}
                        placeholder="저장 API 연결 후 입력할 수 있어요"
                        className="resize-none rounded-lg border border-(--coord-line) bg-(--coord-surface) px-2.5 py-2 text-(--coord-ink)"
                      />
                    </label>
                    <label className="flex flex-col gap-1 text-(--coord-muted)">
                      후속 연락일
                      <input type="date" className="rounded-lg border border-(--coord-line) bg-(--coord-surface) px-2.5 py-1.5 text-(--coord-ink)" />
                    </label>
                    <label className="flex flex-col gap-1 text-(--coord-muted)">
                      상담 예정일
                      <input type="datetime-local" className="rounded-lg border border-(--coord-line) bg-(--coord-surface) px-2.5 py-1.5 text-(--coord-ink)" />
                    </label>
                    <button type="button" className={`${btn.base} ${btn.outline} col-span-2`}>
                      처리 기록 저장 (미연동)
                    </button>
                  </fieldset>
                </section>

                <div className="mt-4 flex gap-2">
                  <Link href={`/coordinator/students/${selected.studentId}?log=new`} className={`${btn.base} ${btn.outline} flex-1`}>
                    상담 기록 쓰기
                  </Link>
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
                </div>
                <p className="mt-2 text-[11px] leading-[1.6] text-(--coord-muted)">
                  실제 전화·문자·이메일 발송은 이음에서 하지 않아요. &lsquo;상담 기록 쓰기&rsquo;는 수료생 상세(C3)의 상담 기록으로 이동해요.
                </p>

                <div className="my-[18px] h-px bg-(--coord-line)" />

                <section aria-label="처리 이력">
                  <div className="flex items-center justify-between gap-2">
                    <h3 className="text-[13px] font-bold">이 요청의 처리 이력</h3>
                    <Unlinked title="요청별 처리 이력 API 가 아직 없어요" />
                  </div>
                  <ol className="mt-2 border-l-2 border-(--coord-line) pl-3 text-xs">
                    <li>
                      <b>{monthDayTime(selected.createdAt)}</b> 수료생 앱에서 도움 요청 접수
                    </li>
                  </ol>
                  <h3 className="mt-4 text-[13px] font-bold">수료생 상담 기록 (참고)</h3>
                  <p className="mt-1 text-[11px] text-(--coord-muted)">수료생 전체 상담 기록이에요. 이 요청과 연결된 기록은 아니에요.</p>
                  <ContactLogs info={info} studentId={selected.studentId} />
                </section>
              </div>
            </>
          ) : (
            <p className="p-[35px] text-center text-[13px] text-(--coord-muted)">요청을 선택해 주세요.</p>
          )}
        </Panel>
      </div>

      {confirming && (
        <ContactedDialog request={confirming} busy={busy} error={actionError} onConfirm={() => markContacted(confirming)} onClose={closeConfirm} />
      )}
    </>
  );
}

function Title() {
  return <PageTitle title="도움 요청" description="필요한 도움부터 확인하고, 관련 서류와 이전 연락 기록을 이어 보세요." />;
}

/** API 가 없어 보여 줄 수 없는 값 */
function Unlinked({ title }: { title: string }) {
  return (
    <span title={title} className="rounded-full bg-(--coord-head) px-2 py-0.5 text-[10px] font-semibold text-(--coord-muted)">
      미연동
    </span>
  );
}

/** 선호 연락 수단 칩. 고르지 않았으면 '선호 연락 없음' */
function ChannelChip({ method }: { method: ContactMethod | null }) {
  if (!method) return <span className="text-[11px] text-(--coord-muted)">선호 연락 없음</span>;
  return (
    <span className={`inline-flex items-center gap-1 rounded-full py-0.5 pr-2.5 pl-1.5 text-[11px] font-semibold whitespace-nowrap ${toneClass("green")}`}>
      <Icon name={channelIcon[method]} className="size-3" />
      {contactLabels[method]} 선호
    </span>
  );
}

/** 수료생 상담 기록 최근 3건 (C3 상세 API) */
function ContactLogs({ info, studentId }: { info: StudentInfo | undefined; studentId: number }) {
  if (!info) return <p className="mt-2 text-xs text-(--coord-muted)">불러오는 중…</p>;
  if (info.state === "error") return <p className="mt-2 text-xs text-(--coord-muted)">상담 기록을 불러오지 못했어요.</p>;
  if (info.logs.length === 0) return <p className="mt-2 text-xs text-(--coord-muted)">남긴 상담 기록이 없어요.</p>;
  const recent = [...info.logs].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 3);
  return (
    <>
      <ul className="mt-2 flex flex-col gap-2">
        {recent.map((l) => (
          <li key={l.contactLogId} className="rounded-lg border border-(--coord-line-soft) px-3 py-2 text-xs">
            <p className="text-[11px] text-(--coord-muted)">
              {monthDayTime(l.createdAt)} · {contactTypeLabels[l.contactType]}
            </p>
            <p className="mt-0.5 line-clamp-2 leading-[1.6] whitespace-pre-wrap">{l.note}</p>
          </li>
        ))}
      </ul>
      {info.logs.length > 3 && (
        <Link href={`/coordinator/students/${studentId}`} className="mt-2 inline-block text-xs font-semibold text-(--coord-green) hover:underline">
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
  request: DashboardHelpRequest;
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
        className="flex w-[480px] flex-col gap-3 rounded-[14px] border border-(--coord-line) bg-(--coord-surface) p-6 outline-none"
      >
        <h2 id={titleId} className="text-[19px] font-bold">
          연락 완료로 바꿀까요?
        </h2>
        <dl className="grid grid-cols-[72px_1fr] gap-y-1.5 rounded-lg bg-(--coord-head) px-4 py-3 text-xs">
          <dt className="text-(--coord-muted)">요청자</dt>
          <dd className="font-semibold">
            {request.studentName} · {courseName(request.courseLabel)}
          </dd>
          <dt className="text-(--coord-muted)">필요한 도움</dt>
          <dd>{request.jobTitle ?? "추천 공고 도움 요청"}</dd>
          <dt className="text-(--coord-muted)">선호 연락</dt>
          <dd>{request.contactMethod ? contactLabels[request.contactMethod] : "선호 연락 없음"}</dd>
          <dt className="text-(--coord-muted)">접수</dt>
          <dd>{monthDayTime(request.createdAt)}</dd>
        </dl>
        <ul className="list-disc pl-5 text-[13px] leading-[1.8] text-(--coord-muted)">
          <li>
            요청 상태가 <b className="text-(--coord-ink)">접수 → 연락 완료</b>로 바뀌고 접수 목록에서 빠져요.
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
          <button type="button" onClick={onClose} disabled={busy} className={`${btn.base} ${btn.outline} flex-1 py-3 text-sm`}>
            취소
          </button>
          <button type="button" onClick={onConfirm} disabled={busy} aria-busy={busy} className={`${btn.base} ${btn.primary} flex-1 py-3 text-sm`}>
            {busy ? "처리 중…" : "연락 완료로 바꾸기"}
          </button>
        </div>
      </div>
    </div>
  );
}
