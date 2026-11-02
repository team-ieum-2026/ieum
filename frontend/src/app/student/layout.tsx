// FE-A — 수료생 웹 · 모바일 공통 레이아웃. API와 화면별 경로는 유지합니다.
import { Suspense } from "react";
import StudentShell from "@/components/student/StudentShell";
import "@/styles/student.css";

export default function StudentLayout({ children }: LayoutProps<"/student">) {
  return <Suspense fallback={<div className="student-app s-layout-loading" role="status">나의 커리어 공간을 불러오고 있어요…</div>}><StudentShell>{children}</StudentShell></Suspense>;
}
