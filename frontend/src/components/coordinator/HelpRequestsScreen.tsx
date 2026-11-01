"use client";
// 주인: FE-B — 도움 요청 목록 · 상세 (11/1 개편 목업, 신규 화면). 데이터는 대시보드 API 의 helpRequests(접수 OPEN 만).
// - 전화 · 문자 · 이메일은 수료생이 앱에서 고른 '선호 연락 수단', memo 는 수료생이 남긴 말
// - '연락 완료' → PATCH /api/coordinator/help-requests/{id} { status: CONTACTED } 성공 뒤에만 목록 · 건수 갱신
//   CONTACTED 는 '연락 완료'이지 '요청 완료'가 아니다 ('완료'로 표시하지 않음)
// - '상담 기록 쓰기'는 이 수료생의 C3 상담 기록으로 이동 (요청과 기록이 자동 연결되지는 않음)
// 백엔드 회신 전이라 숨긴 것: 요청 상태 필터 · 이력(전체 목록 GET), 완료 처리(CLOSED PATCH), 처리 메모 · 상담 일시 · 담당자
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { notifyReviewQueueChanged } from "@/components/coordinator/CoordinatorSidebar";
import { Badge, Icon, LoadState, PageTitle, Panel, btn, toneClass, type IconName } from "@/components/coordinator/CoordUi";
import { contactLabels, courseName, helpLabels, helpTone, monthDayTime } from "@/components/coordinator/coordLabels";
import { ApiError } from "@/lib/api/client";
import { getDashboard, updateHelpRequestStatus } from "@/lib/api/coordinatorReview";
import type { DashboardHelpRequest } from "@/types/coordinator";
import type { ContactMethod } from "@/types/student";

const CHANNELS: (ContactMethod | "ALL")[] = ["ALL", "PHONE", "SMS", "EMAIL"];
const channelIcon: Record<ContactMethod, IconName> = { PHONE: "phone", SMS: "sms", EMAIL: "mail" };

