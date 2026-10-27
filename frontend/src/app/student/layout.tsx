// 주인: FE-A — 수료생 공용 레이아웃: 폭 390px 고정 가운데 정렬 + 하단 메뉴 3탭 (FE-A-13, ALL-01)
// M8 탭 포함. 하단 메뉴 수정은 FE-A 가 처리한다 (공용 파일).
"use client";

import { Suspense } from "react";
import { usePathname } from "next/navigation";
import BottomNavigation from "@/components/student/BottomNavigation";

const tabs = [
  { href: "/student", label: "맞춤 공고" },
  { href: "/student/applications", label: "지원 현황" },
  { href: "/student/profile", label: "내 프로필" },
] as const;

export default function StudentLayout({ children }: LayoutProps<"/student">) {
  const pathname = usePathname();
  const showBottomNav = tabs.some((tab) => tab.href === pathname);

  return (
    <div className="mx-auto flex min-h-screen w-[390px] flex-col bg-surface-warm">
      <main className={`flex flex-1 flex-col${showBottomNav ? " pb-16" : ""}`}>{children}</main>
      {showBottomNav && (
        <Suspense fallback={null}><BottomNavigation /></Suspense>
      )}
    </div>
  );
}
