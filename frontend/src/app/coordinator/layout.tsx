// 주인: FE-B — 코디네이터 공용 레이아웃: 데스크톱 고정 + 왼쪽 사이드바 (FE-B-09, ALL-01)
// FE-A 는 C2 · C3 화면만 작성하고 이 레이아웃은 고치지 않는다 (공용 파일).
import Link from "next/link";

const menu = [
  { href: "/coordinator", label: "대시보드" },
  { href: "/coordinator/students", label: "수료생 목록" },
  { href: "/coordinator/applications", label: "서류 검토" },
] as const;

export default function CoordinatorLayout({ children }: LayoutProps<"/coordinator">) {
  return (
    <div className="flex min-h-screen w-[1280px] bg-coord-bg">
      <aside className="w-60 shrink-0 border-r border-line bg-surface p-4">
        <p className="mb-6 text-lg font-bold">이음 코디네이터</p>
        <nav className="flex flex-col gap-1">
          {/* TODO(FE-B-09): 현재 메뉴 강조, 서류 검토 대기 건수 표시 */}
          {menu.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className="rounded-lg px-3 py-2 text-sm text-ink-sub hover:bg-primary-tint"
            >
              {item.label}
            </Link>
          ))}
        </nav>
      </aside>
      <main className="flex-1 p-8">{children}</main>
    </div>
  );
}
