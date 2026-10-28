// 주인: FE-A — R0 역할 선택 (FE-A-02)
import Link from "next/link";
import { Gowun_Dodum } from "next/font/google";
import StudentEntry from "@/components/student/StudentEntry";

const entryHeadingFont = Gowun_Dodum({
  variable: "--font-gowun-dodum",
  weight: "400",
  subsets: ["latin"],
  display: "swap",
});

export default function RoleSelectPage() {
  return (
    <main className="relative isolate mx-auto flex min-h-dvh w-full max-w-[390px] flex-1 -translate-y-[120px] flex-col justify-between gap-8 bg-entry-surface px-6 pt-12 pb-10 text-ink after:pointer-events-none after:absolute after:inset-x-0 after:top-full after:h-[120px] after:bg-entry-surface">
      <svg
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 -z-10 size-full -translate-y-[55px] overflow-hidden saturate-[0.78]"
        viewBox="0 0 941 1672"
        preserveAspectRatio="xMidYMid slice"
      >
        {/* 이전 배경의 좌표계에서 입체 글씨의 중심을 맞추고, 새 그림의 비율을 유지한다. */}
        <image
          href="/images/r0-illustration.png"
          x="-26"
          y="571"
          width="1044.48"
          height="696.32"
        />
      </svg>
      <div className="flex flex-col gap-3.5">
        <div aria-hidden="true" className="size-14 shrink-0" />
        <h1 className={`${entryHeadingFont.variable} font-entry-heading -translate-x-[96px] translate-y-[140px] text-center text-[35px] font-bold tracking-[-0.02em] text-entry-heading`}>
          <span className="rounded-xl bg-entry-surface/75 px-3 py-1.5">오늘과 내일을</span>
        </h1>
        <p aria-hidden="true" className="invisible text-lg leading-[1.5] font-medium">
          보고하지 않아도,
          <br />
          기회가 먼저 찾아옵니다
        </p>
        <p aria-hidden="true" className="invisible text-sm leading-[1.6] text-ink-sub">청년취업사관학교 수료생 사후관리 서비스</p>
      </div>

      <section
        aria-labelledby="role-select-heading"
        className="relative isolate flex translate-y-[65px] flex-col gap-3"
      >
        {/* 문구·버튼을 이동해도 배경 경계와 하단 배경의 끝은 기존 위치에 유지한다. */}
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -inset-x-6 -top-[52px] bottom-[25px] -z-10 bg-entry-surface"
        >
          <div className="absolute inset-x-0 bottom-full h-8 bg-linear-to-b from-entry-surface/0 to-entry-surface" />
        </div>
        <h2 id="role-select-heading" className="mb-1 translate-y-[3px] text-[18px] font-bold text-ink">
          어떤 역할로 시작하시나요?
        </h2>
        <div className="flex flex-col gap-[18px]">
          <StudentEntry />
          <Link
            href="/coordinator"
            className="flex min-h-[83px] items-center gap-3 rounded-2xl border border-primary/15 bg-surface px-3 py-3 hover:border-primary focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-primary"
          >
            <span className="flex size-[34px] shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <circle cx="9" cy="8" r="3" />
                <path d="M3 19v-2a5 5 0 0 1 10 0v2M16 5a3 3 0 0 1 0 6M17 14a5 5 0 0 1 4 5" />
              </svg>
            </span>
            <span className="min-w-0 flex-1">
              <span className="block text-[17px] leading-6 font-medium text-ink">코디네이터</span>
              <span className="block text-[13px] leading-5 text-ink-sub">수료생 현황과 지원 업무 관리</span>
            </span>
            <svg className="shrink-0 text-primary/60" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="m9 5 7 7-7 7" />
            </svg>
          </Link>
        </div>
        <div aria-hidden="true" className="mt-2.5 h-4 shrink-0" />
      </section>
    </main>
  );
}
