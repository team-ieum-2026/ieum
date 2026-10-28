// 주인: FE-B — 코디네이터 공용 레이아웃: 데스크톱 고정 + 왼쪽 사이드바 (FE-B-09, ALL-01)
// FE-A 는 C2 · C3 화면만 작성하고 이 레이아웃은 고치지 않는다 (공용 파일).
import CoordinatorSidebar from "@/components/coordinator/CoordinatorSidebar";

export default function CoordinatorLayout({ children }: LayoutProps<"/coordinator">) {
  return (
    // 데스크톱 창 폭을 그대로 쓴다 (오른쪽 빈 공간 없이). 고정 폭은 세로 스크롤바(약 15px)만큼 넘쳐서 w-full 로 맞춘다.
    // 반응형은 만들지 않으므로(AGENTS.md) 최소 폭 1200px 로 데스크톱 배치를 지킨다
    <div className="flex min-h-screen w-full min-w-[1200px] bg-coord-bg">
      <CoordinatorSidebar />
      {/* min-w-0: 넓은 표 · 긴 글이 사이드바 옆 영역을 넘어 늘어나지 않게 */}
      <main className="min-w-0 flex-1 p-8">{children}</main>
    </div>
  );
}
