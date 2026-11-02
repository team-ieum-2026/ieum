"use client";

import Link from "next/link";
import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { getProfile } from "@/lib/api/profile";
import { createHelpRequest, getCurrentRecommendations } from "@/lib/api/student";
import type { StudentProfile } from "@/types/profile";
import BottomSheet from "@/components/ui/BottomSheet";
import Button from "@/components/ui/Button";
import BottomNavigation from "./BottomNavigation";
import StudentIcon from "./StudentIcon";
import { ApiState, ActionError } from "./ScreenParts";
import { studentHref, studentIdFrom } from "./navigation";
import { rememberResponse, restoreResponse } from "./mockRecommendationResponses";
import { useApi, errorMessage } from "./useApi";

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
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const previousPath = useRef(pathname);
  const current = pathname.startsWith("/student/applications") ? tabs[1] : pathname.startsWith("/student/profile") ? tabs[2] : tabs[0];
  const name = profile?.basic.name;
  useEffect(() => { window.addEventListener("focus", reload); return () => window.removeEventListener("focus", reload); }, [reload]);
  useEffect(() => {
    if (previousPath.current !== pathname) {
      previousPath.current = pathname;
      reload();
    }
  }, [pathname, reload]);
  return <ProfileContext.Provider value={{ profile, loading, error, reload, update: setData }}>
    <div className="student-app" data-sidebar-collapsed={sidebarCollapsed}>
      <a href="#student-main" className="s-skip-link">본문으로 건너뛰기</a>
      <aside id="student-sidebar" className="s-sidebar" aria-label="수료생 주 메뉴" hidden={sidebarCollapsed}>
        <Link href={studentHref("/student", studentId)} className="s-brand" aria-label="이음 맞춤 공고"><span className="s-brand-mark" aria-hidden="true"><StudentIcon name="sprout" /></span><span className="s-brand-label">이음</span></Link>
        <p className="s-brand-caption">커리어의 다음을 잇다</p><p className="s-nav-caption">MY CAREER SPACE</p>
        <nav className="s-desktop-nav">{tabs.map(tab => <Link key={tab.href} href={studentHref(tab.href, studentId)} className={current.href === tab.href ? "is-active" : ""} aria-current={current.href === tab.href ? "page" : undefined}><StudentIcon name={tab.icon} /><span>{tab.label}</span></Link>)}</nav>
        <div className="s-sidebar-bottom"><section className="s-support"><span className="s-support-icon"><StudentIcon name="message" /></span><h2>다음 걸음, 함께 고민해요.</h2><p>공고나 지원 준비가 막막할 때<br />담당 코디네이터에게 물어보세요.</p><button type="button" onClick={() => setHelpOpen(true)} className="s-text-link">공고 선택하고 도움 요청 <StudentIcon name="arrow" /></button></section><Link className="s-sidebar-profile" href={studentHref("/student/profile", studentId)}><span className="s-avatar">{name?.slice(1, 2) || "나"}</span><span><strong>{name || "나의 프로필"}</strong><small>{profile ? `${profile.course.campus} · ${profile.course.cohort}` : "커리어의 다음을 준비해요"}</small></span><StudentIcon name="chevron" /></Link><p className="s-sidebar-footer">SeSAC × 이음 <span>수료 후에도, 함께.</span></p></div>
      </aside>
      <div className="s-app-content">
        <header className="s-topbar">
          <div className="s-topbar-left">
            <button type="button" className="s-sidebar-toggle" aria-controls="student-sidebar" aria-expanded={!sidebarCollapsed} aria-label={sidebarCollapsed ? "사이드바 펼치기" : "사이드바 접기"} title={sidebarCollapsed ? "사이드바 펼치기" : "사이드바 접기"} onClick={() => setSidebarCollapsed(value => !value)}>
              <StudentIcon name="more" />
            </button>
            <Link href={studentHref("/student", studentId)} className="s-mobile-brand" aria-label="이음 맞춤 공고"><span className="s-mobile-brand-content"><span className="s-brand-mark" aria-hidden="true"><StudentIcon name="sprout" /></span><span className="s-brand-label">이음</span></span></Link>
            <div className="s-breadcrumb"><Link href={studentHref("/student", studentId)}>나의 커리어</Link><StudentIcon name="chevron" /><span>{current.label}</span></div>
          </div>
          <div className="s-topbar-right"><Link href="/" className="s-text-link"><span aria-hidden="true">←</span> 홈으로 돌아가기</Link><span className="s-membership"><span className="s-status-dot" />청년취업사관학교 수료생</span><Link className="s-avatar s-avatar-small" href={studentHref("/student/profile", studentId)} aria-label="내 프로필">{name?.slice(1, 2) || "나"}</Link></div>
        </header>
        <main className="s-main" id="student-main" tabIndex={-1}>{children}</main>
        <footer className="s-page-footer"><span>당신의 가능성과 다음 기회를 연결합니다.</span><span>© 이음 · 수료 후에도, 함께.</span></footer>
      </div>
      <BottomNavigation />
      {helpOpen && <StudentHelpPicker key={studentId} studentId={studentId} onClose={() => setHelpOpen(false)} />}
    </div>
  </ProfileContext.Provider>;
}

function StudentHelpPicker({ studentId, onClose }: { studentId: number; onClose: () => void }) {
  const router = useRouter();
  const load = useCallback(() => getCurrentRecommendations(studentId), [studentId]);
  const { data, loading, error, reload } = useApi(load);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const busy = useRef(false);
  const choices = data?.items.filter(item => restoreResponse(studentId, item.itemId, item.response) === "NONE") ?? [];
  async function choose(itemId: number) {
    if (busy.current) return;
    busy.current = true; setBusyId(itemId); setActionError(null);
    try { const request = await createHelpRequest({ studentId, recommendationItemId: itemId }); rememberResponse(studentId, itemId, "HELP"); onClose(); router.push(studentHref(`/student/help/${request.helpRequestId}`, studentId)); }
    catch (error) { setActionError(errorMessage(error)); busy.current = false; setBusyId(null); }
  }
  return <BottomSheet open onClose={() => { if (!busy.current) onClose(); }} title="어떤 공고를 함께 살펴볼까요?">
    <p className="s-dialog-description">담당자가 함께 확인할 공고를 선택해주세요.</p><ApiState loading={loading} error={error} retry={reload} />
    {data && <div className="s-help-choices">{choices.map(item => <Button variant="secondary" key={item.itemId} disabled={busyId !== null} className="s-help-choice" onClick={() => choose(item.itemId)}><span><strong>{item.job.companyName}</strong><span>{item.job.title}</span></span><StudentIcon name={busyId === item.itemId ? "refresh" : "chevron"} /></Button>)}{!choices.length && <p className="s-dialog-description">이번 추천 공고에는 모두 응답했어요. 맞춤 공고에서 새 추천을 확인해주세요.</p>}</div>}
    <ActionError message={actionError} />
  </BottomSheet>;
}
