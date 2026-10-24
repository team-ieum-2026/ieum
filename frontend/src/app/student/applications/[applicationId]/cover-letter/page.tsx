// 주인: FE-B — M6-1 자기소개서 편집 (FE-B-04)
export default async function CoverLetterEditPage({
  params,
}: PageProps<"/student/applications/[applicationId]/cover-letter">) {
  const { applicationId } = await params;
  return (
    <section className="p-4">
      <h1 className="text-xl font-bold">M6-1 자기소개서 편집 · applicationId {applicationId}</h1>
    </section>
  );
}
