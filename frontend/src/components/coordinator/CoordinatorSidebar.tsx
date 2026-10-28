"use client";
// 주인: FE-B — 코디네이터 사이드바 (FE-B-09). 현재 메뉴 강조 + 서류 검토 대기 건수
// 대기 건수는 대시보드 API 의 reviewQueue 길이 (docs/API.md 2-3). 불러오는 중 · 실패면 배지를 숨긴다.
// 서류 검토 목록 화면 · API 가 없어서 "서류 검토"는 C1 의 서류 검토 대기 영역(#review-queue)으로 보낸다.
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { getDashboard } from "@/lib/api/coordinatorReview";

/** C1 의 서류 검토 대기 영역 id (FE-B-07 에서 이 id 를 붙인다) */
export const REVIEW_QUEUE_ANCHOR = "review-queue";

const REVIEW_QUEUE_CHANGED = "ieum:review-queue-changed";

/** C4 승인 · 반려 등으로 대기 목록이 바뀌었을 때 호출. 사이드바가 API 로 건수를 다시 센다 (숫자를 직접 빼지 않음) */
export function notifyReviewQueueChanged() {
  window.dispatchEvent(new Event(REVIEW_QUEUE_CHANGED));
}

const menu = [
  { href: "/coordinator", label: "대시보드", match: (p: string) => p === "/coordinator" },
  { href: "/coordinator/students", label: "수료생 목록", match: (p: string) => p.startsWith("/coordinator/students") },
  {
    href: `/coordinator#${REVIEW_QUEUE_ANCHOR}`,
    label: "서류 검토",
    match: (p: string) => p.startsWith("/coordinator/applications"),
    showCount: true,
  },
] as const;

export default function CoordinatorSidebar() {
  const pathname = usePathname();
  const [reviewCount, setReviewCount] = useState<number | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  // C4 에서 처리하면 화면을 옮기지 않아도 다시 센다
  useEffect(() => {
    const onChanged = () => setRefreshKey((k) => k + 1);
    window.addEventListener(REVIEW_QUEUE_CHANGED, onChanged);
    return () => window.removeEventListener(REVIEW_QUEUE_CHANGED, onChanged);
  }, []);

  // 화면을 옮길 때마다 · 처리 알림마다 서버 기준으로 다시 센다. 실패하면 배지를 숨긴다 (임의 숫자 표시 안 함)
  useEffect(() => {
    let ignore = false;
    getDashboard()
      .then((d) => {
        if (!ignore) setReviewCount(d.reviewQueue.length);
      })
      .catch(() => {
        if (!ignore) setReviewCount(null);
      });
    return () => {
      ignore = true;
    };
  }, [pathname, refreshKey]);

  return (
    <aside className="flex w-60 shrink-0 flex-col border-r border-line bg-surface p-4">
      <p className="mb-6 px-2 text-lg font-bold">이음 코디네이터</p>
      <nav aria-label="코디네이터 메뉴" className="flex flex-col gap-1">
        {menu.map((item) => {
          const active = item.match(pathname);
          const count = "showCount" in item && reviewCount !== null && reviewCount > 0 ? reviewCount : null;
          return (
            <Link
              key={item.label}
              href={item.href}
              aria-current={active ? "page" : undefined}
              className={`flex min-h-11 items-center justify-between rounded-[10px] px-3.5 text-sm focus-visible:outline-2 focus-visible:outline-primary ${
                active ? "bg-primary-soft font-bold text-primary-strong" : "text-ink-sub hover:bg-primary-tint"
              }`}
            >
              {item.label}
              {count !== null && (
                <span className="rounded-[10px] bg-danger-bg px-2 py-px text-xs font-bold text-danger">
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
