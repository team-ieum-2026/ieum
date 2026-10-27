// 주인: FE-A — StudentDetailScreen
import { notFound } from "next/navigation";
import StudentDetailScreen from "./StudentDetailScreen";

export default async function Page({ params }: PageProps<"/coordinator/students/[studentId]">) {
  const { studentId } = await params;
  if (!/^\d+$/.test(studentId) || !Number.isSafeInteger(Number(studentId)) || Number(studentId) < 1) notFound();
  return <StudentDetailScreen key={studentId} studentId={Number(studentId)} />;
}
