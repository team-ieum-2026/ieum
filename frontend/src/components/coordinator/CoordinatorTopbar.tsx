"use client";
// 주인: FE-B — 코디네이터 상단 막대 (11/1 개편 목업 topbar). 현재 화면 이름 + 알림 벨 + 계정 메뉴.
// - 알림: 실제 데이터만 (대시보드 API 의 새 도움 요청 · 검토 대기 서류). 다른 알림 종류는 없다
// - 계정: 로그인이 없어서 demoAccount.ts 의 데모 계정을 보여 준다. 계정 설정은 준비 중(누를 수 없음)
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { onReviewQueueChanged } from "@/components/coordinator/CoordinatorSidebar";
import { Icon } from "@/components/coordinator/CoordUi";
import { DEMO_COORDINATOR } from "@/components/coordinator/demoAccount";
import { helpTitle, helpTypeLabel, monthDayTime } from "@/components/coordinator/coordLabels";
import { getDashboard } from "@/lib/api/coordinatorReview";

function pageName(path: string) {
  if (path === "/coordinator") return "오늘의 업무";
  if (/^\/coordinator\/students\/[^/]+/.test(path)) return "수료생 상세";
  if (path.startsWith("/coordinator/students")) return "수료생 관리";
  if (/^\/coordinator\/applications\/[^/]+/.test(path)) return "지원서류 검토";
  if (path.startsWith("/coordinator/applications")) return "서류 검토";
  if (path.startsWith("/coordinator/help-requests")) return "도움 요청";
  if (path.startsWith("/coordinator/stats")) return "취업·지원 현황";
  return "";
}

type Notice = { key: string; at: string; title: string; text: string; href: string };

