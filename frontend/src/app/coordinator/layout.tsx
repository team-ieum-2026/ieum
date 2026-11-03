// 주인: FE-B — 코디네이터 공용 레이아웃: 데스크톱 고정 + 왼쪽 사이드바 + 상단 막대 (FE-B-09, ALL-01, 11/1 개편 목업)
// 코디네이터 전용 색 토큰(coordinator.css)은 .coord-root 안에서만 적용된다.
import CoordinatorSidebar from "@/components/coordinator/CoordinatorSidebar";
import CoordinatorTopbar from "@/components/coordinator/CoordinatorTopbar";
import { IS_MOCK, MOCK_SAVE_NOTE } from "@/components/coordinator/demoAccount";
import "@/components/coordinator/coordinator.css";

export default function CoordinatorLayout({ children }: LayoutProps<"/coordinator">) {
  return (
    // 반응형은 만들지 않으므로(AGENTS.md) 최소 폭 1200px 로 데스크톱 배치를 지킨다
    <div className="coord-root min-h-screen w-full min-w-[1200px]">
      <CoordinatorSidebar />
      {/* min-w-0: 넓은 표 · 긴 글이 사이드바 옆 영역을 넘어 늘어나지 않게 */}
      <div className="ml-[220px] min-w-0">
        <CoordinatorTopbar />
        {/* 목업 모드(시연)에서만: 통계는 전체 인원 요약값, 목록 · 상세는 일부 샘플뿐이고 저장되지 않음을 알린다. 실제 BE 모드에서는 그리지 않음 */}
        {IS_MOCK && (
          <p role="note" className="border-b border-(--coord-attention-line) bg-(--coord-attention-soft) px-6 py-2 text-[13px] text-(--coord-attention)">
            <b className="mr-1.5 font-bold">시연용 목업 데이터</b>통계 · 건수는 전체 인원 기준 요약값이고, 목록 · 상세에는 일부 샘플 수료생만 들어 있어요. {MOCK_SAVE_NOTE}
          </p>
        )}
        {/* 리뉴얼 .page-content-wrapper: 화면 폭을 그대로 쓴다 (24px 28px 40px) */}
        <main className="px-7 pt-6 pb-10">{children}</main>
      </div>
    </div>
  );
}
