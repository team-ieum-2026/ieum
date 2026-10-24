// 주인: FE-A — M4 도움 요청 완료 (FE-A-10)
export default async function HelpRequestDonePage({ params }: PageProps<"/student/help/[helpRequestId]">) {
  const { helpRequestId } = await params;
  return (
    <section className="p-4">
      <h1 className="text-xl font-bold">M4 도움 요청 완료 · helpRequestId {helpRequestId}</h1>
    </section>
  );
}
