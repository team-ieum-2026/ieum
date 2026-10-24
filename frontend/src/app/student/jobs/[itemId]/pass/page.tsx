// 주인: FE-A — M3 패스 사유 (FE-A-09)
export default async function PassReasonPage({ params }: PageProps<"/student/jobs/[itemId]/pass">) {
  const { itemId } = await params;
  return (
    <section className="p-4">
      <h1 className="text-xl font-bold">M3 패스 사유 · itemId {itemId}</h1>
    </section>
  );
}
