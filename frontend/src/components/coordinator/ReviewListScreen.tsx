"use client";
// 주인: FE-B — C4 서류 검토 목록 (11/1 개편 목업 reviewsPage). 목록은 상태별 목록 API(GET /api/coordinator/applications)를 ALL 로 한 번 받아 탭별로 나눈다.
// - 상태 탭: 전체 · 검토 대기(SUBMITTED) · 보완 필요(REJECTED) · 승인 완료(APPROVED) · 전달 완료(DELIVERED).
//   첫 화면은 ?status= 의 탭 (대시보드 서류 검토 현황에서 이동), 없으면 검토 대기
// - 근거 점검 · 수료생 수정: 목록에 없어 지금 탭에 보이는 서류만 C4 상세 API 의 verification 을 병렬로 불러 채운다 (한 번 받은 서류는 다시 받지 않음)
//   근거 점검은 'AI 문장이 원본 자료와 연결됐는지'만 뜻하고, 사실 검증 완료가 아니다
// - 마감일정: 공고 deadlineOn · untilFilled. 목업 모드는 목록 API JSON 이 없어 대시보드 reviewQueue(검토 대기)만 보인다
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { apiErrorText, RefreshAction, Badge, LoadState, PageTitle, Panel, btn } from "@/components/coordinator/CoordUi";
import { docLabels, docTone, monthDay, monthDayTime } from "@/components/coordinator/coordLabels";
import { notifyReviewQueueChanged } from "@/components/coordinator/CoordinatorSidebar";
import { IS_MOCK } from "@/components/coordinator/demoAccount";
import { ApiError } from "@/lib/api/client";
import { getCoordinatorApplication, getCoordinatorApplications } from "@/lib/api/coordinatorReview";
import type { CoordinatorApplicationFilter, CoordinatorApplicationListItem, Verification } from "@/types/coordinator";

/** 목업 탭 순서 */
const TABS: { status: CoordinatorApplicationFilter; label: string }[] = [
  { status: "ALL", label: "전체" },
  { status: "SUBMITTED", label: docLabels.SUBMITTED },
  { status: "REJECTED", label: docLabels.REJECTED },
  { status: "APPROVED", label: docLabels.APPROVED },
  { status: "DELIVERED", label: docLabels.DELIVERED },
];

const EMPTY_TEXT: Record<CoordinatorApplicationFilter, string> = {
  ALL: "검토를 요청한 서류가 없어요.",
  SUBMITTED: "검토할 서류가 없어요.",
  REJECTED: "보완 필요로 처리한 서류가 없어요.",
  APPROVED: "승인 완료한 서류가 없어요.",
  DELIVERED: "전달을 마친 서류가 없어요.",
};

/** 리뉴얼 열 (1440px 실측 비율): 수료생 · 지원 공고 · 검토 요청일 · 마감일정 · 근거 점검 · 수료생 수정 · 검토 상태 · 상세 검토 */
const COLUMNS: [string, string][] = [
  ["수료생", "w-[11%]"],
  ["지원 공고", "w-[24%]"],
  ["검토 요청일", "w-[10%]"],
  ["마감일정", "w-[12%]"],
  ["근거 연결 현황", "w-[15%]"],
  ["수료생 수정", "w-[9%]"],
  ["검토 상태", "w-[10%]"],
  ["상세 검토", "w-[9%]"],
];

/** 서류 한 건의 검증 요약 (C4 상세 API) */
/** mockMissing: 목업 JSON 에 없는 서류 (실제 API 오류와 구분해 '목업 데이터 없음'으로 보여 준다) */
type Check =
  | { state: "loading" }
  | { state: "error"; mockMissing: boolean }
  | { state: "ok"; verification: Verification; hasDocument: boolean };

