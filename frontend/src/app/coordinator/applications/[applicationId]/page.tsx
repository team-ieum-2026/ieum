// 주인: FE-B — C4 서류 검토 · 승인 (FE-B-08)
export default async function ApplicationReviewPage({
  params,
}: PageProps<"/coordinator/applications/[applicationId]">) {
  const { applicationId } = await params;
  return (
    <section>
      <h1 className="text-2xl font-bold">C4 서류 검토 · 승인 · applicationId {applicationId}</h1>
    </section>
  );
}
