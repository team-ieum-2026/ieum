"use client";
// 주인: FE-B — C3 수료생 상세 (11/1 역할 조정으로 FE-B, 11/1 개편 목업 studentDetail()).
// 화면 높이에 맞춘 고정 배치: 위 요약(이름 · 상태 · 다음 행동 · 마지막 기록 활동)은 늘 보이고, 아래 두 열은 각각 안에서 스크롤.
// 왼쪽: 추천 공고와 수료생 응답 · 진행 중인 지원서류 · 도움 요청(접수)
// 오른쪽: 기본 정보 · 통화 · 상담 기록(+ 기록 추가) · 역량 프로필 · 등록 자료 (기존 C3 기능은 모두 유지)
// 도움 요청은 대시보드 API 의 접수(OPEN) 목록에서 이 수료생 것만 거른다. ?log=new 로 들어오면 기록 추가 창을 바로 연다.
// 취업 확인 경로 · 마지막 확인일 · 회사/직무 · 후속 연락일 · 상태 변경 이력 · '취업 상태 확인·변경'은 저장하는 곳이 없어 숨김.
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState, type ReactNode } from "react";
import { Avatar, Badge, Icon, btn } from "@/components/coordinator/CoordUi";
import {
  contactLabels,
  courseName,
  docLabels,
  docTone,
  manageLabels,
  manageTone,
  monthDay,
  monthDayTime,
  responseTagLabels,
  responseTone,
} from "@/components/coordinator/coordLabels";
import BottomSheet from "@/components/ui/BottomSheet";
import { ActionError, ApiState } from "@/components/student/ScreenParts";
import { dateLabel } from "@/components/student/navigation";
import { passLabels, subStatusLabels } from "@/components/student/labels";
import { errorMessage, useApi } from "@/components/student/useApi";
import { getDashboard } from "@/lib/api/coordinatorReview";
import { addContactLog, getCoordinatorStudent } from "@/lib/api/coordinatorStudents";
import type { DashboardHelpRequest } from "@/types/coordinator";
import type { ContactType, CoordinatorStudentDetail } from "@/types/student";

const contactTypeLabels: Record<ContactType, string> = { PHONE: "통화", MEETING: "상담", MESSAGE: "메시지" };

