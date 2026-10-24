// 주인: FE-A — C3 수료생 상세 (FE-A-15)
export default async function StudentDetailPage({ params }: PageProps<"/coordinator/students/[studentId]">) {
  const { studentId } = await params;
  return (
    <section>
      <h1 className="text-2xl font-bold">C3 수료생 상세 · studentId {studentId}</h1>
    </section>
  );
}
