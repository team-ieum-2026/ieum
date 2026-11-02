"use client";
// 주인: FE-B — 코디네이터 사이드바 (FE-B-09, 11/1 개편 목업). 메뉴 5개 + 서류 검토 · 도움 요청 대기 건수
// 건수는 대시보드 API 의 reviewQueue · helpRequests 길이 (docs/API.md 2-3). 불러오는 중 · 실패면 배지를 숨긴다.
// 목업의 계정 · 설정 메뉴는 이번 범위에서 제외 (로그인 없음, AGENTS.md 9).
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { Icon, type IconName } from "@/components/coordinator/CoordUi";
import { getDashboard } from "@/lib/api/coordinatorReview";

/** C1 의 서류 검토 대기 영역 id */
export const REVIEW_QUEUE_ANCHOR = "review-queue";

const REVIEW_QUEUE_CHANGED = "ieum:review-queue-changed";

/** 서류 승인 · 반려 · 도움 요청 처리 등으로 대기 목록이 바뀌었을 때 호출. 사이드바가 API 로 건수를 다시 센다 (숫자를 직접 빼지 않음) */
export function notifyReviewQueueChanged() {
  window.dispatchEvent(new Event(REVIEW_QUEUE_CHANGED));
}

/** 위 알림을 받아 다시 조회할 때 쓴다 (사이드바 · 상단 알림). 해제 함수를 돌려준다 */
export function onReviewQueueChanged(listener: () => void) {
  window.addEventListener(REVIEW_QUEUE_CHANGED, listener);
  return () => window.removeEventListener(REVIEW_QUEUE_CHANGED, listener);
}

type Counts = { review: number; help: number };

const menu: { href: string; label: string; icon: IconName; match: (p: string) => boolean; count?: keyof Counts }[] = [
  { href: "/coordinator", label: "오늘의 업무", icon: "home", match: (p) => p === "/coordinator" },
  { href: "/coordinator/students", label: "수료생 관리", icon: "people", match: (p) => p.startsWith("/coordinator/students") },
  {
    href: "/coordinator/applications",
    label: "서류 검토",
    icon: "doc",
    match: (p) => p.startsWith("/coordinator/applications"),
    count: "review",
  },
  {
    href: "/coordinator/help-requests",
    label: "도움 요청",
    icon: "help",
    match: (p) => p.startsWith("/coordinator/help-requests"),
    count: "help",
  },
  { href: "/coordinator/stats", label: "취업·지원 현황", icon: "chart", match: (p) => p.startsWith("/coordinator/stats") },
];

export default function CoordinatorSidebar() {
  const pathname = usePathname();
  const [counts, setCounts] = useState<Counts | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  // 화면을 옮기지 않아도 처리 알림이 오면 다시 센다
  useEffect(() => onReviewQueueChanged(() => setRefreshKey((k) => k + 1)), []);

  // 화면을 옮길 때마다 · 처리 알림마다 서버 기준으로 다시 센다. 실패하면 배지를 숨긴다 (임의 숫자 표시 안 함)
  useEffect(() => {
    let ignore = false;
    getDashboard()
      .then((d) => {
        if (!ignore) setCounts({ review: d.reviewQueue.length, help: d.helpRequests.length });
      })
      .catch(() => {
        if (!ignore) setCounts(null);
      });
    return () => {
      ignore = true;
    };
  }, [pathname, refreshKey]);

  return (
    <aside className="fixed inset-y-0 left-0 z-20 flex w-[212px] flex-col bg-(--coord-forest) px-4 py-[26px] text-(--coord-side-ink)">
      <div className="flex items-center gap-2.5 px-3 text-[26px] leading-none font-bold tracking-[-1.5px] text-(--coord-surface)">
        <span aria-hidden="true" className="relative inline-block h-[30px] w-7">
          <span className="absolute top-1 left-0.5 h-[22px] w-3.5 -rotate-[28deg] rounded-[14px_3px_14px_3px] bg-(--coord-lime)" />
          <span className="absolute top-0 left-[13px] h-[17px] w-[11px] rotate-[35deg] rounded-[14px_3px_14px_3px] bg-(--coord-chart-employed)" />
        </span>
        <span>
          이음
          <span className="mt-1 block text-[10px] font-medium tracking-[2px]">IEUM</span>
        </span>
      </div>
      <p className="mx-2.5 mt-[26px] mb-[27px] border-b border-(--coord-side-muted)/20 pb-[22px] text-[11px]">
        <b className="mb-1 block text-xs text-(--coord-surface)">청년취업사관학교</b>
        수료생 사후관리 워크스페이스
      </p>
      <p className="mx-3.5 mt-2 mb-2.5 text-[10px] tracking-[1.5px] text-(--coord-side-muted)">WORKSPACE</p>
      <nav aria-label="코디네이터 메뉴" className="flex flex-col gap-1">
        {menu.map((item) => {
          const active = item.match(pathname);
          const count = item.count && counts && counts[item.count] > 0 ? counts[item.count] : null;
          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={active ? "page" : undefined}
              className={`flex items-center gap-[11px] rounded-lg px-[13px] py-3 text-[13px] focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-(--coord-lime) ${
                active ? "bg-(--coord-lime) font-bold text-(--coord-forest)" : "hover:bg-(--coord-surface)/5"
              }`}
            >
              <Icon name={item.icon} />
              {item.label}
              {count !== null && (
                <span
                  className={`ml-auto rounded-[5px] px-[5px] py-px text-[10px] ${
                    active ? "bg-(--coord-forest)/10" : "bg-(--coord-surface)/10"
                  }`}
                >
                  {count}
                  <span className="sr-only">건 대기</span>
                </span>
              )}
            </Link>
          );
        })}
      </nav>
      <p className="mt-auto px-3 text-[10px] leading-[1.8] text-(--coord-side-muted)">
        작은 응답을, 다음 기회로.
        <br />
        IEUM COORDINATOR
      </p>
    </aside>
  );
}
