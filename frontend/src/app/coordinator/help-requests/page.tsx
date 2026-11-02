// 주인: FE-B — 도움 요청 목록 · 상세 (11/1 개편 신규 화면)
import { Suspense } from "react";
import HelpRequestsScreen from "@/components/coordinator/HelpRequestsScreen";

export default function Page() {
  return (
    <Suspense fallback={<p role="status">불러오고 있어요…</p>}>
      <HelpRequestsScreen />
    </Suspense>
  );
}
