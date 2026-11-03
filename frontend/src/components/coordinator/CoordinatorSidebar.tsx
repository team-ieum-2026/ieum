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
  { href: "/coordinator/stats", label: "통계", icon: "chart", match: (p) => p.startsWith("/coordinator/stats") },
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
    // 리뉴얼 .app-sidebar: 220px · 다크 포레스트 그린 · 활성 메뉴는 민트 바탕
    <aside className="fixed inset-y-0 left-0 z-20 flex w-[220px] flex-col border-r border-black/10 bg-(--coord-forest) px-3 py-4 text-(--coord-side-ink)">
      <Link
        href="/coordinator"
        className="mb-4 flex items-center gap-2.5 rounded-md border-b border-(--coord-surface)/10 px-2 pt-1.5 pb-[18px] focus-visible:outline-3 focus-visible:outline-(--coord-lime)"
      >
        {/* 1차 피드백: '이' 글자 대신 새싹 로고 */}
        <span aria-hidden="true" className="grid size-8 shrink-0 place-items-center rounded-md bg-(--coord-side-logo) text-(--coord-side-logo-ink)">
          <Icon name="sprout" className="size-[22px] stroke-[2.1]" />
        </span>
        <span className="flex min-w-0 flex-col">
          <span className="flex items-center gap-1.5 text-[17px] font-extrabold tracking-[-0.3px] text-(--coord-surface)">
            이음
            <span className="rounded-[3px] bg-(--coord-surface)/20 px-[5px] py-px text-[11px] font-bold text-(--coord-side-tag)">IEUM</span>
          </span>
          <span className="mt-px text-[11px] whitespace-nowrap text-(--coord-side-muted)">청년취업사관학교 사후관리</span>
        </span>
      </Link>
      <p className="px-2 pb-1.5 text-[11px] font-bold tracking-[0.8px] text-(--coord-side-muted)">WORKSPACE</p>
      <nav aria-label="코디네이터 메뉴" className="flex flex-1 flex-col gap-1">
        {menu.map((item) => {
          const active = item.match(pathname);
          const count = item.count && counts && counts[item.count] > 0 ? counts[item.count] : null;
          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={active ? "page" : undefined}
              className={`group flex items-center gap-[11px] rounded-md px-3 py-2.5 text-[14.5px] leading-[18px] transition focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-(--coord-lime) ${
                active
                  ? "bg-(--coord-lime) font-extrabold text-(--coord-side-active-ink)"
                  : "font-semibold hover:bg-(--coord-surface)/8 hover:text-(--coord-surface)"
              }`}
            >
              <span className={`flex ${active ? "" : "opacity-75 group-hover:opacity-100"}`}>
                <Icon name={item.icon} className="size-[18px]" />
              </span>
              {item.label}
              {count !== null && (
                <span
                  className={`ml-auto min-w-[22px] rounded-full px-[7px] py-0.5 text-center text-[11px] font-extrabold ${
                    active ? "bg-(--coord-side-active-ink)/15 text-(--coord-side-active-ink)" : "bg-(--coord-surface)/12 text-(--coord-surface)"
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
      <div className="flex flex-col gap-2 border-t border-(--coord-surface)/10 pt-3.5">
        <Link
          href="/"
          className="flex items-center gap-2 rounded-[4px] px-2 py-1.5 text-[13px] text-(--coord-side-muted) transition hover:bg-(--coord-surface)/8 hover:text-(--coord-surface) focus-visible:outline-3 focus-visible:outline-(--coord-lime)"
        >
          <Icon name="back" className="size-[15px]" />
          홈으로 돌아가기
        </Link>
        <p className="px-2 text-[11px] leading-[1.4] text-(--coord-surface)/40">
          작은 응답을, 다음 기회로.
          <br />
          IEUM COORDINATOR
        </p>
      </div>
    </aside>
  );
}
