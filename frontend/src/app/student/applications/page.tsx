// 주인: FE-A — ApplicationsScreen
import ApplicationsScreen from "@/components/student/ApplicationsScreen";
import { studentIdFrom } from "@/components/student/navigation";

export default async function Page({ searchParams }: PageProps<"/student/applications">) {
  const studentId = studentIdFrom(await searchParams);
  return <ApplicationsScreen key={studentId} studentId={studentId} />;
}
