// 주인: FE-A — JobDetailScreen
import { notFound } from "next/navigation";
import JobDetailScreen from "@/components/student/JobDetailScreen";
import { studentIdFrom } from "@/components/student/navigation";

export default async function Page({ params, searchParams }: PageProps<"/student/jobs/[itemId]">) {
  const { itemId } = await params;
  if (!/^\d+$/.test(itemId) || !Number.isSafeInteger(Number(itemId)) || Number(itemId) < 1) notFound();
  const studentId = studentIdFrom(await searchParams);
  return <JobDetailScreen key={`${itemId}-${studentId}`} itemId={Number(itemId)} studentId={studentId} />;
}
