"use client";
// 주인: FE-B — 코디네이터 상단 막대 (11/1 개편 목업). 현재 화면 이름만 보여 준다.
// 목업의 알림 벨 · 계정 메뉴는 이번 범위에서 제외 (로그인 · 실제 알림 없음).
import { usePathname } from "next/navigation";

function pageName(path: string) {
  if (path === "/coordinator") return "오늘의 업무";
  if (/^\/coordinator\/students\/[^/]+/.test(path)) return "수료생 상세";
  if (path.startsWith("/coordinator/students")) return "수료생 관리";
  if (/^\/coordinator\/applications\/[^/]+/.test(path)) return "지원서류 검토";
  if (path.startsWith("/coordinator/applications")) return "서류 검토";
  if (path.startsWith("/coordinator/help-requests")) return "도움 요청";
  if (path.startsWith("/coordinator/stats")) return "취업·지원 현황";
  return "";
}

export default function CoordinatorTopbar() {
  const name = pageName(usePathname());
  return (
    <header className="sticky top-0 z-10 flex h-[60px] items-center border-b border-(--coord-line) bg-(--coord-surface) px-[34px]">
      <p className="flex items-center gap-2.5 text-xs text-(--coord-muted)">
        워크스페이스 <span aria-hidden="true">/</span>
        <b className="font-medium text-(--coord-ink)">{name}</b>
      </p>
    </header>
  );
}
