// 주인: FE-B — M6-1 자기소개서 편집 (FE-B-04)
import CoverLetterEditor from "@/components/resume/CoverLetterEditor";

export default async function CoverLetterEditPage({
  params,
}: PageProps<"/student/applications/[applicationId]/cover-letter">) {
  const { applicationId } = await params;
  return <CoverLetterEditor applicationId={Number(applicationId)} />;
}
