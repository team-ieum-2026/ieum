"use client";
// 주인: FE-B — C1 대시보드 (FE-B-07). 도움 요청(맨 위) · 상태별 집계 4개 + 구직 중 세부 3개 · 비율 막대 · 할 일 3종
// 데이터는 GET /api/coordinator/dashboard (docs/API.md 2-3). 이동: 도움 요청 · 전화 · 비고 → C3, 서류 검토 대기 → C4.
// 완료 기준: "추가 확인 필요(무응답)"와 "구직 중(미취업)"은 다른 색 · 다른 칸.
import Link from "next/link";
import { useEffect, useState } from "react";
import { REVIEW_QUEUE_ANCHOR } from "@/components/coordinator/CoordinatorSidebar";
import { ApiError } from "@/lib/api/client";
import { getDashboard } from "@/lib/api/coordinatorReview";
import type { Dashboard } from "@/types/coordinator";
import type { ContactMethod } from "@/types/student";

const CONTACT_LABEL: Record<ContactMethod, string> = { SMS: "문자", PHONE: "전화", EMAIL: "이메일" };

/** "2026-10-24T09:12:00+09:00" → "10.24" · "09:12" (서버가 +09:00 으로 준다) */
const monthDay = (iso: string) => `${iso.slice(5, 7)}.${iso.slice(8, 10)}`;
const time = (iso: string) => iso.slice(11, 16);

/** 할 일 카드마다 앞에서 몇 개만 보여 준다 */
const PREVIEW_COUNT = 3;

export default function DashboardScreen() {
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let ignore = false;
    getDashboard()
      .then((d) => {
        if (!ignore) setData(d);
      })
      .catch((e: unknown) => {
        if (!ignore) setError(e instanceof ApiError ? e.message : "대시보드를 불러오지 못했어요.");
      });
    return () => {
      ignore = true;
    };
  }, [reloadKey]);

  // 사이드바 "서류 검토"(/coordinator#review-queue)로 들어오면 데이터가 그려진 뒤 그 영역으로 내려간다
  useEffect(() => {
    if (data && window.location.hash === `#${REVIEW_QUEUE_ANCHOR}`) {
      document.getElementById(REVIEW_QUEUE_ANCHOR)?.scrollIntoView({ block: "start" });
    }
  }, [data]);

  if (!data) {
    return (
      <div className="flex min-h-[50vh] flex-col items-center justify-center gap-3 text-sm text-ink-sub">
        <p>{error ?? "대시보드를 불러오는 중이에요…"}</p>
        {error && (
          <button
            type="button"
            onClick={() => {
              setError(null);
              setReloadKey((k) => k + 1);
            }}
            className="font-bold text-primary"
          >
            다시 시도
          </button>
        )}
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold">대시보드</h1>
        <p className="text-[13px] text-ink-sub">
          {data.asOf.slice(0, 10).replaceAll("-", ".")} 기준 · 관리 수료생 {data.totalStudents}명
        </p>
      </header>

      <HelpRequests data={data} />
      <StatusOverview data={data} />

      <div className="grid grid-cols-3 gap-4">
        <TodoCard
          id={REVIEW_QUEUE_ANCHOR}
          title="서류 검토 대기"
          count={data.reviewQueue.length}
          badgeClass="bg-primary-soft text-primary-strong"
          empty="검토할 서류가 없어요"
        >
          {data.reviewQueue.map((r) => (
            <li key={r.applicationId}>
              <Link
                href={`/coordinator/applications/${r.applicationId}`}
                className="flex items-center justify-between gap-2 rounded-[10px] px-3 py-2.5 hover:bg-coord-bg focus-visible:outline-2 focus-visible:outline-primary"
              >
                <span className="flex min-w-0 flex-col">
                  <b className="text-sm">{r.studentName}</b>
                  <span className="truncate text-xs text-ink-sub">
                    {r.jobTitle} · {monthDay(r.submittedAt)} 제출
                  </span>
                </span>
                <span className="shrink-0 text-xs font-bold text-primary">검토 →</span>
              </Link>
            </li>
          ))}
        </TodoCard>

        <TodoCard
          title="전화 확인 필요"
          count={data.callList.length}
          badgeClass="bg-warn-bg text-warn-ink"
          empty="전화로 확인할 수료생이 없어요"
          more={data.callList.length > PREVIEW_COUNT ? `${data.callList.length}명 모두 보기` : null}
        >
          {data.callList.slice(0, PREVIEW_COUNT).map((c) => (
            <li key={c.studentId} className="flex items-center justify-between gap-2 px-3 py-2.5">
              <span className="flex min-w-0 flex-col">
                <b className="text-sm">{c.studentName}</b>
                <span className="text-xs text-ink-sub">
                  {c.lastResponseAt ? `마지막 응답 ${monthDay(c.lastResponseAt)} · ` : "응답 기록 없음 · "}
                  {c.daysSinceResponse}일째
                </span>
              </span>
              {/* 통화 · 상담 기록 추가는 C3(FE-A-15)에 있다 */}
              <StudentLink studentId={c.studentId} label="통화 기록" />
            </li>
          ))}
        </TodoCard>

        <TodoCard
          title="비고 확인"
          count={data.notesToCheck.length}
          badgeClass="bg-coord-bg text-ink-sub"
          empty="새 비고가 없어요"
          more={data.notesToCheck.length > PREVIEW_COUNT ? `${data.notesToCheck.length}명 모두 보기` : null}
        >
          {data.notesToCheck.slice(0, PREVIEW_COUNT).map((n) => (
            <li key={n.studentId} className="flex items-center justify-between gap-2 px-3 py-2.5">
              <span className="flex min-w-0 flex-col">
                <b className="text-sm">{n.studentName}</b>
                <span className="truncate text-xs text-ink-sub">&ldquo;{n.passNote}&rdquo;</span>
              </span>
              <StudentLink studentId={n.studentId} label="보기" />
            </li>
          ))}
        </TodoCard>
      </div>
    </div>
  );
}

