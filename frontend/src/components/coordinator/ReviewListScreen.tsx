"use client";
// 주인: FE-B — C4 서류 검토 목록 (11/1 개편 목업). 검토 대기(SUBMITTED)만: 대시보드 API 의 reviewQueue.
// 다른 상태 탭(수정 요청 · 승인 · 전달)은 전체 목록 API 가 없어 숨긴다. 마감일정 · 근거 점검 · 수료생 수정 열도
// 목록 응답에 값이 없어 숨긴다 (마감일정은 BE-B 회신 ④ deadlineOn · untilFilled 가 오면 추가).
import Link from "next/link";
import { useEffect, useState } from "react";
import { Avatar, Badge, LoadState, PageTitle, Panel, btn } from "@/components/coordinator/CoordUi";
import { docLabels, docTone, monthDayTime } from "@/components/coordinator/coordLabels";
import { ApiError } from "@/lib/api/client";
import { getDashboard } from "@/lib/api/coordinatorReview";
import type { ReviewQueueItem } from "@/types/coordinator";

export default function ReviewListScreen() {
  const [items, setItems] = useState<ReviewQueueItem[] | null>(null);
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
        <Panel label="검토 대기 서류" className="flex min-h-[420px] flex-col lg:h-[calc(100vh-234px)]">
          <div className="flex shrink-0 items-center gap-2 border-b border-(--coord-line) px-5 py-4">
            <span className="rounded-[7px] bg-(--coord-green-soft) px-3.5 py-2 text-xs font-semibold text-(--coord-forest)">
              검토 대기 <b className="ml-1 text-[10px]">{items.length}</b>
            </span>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto">
          <table className="w-full table-fixed border-collapse text-left text-xs">
            <caption className="sr-only">검토 대기 서류: 수료생, 지원 공고, 제출일자, 검토 상태</caption>
            <thead className="bg-(--coord-head) text-[11px] text-(--coord-muted)">
              <tr>
                <th scope="col" className="w-[22%] px-[17px] py-[15px] font-medium">수료생</th>
                <th scope="col" className="w-[38%] px-[17px] py-[15px] font-medium">지원 공고</th>
                <th scope="col" className="w-[16%] px-[17px] py-[15px] font-medium">제출일자</th>
                <th scope="col" className="w-[12%] px-[17px] py-[15px] font-medium">검토 상태</th>
                <th scope="col" className="w-[12%] px-[17px] py-[15px] font-medium">
                  <span className="sr-only">상세</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {items.map((r) => (
                <tr key={r.applicationId} className="border-b border-(--coord-line-soft) hover:bg-(--coord-hover)">
                  <td className="px-[17px] py-[15px]">
                    <Link href={`/coordinator/applications/${r.applicationId}`} className="flex items-center gap-2.5 font-semibold hover:text-(--coord-green)">
                      <Avatar name={r.studentName} />
                      <span className="truncate">{r.studentName}</span>
                    </Link>
                  </td>
                  <td className="truncate px-[17px] py-[15px] font-semibold">{r.jobTitle}</td>
                  <td className="px-[17px] py-[15px]">{monthDayTime(r.submittedAt)}</td>
                  <td className="px-[17px] py-[15px]">
                    <Badge tone={docTone.SUBMITTED}>{docLabels.SUBMITTED}</Badge>
                  </td>
                  <td className="px-[17px] py-[15px] text-right">
                    <Link href={`/coordinator/applications/${r.applicationId}`} className={`${btn.base} ${btn.outline} ${btn.sm}`}>
                      상세 검토
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {items.length === 0 && <p className="p-[35px] text-center text-[13px] text-(--coord-muted)">검토할 서류가 없어요.</p>}
          </div>
          <p className="shrink-0 p-[22px] text-xs text-(--coord-muted)">
            AI 점검은 원본 자료와의 연결 여부예요. 사실의 진위와 기관 추천 여부는 코디네이터가 최종 판단해요.
          </p>
        </Panel>
      )}
    </>
  );
}
