"use client";

import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { studentHref, studentIdFrom } from "./navigation";

const tabs = [
  { href: "/student", label: "맞춤 공고", path: "M3 10.5 12 3l9 7.5V21H3z" },
  { href: "/student/applications", label: "지원 현황", path: "M5 3h14v18H5zM9 8h6M9 12h6M9 16h3" },
  { href: "/student/profile", label: "내 프로필", path: "M16 8a4 4 0 1 1-8 0 4 4 0 0 1 8 0M4 21c0-4 4-6 8-6s8 2 8 6" },
];

export default function BottomNavigation() {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const studentId = studentIdFrom({ studentId: searchParams.get("studentId") ?? undefined });
  const active = pathname.startsWith("/student/applications") ? "/student/applications" : pathname.startsWith("/student/profile") ? "/student/profile" : "/student";
  return <nav aria-label="하단 메뉴" className="s-mobile-nav">{tabs.map((tab) => <Link key={tab.href} href={studentHref(tab.href, studentId)} aria-current={active === tab.href ? "page" : undefined} className={active === tab.href ? "is-active" : ""}><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" strokeLinecap="round" aria-hidden="true"><path d={tab.path} /></svg>{tab.label}</Link>)}</nav>;
}
