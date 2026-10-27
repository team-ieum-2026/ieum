// 주인: FE-A — R0 역할 선택 (FE-A-02)
import Link from "next/link";
import { studentIdFrom } from "@/components/student/navigation";
import StudentEntry from "@/components/student/StudentEntry";

export default async function RoleSelectPage({ searchParams }: PageProps<"/">) {
  const studentId = studentIdFrom(await searchParams);

  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-[390px] flex-1 flex-col justify-between gap-12 bg-surface-warm px-6 pt-[72px] pb-10 text-ink">
      <div className="flex flex-col gap-3.5">
        <div className="flex size-14 items-center justify-center rounded-2xl bg-primary-soft text-primary">
          <svg
            width="30"
            height="30"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M12 21V11" />
            <path d="M12 11C12 7 9 5 5 5c0 4 3 6 7 6z" />
            <path d="M12 13c0-3 2.5-5 6-5 0 3.5-2.5 5-6 5z" />
          </svg>
        </div>
        <h1 className="text-[34px] font-bold tracking-[-0.02em] text-primary">이음</h1>
        <p className="text-lg leading-[1.5] font-medium">
          보고하지 않아도,
          <br />
          기회가 먼저 찾아옵니다
        </p>
        <p className="text-sm leading-[1.6] text-ink-sub">청년취업사관학교 수료생 사후관리 서비스</p>
      </div>

      <section aria-labelledby="role-select-heading" className="flex flex-col gap-3">
        <h2 id="role-select-heading" className="mb-1 text-[13px] font-bold text-ink-sub">
          어떤 화면으로 볼까요?
        </h2>
        <StudentEntry initialStudentId={studentId} />
        <Link
          href="/coordinator"
          className="flex flex-col gap-0.5 rounded-[14px] border-[1.5px] border-line bg-surface px-5 py-[18px] hover:border-primary focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-primary"
        >
          <span className="text-[17px] font-bold">코디네이터로 보기</span>
          <span className="text-[13px] text-ink-sub">데스크톱 · 상태 대시보드와 서류 검토</span>
        </Link>
        <div aria-hidden="true" className="mt-2.5 h-4 shrink-0" />
      </section>
    </main>
  );
}
