// 주인: FE-B — M6 이력서 초안 (FE-B-02, FE-B-03). M6-2 섹션 편집 시트는 라우트가 아니라 바텀시트 컴포넌트 (FE-B-05)
export default async function ResumeDraftPage({ params }: PageProps<"/student/applications/[applicationId]">) {
  const { applicationId } = await params;
  return (
    <section className="p-4">
      <h1 className="text-xl font-bold">M6 이력서 초안 · applicationId {applicationId}</h1>
    </section>
  );
}
