// 주인: FE-A — ProgressScreen
import { notFound } from "next/navigation";
import ProgressScreen from "@/components/student/ProgressScreen";
import { studentIdFrom } from "@/components/student/navigation";

export default async function Page({ params, searchParams }: PageProps<"/student/applications/[applicationId]/progress">) {
  const { applicationId } = await params;
  if (!/^\d+$/.test(applicationId) || !Number.isSafeInteger(Number(applicationId)) || Number(applicationId) < 1) notFound();
  const studentId = studentIdFrom(await searchParams);
  return <ProgressScreen key={`${applicationId}-${studentId}`} applicationId={Number(applicationId)} studentId={studentId} />;
}
