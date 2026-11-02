// 주인: FE-B (11/1 역할 조정) — C3 수료생 상세
import { notFound } from "next/navigation";
import { Suspense } from "react";
import StudentDetailScreen from "./StudentDetailScreen";

export default async function Page({ params }: PageProps<"/coordinator/students/[studentId]">) {
  const { studentId } = await params;
  if (!/^\d+$/.test(studentId) || !Number.isSafeInteger(Number(studentId)) || Number(studentId) < 1) notFound();
  return (
    <Suspense fallback={<p role="status">불러오고 있어요…</p>}>
      <StudentDetailScreen key={studentId} studentId={Number(studentId)} />
    </Suspense>
  );
}
