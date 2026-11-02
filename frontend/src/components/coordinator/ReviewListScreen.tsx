"use client";
// 주인: FE-B — C4 서류 검토 목록 (11/1 개편 목업 reviewsPage). 목록은 대시보드 API 의 reviewQueue(검토 대기 SUBMITTED)만.
// - 상태 탭: 전체 · 수정 요청 · 승인·전달 대기 · 전달 완료는 상태별 목록 API 가 없어 '미연동'으로 막는다 (0건 표시 안 함)
// - 근거 점검 · 수료생 수정: 목록에 없어 이 목록의 서류만 C4 상세 API 의 verification 을 병렬로 불러 채운다
//   근거 점검은 'AI 문장이 원본 자료와 연결됐는지'만 뜻하고, 사실 검증 완료가 아니다
// - 마감일정: API 에 없어 '미연동' (BE-B 회신 ④ deadlineOn · untilFilled 가 오면 채움)
import Link from "next/link";
import { useEffect, useState } from "react";
import { Badge, LoadState, PageTitle, Panel, btn } from "@/components/coordinator/CoordUi";
import { docLabels, docTone, monthDayTime } from "@/components/coordinator/coordLabels";
import { IS_MOCK } from "@/components/coordinator/demoAccount";
import { ApiError } from "@/lib/api/client";
import { getCoordinatorApplication, getDashboard } from "@/lib/api/coordinatorReview";
import type { ReviewQueueItem, Verification } from "@/types/coordinator";

/** 목업 탭 순서. 검토 대기만 실제 목록이 있다 */
const TABS: { label: string; connected: boolean }[] = [
  { label: "전체", connected: false },
  { label: "검토 대기", connected: true },
  { label: "수정 요청", connected: false },
  { label: "승인 · 전달 대기", connected: false },
  { label: "전달 완료", connected: false },
];

/** 목업 열: 수료생 · 지원 공고 · 제출일자 · 마감일정 · 근거 점검 · 수료생 수정 · 검토 상태 · 상세 */
const COLUMNS: [string, string][] = [
  ["수료생", "w-[11%]"],
  ["지원 공고", "w-[22%]"],
  ["제출일자", "w-[11%]"],
  ["마감일정", "w-[10%]"],
  ["근거 점검", "w-[15%]"],
  ["수료생 수정", "w-[10%]"],
  ["검토 상태", "w-[10%]"],
  ["상세", "w-[11%]"],
];

/** 서류 한 건의 검증 요약 (C4 상세 API) */
/** mockMissing: 목업 JSON 에 없는 서류 (실제 API 오류와 구분해 '목업 데이터 없음'으로 보여 준다) */
type Check =
  | { state: "loading" }
  | { state: "error"; mockMissing: boolean }
  | { state: "ok"; verification: Verification; hasDocument: boolean };

