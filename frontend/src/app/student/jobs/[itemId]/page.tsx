// 주인: FE-A — M2 공고 상세 · 응답 (FE-A-06 ~ 08)
export default async function JobDetailPage({ params }: PageProps<"/student/jobs/[itemId]">) {
  const { itemId } = await params;
  return (
    <section className="p-4">
      <h1 className="text-xl font-bold">M2 공고 상세 · 응답 · itemId {itemId}</h1>
    </section>
  );
}
