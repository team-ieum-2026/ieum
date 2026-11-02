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
  const [sidebarCollapsed, setSidebarCollapsed] = useState(true);
  const sidebarDialog = useRef<HTMLDialogElement>(null);
  const previousPath = useRef(pathname);
  const current = pathname.startsWith("/student/applications") ? tabs[1] : pathname.startsWith("/student/profile") ? tabs[2] : tabs[0];
  const name = profile?.basic.name;
  useEffect(() => {
    const dialog = sidebarDialog.current;
    if (!dialog) return;
    if (!sidebarCollapsed && !dialog.open) dialog.showModal();
    if (sidebarCollapsed && dialog.open) dialog.close();
  }, [sidebarCollapsed]);
  useEffect(() => { window.addEventListener("focus", reload); return () => window.removeEventListener("focus", reload); }, [reload]);
  useEffect(() => {
    if (previousPath.current !== pathname) {
      previousPath.current = pathname;
      setSidebarCollapsed(true);
      reload();
    }
  }, [pathname, reload]);
  return <ProfileContext.Provider value={{ profile, loading, error, reload, update: setData }}>
    <div className="student-app" data-sidebar-collapsed={sidebarCollapsed}>
      <a href="#student-main" className="s-skip-link">본문으로 건너뛰기</a>
      <dialog ref={sidebarDialog} className="s-utility-dialog" aria-label="나의 커리어 메뉴" onClose={() => setSidebarCollapsed(true)} onCancel={() => setSidebarCollapsed(true)} onClick={event => { if (event.target === event.currentTarget) { const bounds = event.currentTarget.getBoundingClientRect(); if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) setSidebarCollapsed(true); } }}>
      <aside id="student-sidebar" className="s-sidebar" aria-label="수료생 보조 메뉴">
        <button type="button" className="s-sidebar-close" aria-label="사이드바 닫기" onClick={() => setSidebarCollapsed(true)}><StudentIcon name="close" /></button>
        <Link href={studentHref("/student", studentId)} className="s-brand" aria-label="이음 맞춤 공고"><span className="s-brand-mark" aria-hidden="true"><StudentIcon name="sparkles" /></span><span className="s-brand-label">이음</span></Link>
        <p className="s-brand-caption">커리어의 다음을 잇다</p><p className="s-nav-caption">나의 커리어 바로가기</p>
        <nav className="s-desktop-nav">{tabs.map(tab => <Link key={tab.href} href={studentHref(tab.href, studentId)} className={current.href === tab.href ? "is-active" : ""} aria-current={current.href === tab.href ? "page" : undefined}><StudentIcon name={tab.icon} /><span>{tab.label}</span></Link>)}</nav>
        <div className="s-sidebar-bottom"><section className="s-support"><span className="s-support-icon"><StudentIcon name="message" /></span><h2>다음 걸음, 함께 고민해요.</h2><p>공고나 지원 준비가 막막할 때<br />담당 코디네이터에게 물어보세요.</p><button type="button" onClick={() => { setSidebarCollapsed(true); setHelpOpen(true); }} className="s-text-link">담당자에게 도움 요청 <StudentIcon name="arrow" /></button></section><Link className="s-sidebar-profile" href={studentHref("/student/profile", studentId)}><span className="s-avatar">{name?.slice(1, 2) || "나"}</span><span><strong>{name || "나의 프로필"}</strong><small>{profile ? `${profile.course.campus} · ${profile.course.cohort}` : "커리어의 다음을 준비해요"}</small></span><StudentIcon name="chevron" /></Link><p className="s-sidebar-footer">SeSAC × 이음 <span>수료 후에도, 함께.</span></p></div>
        <Link className="s-text-link s-sidebar-home" href="/">← 홈으로 돌아가기</Link>
      </aside>
      </dialog>
      <div className="s-app-content">
        <header className="s-topbar">
          <div className="s-topbar-left">
            <button type="button" className="s-sidebar-toggle" aria-controls="student-sidebar" aria-expanded={!sidebarCollapsed} aria-label={sidebarCollapsed ? "사이드바 펼치기" : "사이드바 접기"} title={sidebarCollapsed ? "사이드바 펼치기" : "사이드바 접기"} onClick={() => setSidebarCollapsed(value => !value)}>
              <StudentIcon name="more" />
            </button>
            <Link href={studentHref("/student", studentId)} className="s-mobile-brand" aria-label="이음 맞춤 공고"><span className="s-mobile-brand-content"><span className="s-brand-mark" aria-hidden="true"><StudentIcon name="sparkles" /></span><span className="s-brand-label">이음</span></span></Link>
            <nav className="s-header-nav" aria-label="수료생 주 메뉴">{tabs.map(tab => <Link key={tab.href} href={studentHref(tab.href, studentId)} aria-current={current.href === tab.href ? "page" : undefined}>{tab.label}</Link>)}</nav>
          </div>
          <div className="s-topbar-right"><button type="button" className="s-header-help" aria-label="담당자에게 도움 요청" onClick={() => setHelpOpen(true)}><StudentIcon name="message" /><span>도움 요청</span></button><Link href="/" className="s-text-link s-home-link" aria-label="역할 선택 홈으로 돌아가기"><span className="s-home-label">홈으로 돌아가기</span></Link></div>
        </header>
        <div className="s-contextbar"><Link href={studentHref("/student", studentId)}>나의 커리어</Link><StudentIcon name="chevron" /><span>{current.label}</span></div>
        <main className="s-main" id="student-main" tabIndex={-1}>{children}</main>
        <hr className="s-page-divider" />
      </div>
      <BottomNavigation />
      {helpOpen && <HelpRequestComposer key={studentId} studentId={studentId} onClose={() => setHelpOpen(false)} />}
    </div>
  </ProfileContext.Provider>;
}
