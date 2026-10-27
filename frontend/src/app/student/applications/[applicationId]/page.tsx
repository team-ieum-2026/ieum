// 주인: FE-B — M6 이력서 초안 (FE-B-02, FE-B-03). M6-2 섹션 편집 시트는 라우트가 아니라 바텀시트 컴포넌트 (FE-B-05)
import ResumeDraft from "@/components/resume/ResumeDraft";

export default async function ResumeDraftPage({ params }: PageProps<"/student/applications/[applicationId]">) {
  const { applicationId } = await params;
  return <ResumeDraft applicationId={Number(applicationId)} />;
}