export default function ReviewListScreen() {
  const [items, setItems] = useState<ReviewQueueItem[] | null>(null);
  const [checks, setChecks] = useState<Record<number, Check>>({});
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let ignore = false;
    getDashboard()
      .then((d) => {
        if (!ignore) setItems([...d.reviewQueue].sort((a, b) => a.submittedAt.localeCompare(b.submittedAt)));
      })
      .catch((e: unknown) => {
        if (!ignore) setError(e instanceof ApiError ? e.message : "서류 목록을 불러오지 못했어요.");
      });
    return () => {
      ignore = true;
    };
  }, [reloadKey]);

  // 목록에 있는 서류만 상세를 병렬로 불러 근거 점검 · 수료생 수정을 채운다
  useEffect(() => {
    if (!items) return;
    let ignore = false;
    const ids = items.map((r) => r.applicationId);
    Promise.allSettled(ids.map((id) => getCoordinatorApplication(id))).then((results) => {
      if (ignore) return;
      const next: Record<number, Check> = {};
      results.forEach((r, i) => {
        next[ids[i]] =
          r.status === "fulfilled"
            ? { state: "ok", verification: r.value.verification, hasDocument: Boolean(r.value.document) }
            : { state: "error", mockMissing: IS_MOCK && r.reason instanceof ApiError && r.reason.code === "APPLICATION_NOT_FOUND" };
      });
      setChecks(next);
    });
    return () => {
      ignore = true;
    };
  }, [items]);

  return (
    <>
      <PageTitle title="서류 검토" description="자기소개서 전체 맥락과 문장별 근거를 보고, 개입할 지점을 판단하세요." />
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
        // 목업처럼 표 카드가 화면 높이를 채우고 안내 문구는 카드 아래쪽에 붙는다 (표가 길면 표 안에서 스크롤)
        <Panel label="서류 목록" className="flex min-h-[420px] flex-col lg:h-[calc(100vh-234px)]">
          <div role="tablist" aria-label="서류 상태" className="flex shrink-0 flex-wrap items-center gap-1 border-b border-(--coord-line) px-5 py-3.5">
            {TABS.map((t) =>
              t.connected ? (
                <span
                  key={t.label}
                  role="tab"
                  aria-selected="true"
                  className="rounded-[7px] bg-(--coord-green-soft) px-3.5 py-2 text-xs font-semibold text-(--coord-forest)"
                >
                  {t.label} <b className="ml-1 text-[10px]">{items.length}</b>
                </span>
              ) : (
                <button
                  key={t.label}
                  type="button"
                  role="tab"
                  aria-selected="false"
                  disabled
                  title="이 상태의 목록을 주는 API 가 아직 없어요"
                  className="cursor-not-allowed rounded-[7px] px-3.5 py-2 text-xs text-(--coord-muted) opacity-70"
                >
                  {t.label} <span className="ml-1 text-[10px]">미연동</span>
                </button>
              ),
            )}
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto">
            <table className="w-full table-fixed border-collapse text-left text-xs">
              <caption className="sr-only">검토 대기 서류: 수료생, 지원 공고, 제출일자, 마감일정, 근거 점검, 수료생 수정, 검토 상태</caption>
              <thead className="sticky top-0 z-[1] bg-(--coord-head) text-[11px] text-(--coord-muted)">
                <tr>
                  {COLUMNS.map(([label, w]) => (
                    <th key={label} scope="col" className={`${w} px-[17px] py-[13px] font-medium ${label === "상세" ? "text-right" : ""}`}>
                      {label === "상세" ? <span className="sr-only">상세</span> : label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {items.map((r) => {
                  const c = checks[r.applicationId] ?? { state: "loading" };
                  return (
                    <tr key={r.applicationId} className="h-[58px] border-b border-(--coord-line-soft) hover:bg-(--coord-hover)">
                      <td className="px-[17px]">
                        <Link
                          href={`/coordinator/applications/${r.applicationId}`}
                          className="block truncate text-[13px] font-bold underline-offset-4 hover:text-(--coord-green) hover:underline"
                        >
                          {r.studentName}
                        </Link>
                      </td>
                      <td className="px-[17px]">
                        <span className="line-clamp-2 leading-[1.45] font-semibold">{r.jobTitle}</span>
                      </td>
                      <td className="px-[17px]">{monthDayTime(r.submittedAt)}</td>
                      <td className="px-[17px]">
                        <span className="text-(--coord-muted)" title="마감일을 주는 API 가 아직 없어요">
                          미연동
                        </span>
                      </td>
                      <td className="px-[17px]">
                        <EvidenceCell check={c} />
                      </td>
                      <td className="px-[17px]">
                        <EditedCell check={c} />
                      </td>
                      <td className="px-[17px]">
                        <Badge tone={docTone.SUBMITTED}>{docLabels.SUBMITTED}</Badge>
                      </td>
                      <td className="px-[17px] text-right">
                        <Link href={`/coordinator/applications/${r.applicationId}`} className={`${btn.base} ${btn.outline} ${btn.sm}`}>
                          상세 검토
                        </Link>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            {items.length === 0 && <p className="p-[35px] text-center text-[13px] text-(--coord-muted)">검토할 서류가 없어요.</p>}
          </div>
          <p className="shrink-0 p-[22px] text-xs text-(--coord-muted)">
            근거 점검은 AI 문장이 원본 자료와 연결됐는지만 보여 줘요. 사실의 진위와 기관 추천 여부는 코디네이터가 최종 판단해요.
          </p>
        </Panel>
      )}
    </>
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
      근거 연결 {linked}/{total}
    </Badge>
  ) : (
    <Badge tone="red">근거 확인 필요 {total - linked}문장</Badge>
  );
}

/** 수료생 수정: 수료생이 직접 쓰거나 고친 곳 수 (서버 userEditedCount) */
function EditedCell({ check }: { check: Check }) {
  if (check.state === "loading") return <span className="text-(--coord-muted)">불러오는 중…</span>;
  if (check.state === "error") return <span className="text-(--coord-muted)">{check.mockMissing ? "목업 데이터 없음" : "조회 실패"}</span>;
  if (!check.hasDocument) return <span className="text-(--coord-muted)">—</span>;
  return <span>{check.verification.userEditedCount}곳</span>;
}
