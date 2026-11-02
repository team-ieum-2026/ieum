// 주인: FE-B (11/1 역할 조정) — C2 수료생 관리
import StudentsScreen from "./StudentsScreen";
import { Suspense } from "react";

export default function Page() {
  return <Suspense fallback={<p role="status">불러오고 있어요…</p>}><StudentsScreen /></Suspense>;
}
