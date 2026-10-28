// 주인: FE-B — M6 이력서 초안 (FE-B-02, FE-B-03). M6-2 섹션 편집 시트는 라우트가 아니라 바텀시트 컴포넌트 (FE-B-05)
import ResumeDraft from "@/components/resume/ResumeDraft";
import { studentIdFrom } from "@/components/student/navigation";

export default async function ResumeDraftPage({
  params,
  searchParams,
}: PageProps<"/student/applications/[applicationId]">) {
  const { applicationId } = await params;
  // ?studentId= 를 받아 M6 안의 이동(자기소개서 · 진행 화면 · 지원 현황)에 그대로 넘긴다
  const studentId = studentIdFrom(await searchParams);
  return <ResumeDraft applicationId={Number(applicationId)} studentId={studentId} />;
}
