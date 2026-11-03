"use client";

import Link from "next/link";
import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { usePathname, useSearchParams } from "next/navigation";
import { getProfile } from "@/lib/api/profile";
import type { StudentProfile } from "@/types/profile";
import BottomNavigation from "./BottomNavigation";
import StudentIcon from "./StudentIcon";
import { studentHref, studentIdFrom } from "./navigation";
import { useApi } from "./useApi";
import HelpRequestComposer from "./HelpRequestComposer";

const ProfileContext = createContext<{ profile: StudentProfile | null; loading: boolean; error: Error | null; reload: () => void; update: (profile: StudentProfile) => void } | null>(null);
export function useStudentProfile() { return useContext(ProfileContext); }

const tabs = [{ href: "/student", label: "맞춤 공고", icon: "sparkles" }, { href: "/student/applications", label: "지원 현황", icon: "folder" }, { href: "/student/profile", label: "내 프로필", icon: "user" }];

export default function StudentShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const query = useSearchParams();
  const studentId = studentIdFrom({ studentId: query.get("studentId") ?? undefined });
  const load = useCallback(() => getProfile(studentId), [studentId]);
  const { data: profile, loading, error, reload, setData } = useApi(load);
  const [helpOpen, setHelpOpen] = useState(false);
  const [headerScrolled, setHeaderScrolled] = useState(false);
  const previousPath = useRef(pathname);
  const current = pathname.startsWith("/student/applications") ? tabs[1] : pathname.startsWith("/student/profile") ? tabs[2] : tabs[0];
  useEffect(() => { window.addEventListener("focus", reload); return () => window.removeEventListener("focus", reload); }, [reload]);
  useEffect(() => {
    const updateHeader = () => setHeaderScrolled(window.scrollY > 0);
    updateHeader();
    window.addEventListener("scroll", updateHeader, { passive: true });
    return () => window.removeEventListener("scroll", updateHeader);
  }, []);
  useEffect(() => {
    if (previousPath.current !== pathname) {
      previousPath.current = pathname;
      reload();
    }
  }, [pathname, reload]);
  return <ProfileContext.Provider value={{ profile, loading, error, reload, update: setData }}>
    <div className="student-app">
      <a href="#student-main" className="s-skip-link">본문으로 건너뛰기</a>
      <div className="s-app-content">
        <header className="s-topbar" data-scrolled={headerScrolled}>
          <div className="s-topbar-left">
            <Link href={studentHref("/student", studentId)} className="s-mobile-brand" aria-label="이음 맞춤 공고"><span className="s-mobile-brand-content"><span className="s-brand-mark" aria-hidden="true"><StudentIcon name="sparkles" /></span><span className="s-brand-label">이음</span></span></Link>
            <nav className="s-header-nav" aria-label="수료생 주 메뉴">{tabs.map(tab => <Link key={tab.href} href={studentHref(tab.href, studentId)} aria-current={current.href === tab.href ? "page" : undefined}>{tab.label}</Link>)}</nav>
          </div>
          <div className="s-topbar-right"><Link href="/" className="s-text-link s-home-link" aria-label="역할 선택 홈으로 돌아가기"><span className="s-home-label">홈으로 돌아가기</span></Link></div>
        </header>
        <div className="s-contextbar"><Link href={studentHref("/student", studentId)}>나의 커리어</Link><StudentIcon name="chevron" /><span>{current.label}</span></div>
        <main className="s-main" id="student-main" tabIndex={-1}>{children}</main>
        <hr className="s-page-divider" />
      </div>
      <BottomNavigation />
      <button type="button" className="s-floating-help" aria-label="담당자에게 도움 요청" aria-haspopup="dialog" aria-expanded={helpOpen} title="다음 걸음, 함께 고민해요. 담당 코디네이터에게 물어보세요." onClick={() => setHelpOpen(true)}>
        <StudentIcon name="message" className="s-floating-help-icon" />
        <span className="s-floating-help-label">도움 요청</span>
      </button>
      {helpOpen && <HelpRequestComposer key={studentId} studentId={studentId} onClose={() => setHelpOpen(false)} />}
    </div>
  </ProfileContext.Provider>;
}
