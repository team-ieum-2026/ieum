// 주인: FE-A — M5 서류 생성 중 (FE-A-11)
export default async function ApplicationProgressPage({
  params,
}: PageProps<"/student/applications/[applicationId]/progress">) {
  const { applicationId } = await params;
  return (
    <section className="p-4">
      <h1 className="text-xl font-bold">M5 서류 생성 중 · applicationId {applicationId}</h1>
    </section>
  );
}
