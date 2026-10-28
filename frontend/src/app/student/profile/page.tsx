// 주인: FE-B — M8 내 프로필 (FE-B-06)
import ProfileScreen from "@/app/student/profile/ProfileScreen";
import { studentIdFrom } from "@/components/student/navigation";

export default async function ProfilePage({ searchParams }: PageProps<"/student/profile">) {
  const studentId = studentIdFrom(await searchParams);
  return <ProfileScreen key={studentId} studentId={studentId} />;
}
