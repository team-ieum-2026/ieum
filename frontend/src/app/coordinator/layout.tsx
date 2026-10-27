// 주인: FE-B — 코디네이터 공용 레이아웃: 데스크톱 고정 + 왼쪽 사이드바 (FE-B-09, ALL-01)
// FE-A 는 C2 · C3 화면만 작성하고 이 레이아웃은 고치지 않는다 (공용 파일).
import CoordinatorSidebar from "@/components/coordinator/CoordinatorSidebar";

export default function CoordinatorLayout({ children }: LayoutProps<"/coordinator">) {
  return (
    <div className="flex min-h-screen w-[1280px] bg-coord-bg">
      <CoordinatorSidebar />
      <main className="flex-1 p-8">{children}</main>
    </div>
  );
}