export default function ReviewListScreen() {
  const [items, setItems] = useState<CoordinatorApplicationListItem[] | null>(null);
  // 고른 탭. 고르기 전에는 주소의 ?status= (대시보드에서 이동), 없거나 틀리면 검토 대기
  const statusParam = useSearchParams().get("status");
  const [pickedTab, setTab] = useState<CoordinatorApplicationFilter | null>(null);
  const tab = pickedTab ?? TABS.find((t) => t.status === statusParam)?.status ?? "SUBMITTED";
  const [checks, setChecks] = useState<Record<number, Check>>({});
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  // 수동 '목록 새로고침': 성공하면 목록을 바꾸고(→ 근거 점검 · 수료생 수정도 다시 채움), 실패하면 지금 목록을 그대로 둔다
  const [refreshing, setRefreshing] = useState(false);
  const [refreshError, setRefreshError] = useState<string | null>(null);

  const refresh = async () => {
    if (refreshing) return;
    setRefreshing(true);
    setRefreshError(null);
    try {
      const next = await getCoordinatorApplications("ALL");
      setChecks({});
      setItems(next);
      notifyReviewQueueChanged(); // 사이드바 · 상단 알림 건수도 같은 시점으로
    } catch (e) {
      setRefreshError(apiErrorText(e, "서류 목록을 다시 불러오지 못했어요."));
    } finally {
      setRefreshing(false);
    }
  };

  useEffect(() => {
    let ignore = false;
    getCoordinatorApplications("ALL")
      .then((list) => {
        if (!ignore) setItems(list);
      })
      .catch((e: unknown) => {
        if (!ignore) setError(apiErrorText(e, "서류 목록을 불러오지 못했어요."));
      });
    return () => {
      ignore = true;
    };
  }, [reloadKey]);

  const visible = (items ?? []).filter((r) => tab === "ALL" || r.status === tab);
  const missingIds = visible.filter((r) => !checks[r.applicationId]).map((r) => r.applicationId);
  const missingKey = missingIds.join(",");

  // 지금 탭에 보이는 서류 중 아직 받지 않은 것만 상세를 병렬로 불러 근거 점검 · 수료생 수정을 채운다
  useEffect(() => {
    if (!missingKey) return;
    let ignore = false;
    const ids = missingKey.split(",").map(Number);
    Promise.allSettled(ids.map((id) => getCoordinatorApplication(id))).then((results) => {
      if (ignore) return;
      const next: Record<number, Check> = {};
      results.forEach((r, i) => {
        next[ids[i]] =
          r.status === "fulfilled"
            ? { state: "ok", verification: r.value.verification, hasDocument: Boolean(r.value.document) }
            : { state: "error", mockMissing: IS_MOCK && r.reason instanceof ApiError && r.reason.code === "APPLICATION_NOT_FOUND" };
      });
      setChecks((m) => ({ ...m, ...next }));
    });
    return () => {
      ignore = true;
    };
  }, [missingKey]);

  return (
    <>
      <PageTitle
        title="서류 검토"
        action={items && <RefreshAction busy={refreshing} error={refreshError} onRefresh={refresh} />}
      />
      {!items ? (
        <LoadState
          error={error}
          loadingText="서류 목록을 불러오는 중이에요…"
          onRetry={() => {
            setError(null);
            setReloadKey((k) => k + 1);
          }}
        />
      ) : (
        // 리뉴얼 reviewsPage: 상태 버튼 줄 · 표 · 아래 안내 (카드는 내용만큼 높이)
        <Panel label="서류 목록">
          <div role="tablist" aria-label="서류 상태" className="flex flex-wrap items-center gap-2 border-b border-(--coord-line) px-5 py-4">
            {TABS.map((t) => {
              const active = t.status === tab;
              const count = items.filter((r) => t.status === "ALL" || r.status === t.status).length;
              return (
                <button
                  key={t.status}
                  type="button"
                  role="tab"
                  aria-selected={active}
                  onClick={() => setTab(t.status)}
                  className={`${btn.base} ${active ? btn.primary : btn.outline} ${btn.sm}`}
                >
                  {t.label} {count}
                </button>
              );
            })}
            {IS_MOCK && <span className="ml-auto text-[13px] text-(--coord-muted)">목업 모드는 검토 대기 서류만 있어요</span>}
          </div>
          <table className="w-full table-fixed border-collapse text-left text-[15px]">
            <caption className="sr-only">서류 목록: 수료생, 지원 공고, 검토 요청일, 마감일정, 근거 연결 현황, 수료생 수정, 검토 상태</caption>
            <thead className="bg-(--coord-head) text-[13.5px] text-(--coord-muted)">
              <tr>
                {COLUMNS.map(([label, w]) => (
                  <th key={label} scope="col" className={`${w} border-b border-(--coord-line) px-4 py-3 font-bold whitespace-nowrap`}>
                    {label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {visible.map((r) => {
                const c = checks[r.applicationId] ?? { state: "loading" };
                return (
                  <tr key={r.applicationId} className="h-[63px] border-b border-(--coord-line) hover:bg-(--coord-hover)">
                    <td className="overflow-hidden px-4 py-3">
                      {/* 1차 피드백: 이름 아바타 없음 */}
                      <Link href={`/coordinator/applications/${r.applicationId}`} className="group flex min-w-0 items-center">
                        <span className="truncate text-[15.5px] font-extrabold underline-offset-4 group-hover:text-(--coord-green) group-hover:underline">
                          {r.studentName}
                        </span>
                      </Link>
                    </td>
                    <td className="px-4 py-3">
                      <span className="line-clamp-2 leading-[1.4] font-bold break-keep" title={r.jobTitle}>
                        {r.jobTitle}
                      </span>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">{monthDayTime(r.submittedAt)}</td>
                    <td className="px-4 py-3">
                      <DeadlineCell deadlineOn={r.deadlineOn} untilFilled={r.untilFilled} />
                    </td>
                    <td className="px-4 py-3">
                      <EvidenceCell check={c} />
                    </td>
                    <td className="px-4 py-3">
                      <EditedCell check={c} />
                    </td>
                    <td className="px-4 py-3">
                      <Badge tone={docTone[r.status]}>{docLabels[r.status]}</Badge>
                    </td>
                    <td className="px-4 py-3">
                      <Link href={`/coordinator/applications/${r.applicationId}`} className={`${btn.base} ${btn.outline} ${btn.sm}`}>
                        {r.status === "SUBMITTED" ? "상세 검토" : "서류 보기"}
                      </Link>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          {visible.length === 0 && <p className="p-9 text-center text-[14.5px] text-(--coord-muted)">{EMPTY_TEXT[tab]}</p>}
          <p className="border-t border-(--coord-line) px-5 py-3.5 text-[13px] text-(--coord-muted)">
            근거 연결 현황은 AI 문장이 원본 자료와 연결됐는지만 보여 줘요. 사실의 진위와 기관 추천 여부는 매니저가 최종 판단해요.
          </p>
        </Panel>
      )}
    </>
  );
}

/** 오늘 0시 기준 마감까지 남은 날 ("2026-11-30" 은 그날 자정까지로 본다) */
const daysLeft = (deadlineOn: string) => {
  const [y, m, d] = deadlineOn.split("-").map(Number);
  const now = new Date();
  return Math.round((Date.UTC(y, m - 1, d) - Date.UTC(now.getFullYear(), now.getMonth(), now.getDate())) / 86_400_000);
};

/** 마감일정: 날짜가 있으면 날짜 + D-day, 없고 채용 시 마감이면 '채용 시 마감' */
function DeadlineCell({ deadlineOn, untilFilled }: { deadlineOn: string | null; untilFilled: boolean }) {
  if (!deadlineOn) {
    return <span className="text-(--coord-muted)">{untilFilled ? "채용 시 마감" : "—"}</span>;
  }
  const left = daysLeft(deadlineOn);
  // 리뉴얼은 마감이 가까우면 빨강 굵게 ("11.03 (D-1 마감)"). 지난 마감 · 여유 있는 마감은 기본색
  const urgent = left >= 0 && left <= 3;
  return (
    <span className="flex flex-col gap-0.5">
      <b className={`whitespace-nowrap ${urgent ? "text-(--coord-red)" : left < 0 ? "font-normal text-(--coord-muted)" : ""}`}>
        {monthDay(deadlineOn)} ({left < 0 ? "마감" : left === 0 ? "D-day" : `D-${left}`})
      </b>
      {untilFilled && <span className="text-[12px] text-(--coord-faint)">채용 시 마감</span>}
    </span>
  );
}

/** 근거 점검: AI 문장 중 원본 자료와 연결된 수 (사실 검증 완료가 아님) */
function EvidenceCell({ check }: { check: Check }) {
  if (check.state === "loading") return <span className="text-(--coord-muted)">불러오는 중…</span>;
  if (check.state === "error") return <span className="text-(--coord-muted)">{check.mockMissing ? "목업 데이터 없음" : "조회 실패"}</span>;
  if (!check.hasDocument) return <span className="text-(--coord-muted)">서류 없음</span>;
  const { aiSentenceCount: total, aiSentenceWithEvidence: linked } = check.verification;
  if (total === 0) return <span className="text-(--coord-muted)">AI 문장 없음</span>;
  return linked === total ? (
    <Badge tone="green">
      AI 문장 근거 연결 {linked}/{total}
    </Badge>
  ) : (
    <Badge tone="orange">근거 확인 필요 {total - linked}문장</Badge>
  );
}

/** 수료생 수정: 수료생이 직접 쓰거나 고친 곳 수 (서버 userEditedCount) */
function EditedCell({ check }: { check: Check }) {
  if (check.state === "loading") return <span className="text-(--coord-muted)">불러오는 중…</span>;
  if (check.state === "error") return <span className="text-(--coord-muted)">{check.mockMissing ? "목업 데이터 없음" : "조회 실패"}</span>;
  if (!check.hasDocument) return <span className="text-(--coord-muted)">—</span>;
  return <span>{check.verification.userEditedCount}곳</span>;
}
