// 주인: FE-B — C4 서류 검토 목록 (11/1 개편). ?status= 로 첫 탭을 고른다 (대시보드 서류 검토 현황에서 이동)
import { Suspense } from "react";
import ReviewListScreen from "@/components/coordinator/ReviewListScreen";

export default function Page() {
  return (
    <Suspense fallback={<p role="status">불러오고 있어요…</p>}>
      <ReviewListScreen />
    </Suspense>
  );
}
