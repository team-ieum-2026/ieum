// 주인: FE-B — M6-1 자기소개서 편집 (FE-B-04)
import CoverLetterEditor from "@/components/resume/CoverLetterEditor";
import { studentIdFrom } from "@/components/student/navigation";

export default async function CoverLetterEditPage({
  params,
  searchParams,
}: PageProps<"/student/applications/[applicationId]/cover-letter">) {
  const { applicationId } = await params;
  // ?studentId= 를 받아 이력서로 돌아갈 때 그대로 넘긴다
  const studentId = studentIdFrom(await searchParams);
  return <CoverLetterEditor applicationId={Number(applicationId)} studentId={studentId} />;
}
