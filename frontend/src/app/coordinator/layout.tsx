// 주인: FE-B — 코디네이터 공용 레이아웃: 데스크톱 고정 + 왼쪽 사이드바 (FE-B-09, ALL-01)
// FE-A 는 C2 · C3 화면만 작성하고 이 레이아웃은 고치지 않는다 (공용 파일).
import CoordinatorSidebar from "@/components/coordinator/CoordinatorSidebar";

export default function CoordinatorLayout({ children }: LayoutProps<"/coordinator">) {
  return (
    // 1280px 화면 기준. 고정 w-[1280px] 는 세로 스크롤바(약 15px)만큼 가로로 넘쳐서 창 폭에 맞추되 1280 을 넘지 않게.
    // 반응형은 만들지 않으므로(AGENTS.md) 최소 폭으로 데스크톱 배치를 지킨다
    <div className="flex min-h-screen w-full min-w-[1200px] max-w-[1280px] bg-coord-bg">
      <CoordinatorSidebar />
      <main className="flex-1 p-8">{children}</main>
    </div>
  );
}
