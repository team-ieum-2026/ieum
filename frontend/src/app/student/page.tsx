// 주인: FE-A — RecommendationsScreen
import RecommendationsScreen from "@/components/student/RecommendationsScreen";
import { studentIdFrom } from "@/components/student/navigation";

export default async function Page({ searchParams }: PageProps<"/student">) {
  const studentId = studentIdFrom(await searchParams);
  return <RecommendationsScreen key={studentId} studentId={studentId} />;
}