export default function StudentDetailScreen({ studentId }: { studentId: number }) {
  const searchParams = useSearchParams();
  const load = useCallback(() => getCoordinatorStudent(studentId), [studentId]);
  const { data, loading, error, reload, setData } = useApi(load);
  const [help, setHelp] = useState<DashboardHelpRequest[]>([]);
  const [open, setOpen] = useState(searchParams.get("log") === "new");
  const [contactType, setContactType] = useState<ContactType>("PHONE");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  // 이 수료생의 접수(OPEN) 도움 요청. 못 받아도 상세는 그대로 보여 준다
  useEffect(() => {
    let ignore = false;
    getDashboard()
      .then((d) => {
        if (!ignore) setHelp(d.helpRequests.filter((h) => h.studentId === studentId));
      })
      .catch(() => {});
    return () => {
      ignore = true;
    };
  }, [studentId]);

  const openLog = () => {
    setOpen(true);
    setSaveError(null);
    setSaved(false);
  };

  return (
    // 상단 막대 60 + 위아래 여백 70 을 뺀 높이. 작은 화면 · 확대에서는 페이지가 자연스럽게 늘어난다
    <div className="flex min-h-[640px] flex-col lg:h-[calc(100vh-130px)]">
      <Link href="/coordinator/students" className="mb-[18px] inline-flex w-fit items-center gap-[7px] text-xs text-(--coord-muted) hover:text-(--coord-green)">
        <Icon name="back" className="size-4" /> 수료생 목록
      </Link>
      <ApiState loading={loading} error={error} retry={reload} notFoundHref="/coordinator/students" notFoundLabel="수료생 목록으로" />
      {data && (
        <>
          <Head data={data} help={help} onAddLog={openLog} />
          {saved && (
            <p role="status" className="mt-3 shrink-0 rounded-lg bg-(--coord-green-soft) px-3.5 py-2.5 text-[13px] text-(--coord-green-ink)">
              상담 기록을 저장했어요.
            </p>
          )}
          <div className="mt-[22px] grid min-h-0 flex-1 grid-cols-[minmax(0,1.7fr)_minmax(280px,1fr)] gap-5">
            <div className="flex min-h-0 flex-col gap-[18px] overflow-y-auto pr-1">
              <Recommendations data={data} />
              <Documents data={data} />
              <HelpList help={help} />
            </div>
            <div className="flex min-h-0 flex-col gap-[18px] overflow-y-auto pr-1">
              <BasicInfo data={data} help={help} />
              <ContactLogs data={data} onAdd={openLog} />
              <Competency data={data} />
              <Registered data={data} />
            </div>
          </div>
          <BottomSheet
            open={open}
            onClose={() => {
              if (!busy) setOpen(false);
            }}
            title="통화 · 상담 기록 추가"
          >
            <form
              className="space-y-4"
              onSubmit={async (event) => {
                event.preventDefault();
                if (!note.trim() || busy) return;
                setBusy(true);
                setSaveError(null);
                try {
                  const log = await addContactLog(studentId, { contactType, note: note.trim() });
                  setData({ ...data, contactLogs: [log, ...data.contactLogs] });
                  setNote("");
                  setOpen(false);
                  setSaved(true);
                } catch (e) {
                  setSaveError(errorMessage(e));
                } finally {
                  setBusy(false);
                }
              }}
            >
              <label className="block space-y-2 text-sm">
                <span>연락 유형</span>
                <select
                  value={contactType}
                  disabled={busy}
                  onChange={(event) => setContactType(event.target.value as ContactType)}
                  className="min-h-11 w-full rounded-xl border border-line bg-surface px-3"
                >
                  {(Object.keys(contactTypeLabels) as ContactType[]).map((value) => (
                    <option key={value} value={value}>
                      {contactTypeLabels[value]}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block space-y-2 text-sm">
                <span>상담 내용</span>
                <textarea
                  required
                  rows={5}
                  value={note}
                  disabled={busy}
                  onChange={(event) => setNote(event.target.value)}
                  placeholder="상담 내용을 남겨주세요"
                  className="w-full rounded-xl border border-line p-3"
                />
              </label>
              <ActionError message={saveError} />
              <button type="submit" disabled={!note.trim() || busy} className={`${btn.base} ${btn.primary} w-full py-3`}>
                {busy ? "저장 중…" : "저장"}
              </button>
            </form>
          </BottomSheet>
        </>
      )}
    </div>
  );
}

/** 추천 이력의 모든 공고 (최근 추천 먼저) */
const historyItems = (data: CoordinatorStudentDetail) => data.history.flatMap((h) => h.items.map((item) => ({ ...item, recommendedAt: h.createdAt })));

/** 저장된 기록 중 가장 최근 것: 상담 기록 작성 · 추천 공고 응답 (실제 데이터만, 없으면 null) */
function lastActivity(data: CoordinatorStudentDetail): { at: string; text: string } | null {
  const logs = data.contactLogs.map((l) => ({ at: l.createdAt, text: `${contactTypeLabels[l.contactType]} 기록 작성` }));
  const responses = historyItems(data)
    .filter((i) => i.respondedAt && i.response !== "NONE")
    .map((i) => ({ at: i.respondedAt as string, text: `추천 공고 ${responseTagLabels[i.response]} · ${i.companyName}` }));
  return [...logs, ...responses].sort((a, b) => b.at.localeCompare(a.at))[0] ?? null;
}

function Head({ data, help, onAddLog }: { data: CoordinatorStudentDetail; help: DashboardHelpRequest[]; onAddLog: () => void }) {
  const s = data.student;
  const reviewDoc = historyItems(data).find((i) => i.applicationStatus === "SUBMITTED" && i.applicationId);
  // 다음 행동: 접수된 도움 요청 → 검토 대기 서류 순 (있는 데이터로만 정하고, 없으면 버튼을 만들지 않는다)
  const next = help[0]
    ? { text: "도움 요청 확인", href: `/coordinator/help-requests?id=${help[0].helpRequestId}` }
    : reviewDoc
      ? { text: "서류 검토", href: `/coordinator/applications/${reviewDoc.applicationId}` }
      : null;
  const activity = lastActivity(data);
  return (
    <section aria-label="수료생 요약" className="shrink-0 rounded-xl border border-(--coord-line) bg-(--coord-surface) p-[22px]">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-[17px]">
          <Avatar name={s.name} large />
          <div>
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-[27px] leading-[1.3] font-bold tracking-[-1px]">{s.name}</h1>
              <Badge tone={manageTone[s.status]}>{manageLabels[s.status]}</Badge>
              {help.length > 0 && <Badge tone="red">도움 요청 {help.length}건</Badge>}
            </div>
            <p className="mt-[7px] text-xs text-(--coord-muted)">
              {[courseName(s.courseLabel), s.desiredJob, s.subStatus && subStatusLabels[s.subStatus]].filter(Boolean).join(" · ")}
            </p>
          </div>
        </div>
        <button type="button" onClick={onAddLog} className={`${btn.base} ${btn.primary} px-4 py-3 text-sm`}>
          통화 · 상담 기록 추가
        </button>
      </div>
      <div className="mt-2.5 flex flex-wrap items-center justify-between gap-4 rounded-[10px] border border-(--coord-tint-line) bg-(--coord-tint) px-3.5 py-[9px]">
        <div>
          <span className="text-[11px] font-bold tracking-[1.4px] text-(--coord-green)">NEXT ACTION</span>
          <h3 className="mt-[5px] text-[15px] font-bold">{s.reason ?? manageLabels[s.status]}</h3>
          <p className="mt-[5px] text-xs text-(--coord-muted)">{s.lastResponseAt ? `${monthDay(s.lastResponseAt)} 앱 응답` : "앱 응답 기록 없음"}</p>
        </div>
        {next && (
          <Link href={next.href} className={`${btn.base} ${btn.primary} px-4 py-3 text-sm`}>
            {next.text}
          </Link>
        )}
      </div>
      {activity && (
        <p className="mt-2.5 rounded-lg border border-(--coord-tint-line) bg-(--coord-tint) px-3.5 py-2.5 text-xs leading-[1.7]">
          <b>마지막 기록 활동</b>　{monthDayTime(activity.at)} · {activity.text}
          <span className="text-(--coord-muted)"> · 저장된 응답 · 상담 기록 기준이며 실시간 접속 정보는 아니에요.</span>
        </p>
      )}
    </section>
  );
}

/** 목업 .panel + .panelhead. 데이터가 없으면 한 줄 안내만 (큰 빈 카드가 생기지 않게) */
function Card({ title, aside, empty, children }: { title: string; aside?: ReactNode; empty?: string | false; children?: ReactNode }) {
  return (
    <section aria-label={title} className="shrink-0 overflow-hidden rounded-xl border border-(--coord-line) bg-(--coord-surface)">
      <div className="flex items-center justify-between gap-3 border-b border-(--coord-line-soft) px-5 py-4">
        <h2 className="text-[15px] font-bold">{title}</h2>
        {aside}
      </div>
      {empty ? <p className="px-5 py-4 text-[13px] text-(--coord-muted)">{empty}</p> : children}
    </section>
  );
}

function Recommendations({ data }: { data: CoordinatorStudentDetail }) {
  const items = historyItems(data);
  return (
    <Card
      title="추천 공고와 수료생 응답"
      aside={<span className="text-xs text-(--coord-muted)">추천 → 선택 → 다음 행동</span>}
      empty={items.length === 0 && "저장된 추천 공고 응답이 없어요."}
    >
      <ul>
        {items.map((item) => (
          <li key={item.itemId} className="flex items-center gap-[13px] border-b border-(--coord-line) px-5 py-[15px] last:border-b-0">
            <span
              aria-hidden="true"
              className="grid size-[38px] shrink-0 place-items-center rounded-[9px] border border-(--coord-line) bg-(--coord-head) text-sm font-bold text-(--coord-green-ink)"
            >
              {item.companyName.replace(/^\(주\)\s*/, "").slice(0, 1)}
            </span>
            <div className="min-w-0">
              <h3 className="truncate text-[13px] font-bold">
                {item.companyName} · {item.title}
              </h3>
              <p className="mt-0.5 text-xs text-(--coord-muted)">
                {dateLabel(item.recommendedAt)} 추천{item.respondedAt ? ` · ${dateLabel(item.respondedAt)} 응답` : " · 아직 응답하지 않았어요"}
              </p>
              {(item.passReason || item.passNote) && (
                <p className="mt-0.5 text-xs text-(--coord-muted)">
                  {[item.passReason && passLabels[item.passReason], item.passNote && `비고: ${item.passNote}`].filter(Boolean).join(" · ")}
                </p>
              )}
            </div>
            <span className="ml-auto">
              <Badge tone={responseTone[item.response]}>{responseTagLabels[item.response]}</Badge>
            </span>
          </li>
        ))}
      </ul>
    </Card>
  );
}

function Documents({ data }: { data: CoordinatorStudentDetail }) {
  const docs = historyItems(data).filter((i) => i.applicationId && i.applicationStatus);
  return (
    <Card title="진행 중인 지원서류" aside={<Badge>{docs.length}건</Badge>} empty={docs.length === 0 && "지원한 서류가 없어요."}>
      <ul>
        {docs.map((d) => (
          <li key={d.itemId} className="flex items-center gap-[13px] border-b border-(--coord-line-soft) px-5 py-[15px] last:border-b-0">
            <span className="grid size-[34px] shrink-0 place-items-center rounded-[9px] bg-(--coord-green-soft) text-(--coord-green-ink)">
              <Icon name="doc" />
            </span>
            <div className="min-w-0 flex-1">
              <h3 className="truncate text-[13px] font-bold">
                {d.companyName} · {d.title}
              </h3>
              {d.respondedAt && <p className="mt-0.5 text-xs text-(--coord-muted)">{dateLabel(d.respondedAt)} 지원</p>}
            </div>
            {d.applicationStatus && <Badge tone={docTone[d.applicationStatus]}>{docLabels[d.applicationStatus]}</Badge>}
            <Link href={`/coordinator/applications/${d.applicationId}`} className={`${btn.base} ${btn.outline} ${btn.sm}`}>
              서류 보기
            </Link>
          </li>
        ))}
      </ul>
    </Card>
  );
}

function HelpList({ help }: { help: DashboardHelpRequest[] }) {
  return (
    <Card title="도움 요청" aside={<span className="text-xs text-(--coord-muted)">접수된 요청</span>} empty={help.length === 0 && "접수된 도움 요청이 없어요."}>
      <ul>
        {help.map((h) => (
          <li key={h.helpRequestId} className="flex items-center gap-[13px] border-b border-(--coord-line-soft) px-5 py-[15px] last:border-b-0">
            <span className="grid size-[34px] shrink-0 place-items-center rounded-[9px] bg-(--coord-red-soft) text-(--coord-red)">
              <Icon name="help" />
            </span>
            <div className="min-w-0 flex-1">
              <h3 className="truncate text-[13px] font-bold">{h.jobTitle ?? "추천 공고 도움 요청"}</h3>
              <p className="mt-0.5 text-xs text-(--coord-muted)">
                {h.contactMethod ? `${contactLabels[h.contactMethod]} 선호` : "선호 연락 없음"} · {monthDayTime(h.createdAt)} 접수
              </p>
            </div>
            <Badge tone="red">접수</Badge>
            <Link href={`/coordinator/help-requests?id=${h.helpRequestId}`} className={`${btn.base} ${btn.outline} ${btn.sm}`}>
              요청 보기
            </Link>
          </li>
        ))}
      </ul>
    </Card>
  );
}

function BasicInfo({ data, help }: { data: CoordinatorStudentDetail; help: DashboardHelpRequest[] }) {
  const s = data.student;
  const pref = help.find((h) => h.contactMethod)?.contactMethod;
  const rows: [string, ReactNode][] = [
    ["이메일", s.email],
    ["선호 연락", pref ? `${contactLabels[pref]} (도움 요청에서 고름)` : null],
    ["희망 직무", s.desiredJob],
    ["희망 지역", s.preferredRegion],
    ["교육과정", courseName(s.courseLabel)],
    ["수료일", dateLabel(s.completedOn)],
    [
      "현재 상태",
      <Badge key="status" tone={manageTone[s.status]}>
        {manageLabels[s.status]}
        {s.subStatus && ` · ${subStatusLabels[s.subStatus]}`}
      </Badge>,
    ],
    ["최근 응답일", s.lastResponseAt ? dateLabel(s.lastResponseAt) : null],
  ];
  return (
    <Card title="기본 정보">
      <dl className="grid grid-cols-[96px_1fr] items-center gap-3 p-5 text-[13px]">
        {rows.map(([label, value]) => (
          <div key={label} className="contents">
            <dt className="text-(--coord-muted)">{label}</dt>
            <dd className="break-words">{value || <span className="text-(--coord-muted)">기록 없음</span>}</dd>
          </div>
        ))}
      </dl>
    </Card>
  );
}

function ContactLogs({ data, onAdd }: { data: CoordinatorStudentDetail; onAdd: () => void }) {
  return (
    <Card
      title="통화 · 상담 기록"
      aside={
        <button type="button" onClick={onAdd} className={`${btn.base} ${btn.outline} ${btn.sm}`}>
          기록 추가
        </button>
      }
      empty={data.contactLogs.length === 0 && "아직 남긴 비고나 상담 기록이 없어요."}
    >
      <ul className="max-h-[320px] overflow-y-auto px-5 py-4">
        {data.contactLogs.map((log, index) => (
          <li key={`${log.contactLogId}-${index}`} className="border-l border-(--coord-tint-line) pb-4 pl-4 last:pb-0">
            <p className="text-[13px] leading-relaxed whitespace-pre-wrap">{log.note}</p>
            <small className="mt-1 block text-[11px] text-(--coord-muted)">
              {monthDayTime(log.createdAt)} · {contactTypeLabels[log.contactType]}
            </small>
          </li>
        ))}
      </ul>
    </Card>
  );
}

function Competency({ data }: { data: CoordinatorStudentDetail }) {
  const cp = data.competencyProfile;
  return (
    <Card title="역량 프로필" aside={<Badge tone="blue">AI 정리</Badge>} empty={!cp && "아직 생성된 역량 프로필이 없어요."}>
      {cp && (
        <div className="space-y-3 p-5 text-[13px]">
          <p>
            <span className="mr-2 text-xs font-bold text-(--coord-muted)">역할</span>
            {cp.roles.join(", ") || "추출된 역할이 없어요."}
          </p>
          {cp.skills.length > 0 && (
            <ul className="flex flex-wrap gap-1.5" aria-label="역량 기술">
              {cp.skills.map((skill) => (
                <li key={skill.name} className="rounded-md border border-(--coord-line) px-2 py-1 text-xs">
                  {skill.name}
                </li>
              ))}
            </ul>
          )}
          {cp.achievements.length > 0 && (
            <details>
              <summary className="cursor-pointer text-xs font-bold text-(--coord-green)">성과 {cp.achievements.length}개 보기</summary>
              <ul className="mt-2 list-disc space-y-1.5 pl-5">
                {cp.achievements.map((a) => (
                  <li key={a.text}>{a.text}</li>
                ))}
              </ul>
            </details>
          )}
        </div>
      )}
    </Card>
  );
}

function Registered({ data }: { data: CoordinatorStudentDetail }) {
  const r = data.registered;
  const rows: [string, string][] = [
    ["학력", `${r.education}건`],
    ["자격증", `${r.certificates}건`],
    ["프로젝트 · 포트폴리오", `${r.projects}건`],
    ["자기소개서 원본", r.hasCoverLetter ? "등록됨" : "미등록"],
  ];
  return (
    <Card title="등록 자료" aside={<Badge tone="green">등록 자료</Badge>}>
      <dl className="grid grid-cols-2 gap-x-4 gap-y-2 p-5 text-[13px]">
        {rows.map(([label, value]) => (
          <div key={label} className="flex justify-between gap-2">
            <dt className="text-(--coord-muted)">{label}</dt>
            <dd className="font-semibold">{value}</dd>
          </div>
        ))}
      </dl>
    </Card>
  );
}