/** 맨 위 도움 요청. 수료생이 M2 → M4 에서 보낸 것 (INT-02) */
function HelpRequests({ data }: { data: Dashboard }) {
  const list = data.helpRequests;
  if (list.length === 0) {
    return (
      <section aria-label="도움 요청" className="rounded-[14px] border border-line bg-surface px-5 py-4 text-sm text-ink-sub">
        새 도움 요청이 없어요
      </section>
    );
  }
  return (
    <section
      aria-label="도움 요청"
      className="flex flex-col gap-2.5 rounded-[14px] border-[1.5px] border-danger/40 bg-danger-bg px-5 py-4"
    >
      <div className="flex items-baseline gap-3">
        <h2 className="text-base font-bold text-danger">도움 요청 {list.length}건이 새로 들어왔어요</h2>
        <span className="text-xs text-danger">먼저 연락해 주세요</span>
      </div>
      <ul className="flex flex-col gap-2">
        {list.map((h) => (
          // 줄(흰 배경)은 카드 폭 그대로, 내용 · 버튼 묶음만 최대 960px → 넓은 화면에서도 "상세 보기"가 내용 가까이
          <li key={h.helpRequestId} className="rounded-[10px] bg-surface px-3.5 py-2.5 text-[13px]">
            <div className="flex max-w-[960px] items-center justify-between gap-3">
              <span className="min-w-0">
                <b>{h.studentName}</b>
                <span className="text-ink-sub">
                  {" · "}
                  {[
                    h.courseLabel,
                    `${monthDay(h.createdAt)} ${time(h.createdAt)}`,
                    h.jobTitle && `공고: ${h.jobTitle}`,
                    h.memo && `메모: "${h.memo}"`,
                    h.contactMethod && `연락 희망: ${CONTACT_LABEL[h.contactMethod]}`,
                  ]
                    .filter(Boolean)
                    .join(" · ")}
                </span>
              </span>
              <Link
                href={`/coordinator/students/${h.studentId}`}
                className="shrink-0 rounded-lg border-[1.5px] border-danger px-3 py-1.5 text-xs font-bold text-danger hover:bg-danger-bg focus-visible:outline-2 focus-visible:outline-danger"
              >
                상세 보기
              </Link>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}

/** 상태별 집계 4칸 + 구직 중 세부 3개 + 전체 비율 막대 (S12 자동 분류) */
function StatusOverview({ data }: { data: Dashboard }) {
  const s = data.statusSummary;
  // 막대 · 범례 공통. 구직 중 세부는 같은 회색을 진하기로 나누고, 추가 확인 필요는 노랑 계열로 구직 중과 구분
  const segments = [
    { label: "취업 완료", value: s.EMPLOYED, color: "bg-primary" },
    { label: "기관 추천 지원 중", value: s.SEEKING.APPLYING, color: "bg-ink-sub" },
    { label: "패스 후 대기 중", value: s.SEEKING.WAITING, color: "bg-ink-sub/60" },
    { label: "기타 사유", value: s.SEEKING.OTHER, color: "bg-ink-sub/30" },
    { label: "도움 필요", value: s.NEED_HELP, color: "bg-danger" },
    { label: "추가 확인 필요 (확인 불가)", value: s.CHECK_REQUIRED, color: "bg-warn-ink" },
  ];
  const total = segments.reduce((sum, seg) => sum + seg.value, 0);

  const cards = [
    { label: "취업 완료", value: s.EMPLOYED, note: "패스 사유 '이미 취업'", className: "bg-primary-tint text-primary-strong" },
    {
      label: "구직 중",
      value: s.SEEKING.total,
      note: `추천 지원 ${s.SEEKING.APPLYING} · 패스 후 대기 ${s.SEEKING.WAITING} · 기타 ${s.SEEKING.OTHER}`,
      className: "bg-coord-bg text-ink",
    },
    { label: "도움 필요", value: s.NEED_HELP, note: "코디네이터 개입", className: "bg-danger-bg text-danger" },
    { label: "추가 확인 필요", value: s.CHECK_REQUIRED, note: "1주 무응답 · 전화 확인", className: "bg-warn-bg text-warn-ink" },
  ];

  return (
    <section aria-labelledby="status-title" className="flex flex-col gap-4 rounded-2xl border border-line bg-surface p-5">
      <h2 id="status-title" className="text-base font-bold">
        수료 후 현황{" "}
        <span className="text-xs font-normal text-ink-sub">응답 기반 자동 분류 · 무응답은 미취업과 따로 집계</span>
      </h2>
      <ul className="grid grid-cols-4 gap-3">
        {cards.map((c) => (
          <li key={c.label} className={`flex flex-col gap-1 rounded-xl px-4 py-3 ${c.className}`}>
            <span className="text-[13px] font-bold">{c.label}</span>
            <b className="text-2xl">
              {c.value}
              <span className="ml-0.5 text-sm font-normal">명</span>
            </b>
            <span className="text-xs opacity-80">{c.note}</span>
          </li>
        ))}
      </ul>
      {total > 0 && (
        <div className="flex flex-col gap-2">
          <div className="flex h-3 overflow-hidden rounded-full bg-coord-bg" role="img" aria-label="전체 상태 비율">
            {segments
              .filter((seg) => seg.value > 0)
              .map((seg) => (
                <span key={seg.label} className={seg.color} style={{ width: `${(seg.value / total) * 100}%` }} />
              ))}
          </div>
          <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-ink-sub">
            {segments.map((seg) => (
              <li key={seg.label} className="flex items-center gap-1.5">
                <span className={`size-2.5 rounded-sm ${seg.color}`} aria-hidden="true" />
                {seg.label} {seg.value}명 ({Math.round((seg.value / total) * 1000) / 10}%)
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}

function TodoCard({
  id,
  title,
  count,
  badgeClass,
  empty,
  more = null,
  children,
}: {
  id?: string;
  title: string;
  count: number;
  badgeClass: string;
  empty: string;
  /** "N명 모두 보기" (C2 로) */
  more?: string | null;
  children: React.ReactNode;
}) {
  return (
    <section id={id} aria-label={title} className="flex scroll-mt-8 flex-col gap-2 rounded-2xl border border-line bg-surface p-4">
      <h2 className="flex items-center justify-between text-[15px] font-bold">
        {title}
        <span className={`rounded-[10px] px-2 py-px text-xs font-bold ${badgeClass}`}>{count}</span>
      </h2>
      {count === 0 ? (
        <p className="px-3 py-2.5 text-[13px] text-ink-muted">{empty}</p>
      ) : (
        <ul className="flex flex-col divide-y divide-line">{children}</ul>
      )}
      {more && (
        <Link href="/coordinator/students" className="px-3 text-xs font-bold text-primary">
          {more} →
        </Link>
      )}
    </section>
  );
}

function StudentLink({ studentId, label }: { studentId: number; label: string }) {
  return (
    <Link
      href={`/coordinator/students/${studentId}`}
      className="shrink-0 rounded-lg border border-line px-2.5 py-1 text-xs font-bold text-ink-sub hover:bg-coord-bg focus-visible:outline-2 focus-visible:outline-primary"
    >
      {label}
    </Link>
  );
}
