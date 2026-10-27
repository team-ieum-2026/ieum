// 주인: FE-A — HelpRequestScreen
import { notFound } from "next/navigation";
import HelpRequestScreen from "@/components/student/HelpRequestScreen";
import { studentIdFrom } from "@/components/student/navigation";

export default async function Page({ params, searchParams }: PageProps<"/student/help/[helpRequestId]">) {
  const { helpRequestId } = await params;
  if (!/^\d+$/.test(helpRequestId) || !Number.isSafeInteger(Number(helpRequestId)) || Number(helpRequestId) < 1) notFound();
  const studentId = studentIdFrom(await searchParams);
  return <HelpRequestScreen key={`${helpRequestId}-${studentId}`} helpRequestId={Number(helpRequestId)} studentId={studentId} />;
}
