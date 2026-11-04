"use client";
// 주인: FE-B — 코디네이터 사이드바 (FE-B-09, 11/1 개편 목업). 메뉴 5개 + 서류 검토 · 도움 요청 대기 건수
// 건수는 대시보드 API 의 reviewQueue · helpRequests 길이 (docs/API.md 2-3). 불러오는 중 · 실패면 배지를 숨긴다.
// 목업의 계정 · 설정 메뉴는 이번 범위에서 제외 (로그인 없음, AGENTS.md 9).
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { Icon, type IconName } from "@/components/coordinator/CoordUi";
import StudentIcon from "@/components/student/StudentIcon";
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
    // 리뉴얼 .app-sidebar: 220px · 딥 그린 바탕(11/4 그린 팔레트 시안) · 활성 메뉴는 메인 그린
    <aside className="fixed inset-y-0 left-0 z-20 flex w-[220px] flex-col border-r border-(--coord-side-line) bg-(--coord-side-bg) px-3 py-4 text-(--coord-side-ink)">
      <Link
        href="/coordinator"
        className="mb-4 flex items-center gap-2 rounded-lg px-2.5 py-2.5 focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-(--coord-side-focus)"
      >
        {/* 수료생 화면 로고와 같은 반짝이는 별 + '이음' (아이콘은 StudentIcon 재사용, 크기 · 굵기는 student.css .s-brand-mark · .s-brand-label 과 같게) */}
        <span aria-hidden="true" className="grid h-9 w-8 shrink-0 place-items-center text-(--coord-side-logo-mark)">
          <StudentIcon name="sparkles" className="size-[30px] stroke-[2.2]" />
        </span>
        <span className="flex min-w-0 flex-col">
          <span className="text-[27px] leading-[1.1] font-extrabold tracking-[-0.06em] text-(--coord-side-logo-ink)">이음</span>
          <span className="mt-0.5 text-[11px] whitespace-nowrap text-(--coord-side-logo-sub)">청년취업사관학교 사후관리</span>
        </span>
      </Link>
      <p className="px-2 pb-1.5 text-[11px] font-bold tracking-[0.8px] text-(--coord-side-muted)">WORKSPACE</p>
      <nav aria-label="매니저 메뉴" className="flex flex-1 flex-col gap-1">
        {menu.map((item) => {
          const active = item.match(pathname);
          const count = item.count && counts && counts[item.count] > 0 ? counts[item.count] : null;
          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={active ? "page" : undefined}
              className={`group flex items-center gap-[11px] rounded-md px-3 py-2.5 text-[14.5px] leading-[18px] transition focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-(--coord-side-focus) ${
                active
                  ? "bg-(--coord-side-active) font-extrabold text-(--coord-side-active-ink)"
                  : "font-semibold hover:bg-(--coord-side-hover) hover:text-(--coord-side-hover-ink)"
              }`}
            >
              <span className={`flex ${active ? "" : "opacity-75 group-hover:opacity-100"}`}>
                <Icon name={item.icon} className="size-[18px]" />
              </span>
              {item.label}
              {count !== null && (
                <span
                  className={`ml-auto min-w-[22px] rounded-full px-[7px] py-0.5 text-center text-[11px] font-extrabold ${
                    active ? "bg-(--coord-side-active-ink)/15 text-(--coord-side-active-ink)" : "bg-(--coord-side-active) text-(--coord-side-active-ink)"
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
    </aside>
  );
}