export default function HelpRequestsScreen() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const selectedId = Number(searchParams.get("id")) || null;

  const [list, setList] = useState<DashboardHelpRequest[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [channel, setChannel] = useState<ContactMethod | "ALL">("ALL");
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
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

  const filtered = list.filter((r) => channel === "ALL" || r.contactMethod === channel);
  const selected = filtered.find((r) => r.helpRequestId === selectedId) ?? filtered[0] ?? null;
  const select = (id: number) => router.replace(`${pathname}?id=${id}`, { scroll: false });

  const markContacted = async (r: DashboardHelpRequest) => {
    setBusy(true);
    setActionError(null);
    try {
      await updateHelpRequestStatus(r.helpRequestId, { status: "CONTACTED" });
      setDone({ name: r.studentName, studentId: r.studentId });
      router.replace(pathname, { scroll: false });
      setReloadKey((k) => k + 1);
      notifyReviewQueueChanged();
    } catch (e) {
      if (e instanceof ApiError && (e.status === 404 || e.status === 409)) {
        setActionError("이미 처리됐거나 없는 요청이에요. 목록을 새로 불러왔어요.");
        setReloadKey((k) => k + 1);
        notifyReviewQueueChanged();
      } else {
        setActionError(e instanceof ApiError ? e.message : "연락 완료로 바꾸지 못했어요. 다시 시도해 주세요.");
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
          </span>
          <Link href={`/coordinator/students/${done.studentId}?log=new`} className="font-bold underline-offset-4 hover:underline">
            상담 기록 쓰기
          </Link>
        </p>
      )}
      <div className="grid grid-cols-[minmax(0,1.35fr)_minmax(330px,1fr)] items-start gap-5">
        <Panel label="접수된 도움 요청">
          <div className="flex flex-wrap items-center gap-2.5 border-b border-(--coord-line) px-5 py-4">
            <span className="text-[11px] font-bold text-(--coord-muted)">선호 연락</span>
            <div className="flex flex-wrap gap-1" role="group" aria-label="선호 연락 필터">
              {CHANNELS.map((ch) => (
                <button
                  key={ch}
                  type="button"
                  aria-pressed={channel === ch}
                  onClick={() => setChannel(ch)}
                  className={`rounded-[7px] px-3.5 py-2 text-xs focus-visible:outline-3 focus-visible:outline-(--coord-green) ${
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
            <ul>
              {filtered.map((r) => {
                const active = r.helpRequestId === selected?.helpRequestId;
                return (
                  <li key={r.helpRequestId}>
                    <button
                      type="button"
                      aria-current={active ? "true" : undefined}
                      onClick={() => select(r.helpRequestId)}
                      className={`grid w-full grid-cols-[1fr_auto] gap-3 border-b border-(--coord-line) px-5 py-[19px] text-left focus-visible:outline-3 focus-visible:-outline-offset-3 focus-visible:outline-(--coord-green) ${
                        active ? "bg-(--coord-tint) shadow-[inset_3px_0_var(--coord-green)]" : "hover:bg-(--coord-hover)"
                      }`}
                    >
                      <div className="min-w-0">
                        <b className="text-sm">{r.studentName}</b>
                        <h3 className="mt-1.5 mb-1 truncate text-[13px] font-bold">{r.jobTitle ?? "추천 공고 도움 요청"}</h3>
                        <p className="flex flex-wrap items-center gap-2 text-[11px] text-(--coord-muted)">
                          <ChannelChip method={r.contactMethod} />
                          <span>{monthDayTime(r.createdAt)} 접수</span>
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
        <Panel label="요청 상세" className="sticky top-[80px]">
          {selected ? (
            <>
              <div className="border-b border-(--coord-line-soft) px-[22px] py-5">
                <h2 className="text-[15px] font-bold">요청 상세</h2>
              </div>
              <div className="p-[22px]">
                <dl className="grid grid-cols-[84px_1fr] items-center gap-2.5 text-[12.5px]">
                  <dt className="text-(--coord-muted)">요청자</dt>
                  <dd className="flex items-center justify-between gap-2">
                    <b>{selected.studentName}</b>
                    <Link href={`/coordinator/students/${selected.studentId}`} className={`${btn.base} ${btn.outline} ${btn.sm}`}>
                      수료생 보기
                    </Link>
                  </dd>
                  <dt className="text-(--coord-muted)">교육과정</dt>
                  <dd>{courseName(selected.courseLabel)}</dd>
                  <dt className="text-(--coord-muted)">요청 상태</dt>
                  <dd>
                    <Badge tone={helpTone.OPEN}>{helpLabels.OPEN}</Badge>
                  </dd>
                  <dt className="text-(--coord-muted)">관련 공고</dt>
                  <dd>{selected.jobTitle ?? "기록 없음"}</dd>
                  <dt className="text-(--coord-muted)">선호 연락</dt>
                  <dd>
                    <ChannelChip method={selected.contactMethod} />
                  </dd>
                  <dt className="text-(--coord-muted)">접수 시각</dt>
                  <dd>{monthDayTime(selected.createdAt)}</dd>
                </dl>
                <div className="my-[18px] h-px bg-(--coord-line)" />
                <h4 className="text-[13px] font-bold">수료생이 전한 말</h4>
                <p className="mt-2 border-l-3 border-(--coord-chart-total) bg-(--coord-tint) px-3 py-2.5 text-xs whitespace-pre-wrap">
                  {selected.memo ?? "남긴 메모가 없어요."}
                </p>
                <div className="my-[18px] h-px bg-(--coord-line)" />
                {actionError && (
                  <p role="alert" className="mb-3 text-xs font-bold text-(--coord-red)">
                    {actionError}
                  </p>
                )}
                <div className="flex gap-2">
                  <Link href={`/coordinator/students/${selected.studentId}?log=new`} className={`${btn.base} ${btn.outline} flex-1`}>
                    상담 기록 쓰기
                  </Link>
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => markContacted(selected)}
                    aria-label={`${selected.studentName} 도움 요청 연락 완료`}
                    className={`${btn.base} ${btn.primary} flex-1`}
                  >
                    {busy ? "처리 중…" : "연락 완료"}
                  </button>
                </div>
                <p className="mt-3 text-[11px] leading-[1.6] text-(--coord-muted)">
                  연락 완료로 바꾸면 접수 목록에서 빠져요. 실제 전화·문자·이메일 발송은 이음에서 하지 않아요.
                </p>
              </div>
            </>
          ) : (
            <p className="p-[35px] text-center text-[13px] text-(--coord-muted)">요청을 선택해 주세요.</p>
          )}
        </Panel>
      </div>
    </>
  );
}

function Title() {
  return <PageTitle title="도움 요청" description="필요한 도움부터 확인하고, 관련 서류와 이전 연락 기록을 이어 보세요." />;
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
