// 주인: FE-A — R0 역할 선택 (FE-A-02)
import Link from "next/link";

export default function RoleSelectPage() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-6 p-6">
      <h1 className="text-2xl font-bold">R0 역할 선택</h1>
      <div className="flex flex-col gap-3">
        <Link
          href="/student"
          className="rounded-xl bg-primary px-6 py-3 text-center font-medium text-surface"
        >
          수료생으로 보기
        </Link>
        <Link
          href="/coordinator"
          className="rounded-xl border border-line bg-surface px-6 py-3 text-center font-medium text-ink"
        >
          코디네이터로 보기
        </Link>
      </div>
    </main>
  );
}
