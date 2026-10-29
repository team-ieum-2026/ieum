// 주인: FE-A — StudentsScreen
import StudentsScreen from "./StudentsScreen";
import { Suspense } from "react";

export default function Page() {
  return <Suspense fallback={<p role="status" className="p-6 text-sm text-ink-sub">불러오고 있어요…</p>}><StudentsScreen /></Suspense>;
}
