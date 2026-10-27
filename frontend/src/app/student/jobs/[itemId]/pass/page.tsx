// 주인: FE-A — PassReasonScreen
import { notFound } from "next/navigation";
import PassReasonScreen from "@/components/student/PassReasonScreen";
import { studentIdFrom } from "@/components/student/navigation";

export default async function Page({ params, searchParams }: PageProps<"/student/jobs/[itemId]/pass">) {
  const { itemId } = await params;
  if (!/^\d+$/.test(itemId) || !Number.isSafeInteger(Number(itemId)) || Number(itemId) < 1) notFound();
  const studentId = studentIdFrom(await searchParams);
  return <PassReasonScreen key={`${itemId}-${studentId}`} itemId={Number(itemId)} studentId={studentId} />;
}