export default function CoordinatorTopbar() {
  const pathname = usePathname();
  const [notices, setNotices] = useState<Notice[] | null>(null);
  const [open, setOpen] = useState<"bell" | "account" | null>(null);
  const boxRef = useRef<HTMLDivElement>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  // 도움 요청 · 서류 처리에 성공하면(notifyReviewQueueChanged) 같은 화면에 있어도 다시 조회한다. 실패하면 알림이 오지 않아 숫자도 그대로
  useEffect(() => onReviewQueueChanged(() => setRefreshKey((k) => k + 1)), []);

  // 화면을 옮길 때마다 · 처리 알림마다 알림 다시 조회 (실패하면 배지 숨김, 임의 숫자 없음)
  useEffect(() => {
    let ignore = false;
    getDashboard()
      .then((d) => {
        if (ignore) return;
        const help: Notice[] = d.helpRequests.map((h) => ({
          key: `help-${h.helpRequestId}`,
          at: h.createdAt,
          title: `${h.studentName} 님이 도움을 요청했어요`,
          text: `${helpTypeLabel(h.helpType)} · ${helpTitle(h)}`,
          href: `/coordinator/help-requests?id=${h.helpRequestId}`,
        }));
        const docs: Notice[] = d.reviewQueue.map((r) => ({
          key: `doc-${r.applicationId}`,
          at: r.submittedAt,
          title: `${r.studentName} 님이 서류 검토를 요청했어요`,
          text: r.jobTitle,
          href: `/coordinator/applications/${r.applicationId}`,
        }));
        setNotices([...help, ...docs].sort((a, b) => b.at.localeCompare(a.at)));
      })
      .catch(() => {
        if (!ignore) setNotices(null);
      });
    return () => {
      ignore = true;
    };
  }, [pathname, refreshKey]);

  // 바깥을 누르거나 Esc 를 누르면 닫는다
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(null);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(null);
    };
    document.addEventListener("mousedown", onDown);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const count = notices?.length ?? 0;
  return (
    <header className="sticky top-0 z-10 flex h-[60px] items-center justify-between border-b border-(--coord-line) bg-(--coord-surface) px-[34px]">
      <p className="flex items-center gap-2.5 text-xs text-(--coord-muted)">
        워크스페이스 <span aria-hidden="true">/</span>
        <b className="font-medium text-(--coord-ink)">{pageName(pathname)}</b>
      </p>
      <div ref={boxRef} className="relative flex items-center gap-2">
        <button
          type="button"
          aria-label={`알림 ${count}건`}
          aria-expanded={open === "bell"}
          onClick={() => setOpen(open === "bell" ? null : "bell")}
          className="relative grid size-9 place-items-center rounded-lg hover:bg-(--coord-hover) focus-visible:outline-3 focus-visible:outline-(--coord-green)"
        >
          <Icon name="bell" />
          {count > 0 && (
            <span className="absolute -top-0.5 -right-0.5 grid h-[17px] min-w-[17px] place-items-center rounded-[10px] border-2 border-(--coord-surface) bg-(--coord-red) px-1 text-[9px] text-(--coord-surface)">
              {count}
            </span>
          )}
        </button>
        <button
          type="button"
          aria-expanded={open === "account"}
          aria-label={`${DEMO_COORDINATOR.name} ${DEMO_COORDINATOR.role} 계정 메뉴`}
          onClick={() => setOpen(open === "account" ? null : "account")}
          className="flex items-center gap-[9px] border-l border-(--coord-line) pl-4 text-left focus-visible:outline-3 focus-visible:outline-(--coord-green)"
        >
          <span aria-hidden="true" className="grid size-[34px] place-items-center rounded-full bg-(--coord-green-soft) text-xs font-semibold text-(--coord-green-ink)">
            {DEMO_COORDINATOR.initial}
          </span>
          <span>
            <b className="block text-xs">{DEMO_COORDINATOR.name}</b>
            <small className="text-[10px] text-(--coord-muted)">{DEMO_COORDINATOR.role}</small>
          </span>
          <Icon name="chevron" className="size-4" />
        </button>

        {open === "bell" && (
          <div role="dialog" aria-label="알림" className="absolute top-[46px] right-0 w-[350px] overflow-hidden rounded-xl border border-(--coord-line) bg-(--coord-surface) shadow-[0_15px_60px_rgb(0_0_0/0.12)]">
            <div className="border-b border-(--coord-line) px-5 py-3.5">
              <b className="text-[13px]">알림 {count}건</b>
              <p className="mt-0.5 text-[11px] text-(--coord-muted)">새 도움 요청 · 검토 대기 서류 기준이에요.</p>
            </div>
            {count === 0 ? (
              <p className="px-5 py-6 text-center text-xs text-(--coord-muted)">새 알림이 없어요.</p>
            ) : (
              <ul className="max-h-[360px] overflow-y-auto">
                {notices?.map((n) => (
                  <li key={n.key}>
                    <Link href={n.href} onClick={() => setOpen(null)} className="block border-b border-(--coord-line) px-5 py-3 text-xs hover:bg-(--coord-hover)">
                      <b className="block">{n.title}</b>
                      <span className="mt-0.5 block truncate text-(--coord-muted)">
                        {n.text} · {monthDayTime(n.at)}
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}

        {open === "account" && (
          <div role="menu" aria-label="계정 메뉴" className="absolute top-[46px] right-0 w-[240px] overflow-hidden rounded-xl border border-(--coord-line) bg-(--coord-surface) shadow-[0_15px_60px_rgb(0_0_0/0.12)]">
            <div className="border-b border-(--coord-line) px-5 py-3.5">
              <b className="block text-[13px]">
                {DEMO_COORDINATOR.name} {DEMO_COORDINATOR.role}
              </b>
              {DEMO_COORDINATOR.isDemo && <p className="mt-0.5 text-[11px] text-(--coord-muted)">데모 계정 · 로그인 기능은 아직 없어요</p>}
            </div>
            <button
              type="button"
              role="menuitem"
              disabled
              aria-disabled="true"
              className="block w-full cursor-not-allowed border-b border-(--coord-line) px-5 py-3 text-left text-xs text-(--coord-muted)"
            >
              계정 설정 <span className="ml-1 text-[10px]">(준비 중)</span>
            </button>
            <Link href="/" role="menuitem" onClick={() => setOpen(null)} className="block px-5 py-3 text-xs hover:bg-(--coord-hover)">
              역할 선택 화면으로
            </Link>
          </div>
        )}
      </div>
    </header>
  );
}
