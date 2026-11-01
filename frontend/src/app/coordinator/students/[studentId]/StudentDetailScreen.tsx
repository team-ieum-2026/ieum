"use client";
// 주인: FE-B — C3 수료생 상세 (11/1 역할 조정으로 FE-B, 11/1 개편 목업).
// 위: 목업의 머리 · 다음 행동 · 추천 공고와 응답 · 진행 중인 지원서류 · 도움 요청(접수) · 기본 정보 — 있는 값만
// 아래: 기존 역량 프로필 · 등록 자료 · 비고 · 상담 기록 + '통화 · 상담 기록 추가' 유지 (POST …/contact-logs)
// 도움 요청은 대시보드 API 의 접수(OPEN) 목록에서 이 수료생 것만 거른다. ?log=new 로 들어오면 기록 추가 창을 바로 연다.
// 취업 확인 경로 · 마지막 확인일 · 회사/직무 · 후속 연락일 · 상태 변경 이력 · '취업 상태 확인·변경'은 저장하는 곳이 없어 숨김
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Avatar, Badge, Icon, Panel, PanelHead, btn } from "@/components/coordinator/CoordUi";
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
import Tag from "@/components/ui/Tag";
import { ActionError, ApiState } from "@/components/student/ScreenParts";
import { dateLabel } from "@/components/student/navigation";
import { passLabels, subStatusLabels } from "@/components/student/labels";
import { errorMessage, useApi } from "@/components/student/useApi";
import { getDashboard } from "@/lib/api/coordinatorReview";
import { addContactLog, getCoordinatorStudent } from "@/lib/api/coordinatorStudents";
import type { DashboardHelpRequest } from "@/types/coordinator";
import type { ContactType, CoordinatorStudentDetail, RecommendationHistoryItem } from "@/types/student";

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
    <div>
      <Link href="/coordinator/students" className="mb-[18px] inline-flex items-center gap-[7px] text-xs text-(--coord-muted) hover:text-(--coord-green)">
        <Icon name="back" className="size-4" /> 수료생 목록
      </Link>
      <ApiState loading={loading} error={error} retry={reload} notFoundHref="/coordinator/students" notFoundLabel="수료생 목록으로" />
      {data && (
        <>
          <Head data={data} help={help} onAddLog={openLog} />
          {saved && (
            <p role="status" className="mt-3 rounded-lg bg-(--coord-green-soft) px-3.5 py-3 text-[13px] text-(--coord-green-ink)">
              상담 기록을 저장했어요.
            </p>
          )}
          <div className="mt-[22px] grid grid-cols-[minmax(0,1.7fr)_minmax(280px,1fr)] items-start gap-5">
            <div className="flex flex-col gap-[18px]">
              <Recommendations data={data} />
              <Documents data={data} />
              <HelpList help={help} />
            </div>
            <div className="flex flex-col gap-[18px]">
              <BasicInfo data={data} help={help} />
            </div>
          </div>
          <div className="mt-[22px] grid grid-cols-3 items-start gap-5">
            <Competency data={data} />
            <Registered data={data} />
            <ContactLogs data={data} onAdd={openLog} />
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

function Head({ data, help, onAddLog }: { data: CoordinatorStudentDetail; help: DashboardHelpRequest[]; onAddLog: () => void }) {
  const s = data.student;
  const reviewDoc = historyItems(data).find((i) => i.applicationStatus === "SUBMITTED" && i.applicationId);
  // 다음 행동: 접수된 도움 요청 → 검토 대기 서류 → 상담 기록 순 (있는 데이터로만 정한다)
  const next = help[0]
    ? { text: "도움 요청 확인", href: `/coordinator/help-requests?id=${help[0].helpRequestId}` }
    : reviewDoc
      ? { text: "서류 검토", href: `/coordinator/applications/${reviewDoc.applicationId}` }
      : null;
  return (
    <Panel label="수료생 요약" className="p-[22px]">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-[17px]">
          <Avatar name={s.name} large />
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-[27px] leading-[1.3] font-bold tracking-[-1px]">{s.name}</h1>
              <Badge tone={manageTone[s.status]}>
                {manageLabels[s.status]}
                {s.subStatus && ` · ${subStatusLabels[s.subStatus]}`}
              </Badge>
              {help.length > 0 && <Badge tone="red">도움 요청 {help.length}건</Badge>}
            </div>
            <p className="mt-[7px] text-xs text-(--coord-muted)">{[courseName(s.courseLabel), s.desiredJob].filter(Boolean).join(" · ")}</p>
          </div>
        </div>
        <button type="button" onClick={onAddLog} className={`${btn.base} ${btn.primary}`}>
          통화 · 상담 기록 추가
        </button>
      </div>
      <div className="mt-2.5 flex flex-wrap items-start justify-between gap-4 rounded-[10px] border border-(--coord-tint-line) bg-(--coord-tint) px-3.5 py-[9px]">
        <div>
          <span className="text-[11px] font-bold tracking-[1.4px] text-(--coord-green)">NEXT ACTION</span>
          <h3 className="mt-[5px] text-[15px] font-bold">{s.reason ?? manageLabels[s.status]}</h3>
          <p className="mt-[5px] text-xs text-(--coord-muted)">{s.lastResponseAt ? `${monthDay(s.lastResponseAt)} 응답` : "응답 기록 없음"}</p>
        </div>
        {next && (
          <Link href={next.href} className={`${btn.base} ${btn.primary}`}>
            {next.text}
          </Link>
        )}
      </div>
    </Panel>
  );
}

function Recommendations({ data }: { data: CoordinatorStudentDetail }) {
  const items = historyItems(data);
  return (
    <Panel label="추천 공고와 수료생 응답">
      <PanelHead title="추천 공고와 수료생 응답">
        <span className="text-xs text-(--coord-muted)">추천 → 선택 → 다음 행동</span>
      </PanelHead>
      {items.length === 0 ? (
        <p className="p-[35px] text-center text-[13px] text-(--coord-muted)">저장된 추천 공고 응답이 없어요.</p>
      ) : (
        <ul className="max-h-[360px] overflow-auto">
          {items.map((item) => (
            <RecRow key={item.itemId} item={item} recommendedAt={item.recommendedAt} />
          ))}
        </ul>
      )}
    </Panel>
  );
}

function RecRow({ item, recommendedAt }: { item: RecommendationHistoryItem; recommendedAt: string }) {
  return (
    <li className="flex items-center gap-[13px] border-b border-(--coord-line) px-5 py-[17px] last:border-b-0">
      <span className="grid size-[38px] shrink-0 place-items-center rounded-[9px] border border-(--coord-line) bg-(--coord-head) text-sm font-bold text-(--coord-green-ink)">
        {item.companyName.replace(/^\(주\)\s*/, "").slice(0, 1)}
      </span>
      <div className="min-w-0">
        <h3 className="text-xs font-bold">
          {item.companyName} · {item.title}
        </h3>
        <p className="mt-0.5 text-[11px] text-(--coord-muted)">
          {dateLabel(recommendedAt)} 추천{item.respondedAt && ` · ${dateLabel(item.respondedAt)} 응답`}
        </p>
        {item.passReason && <p className="mt-0.5 text-[11px] text-(--coord-muted)">{passLabels[item.passReason]}</p>}
        {item.passNote && <p className="mt-0.5 text-[11px] text-(--coord-muted)">비고: {item.passNote}</p>}
      </div>
      <span className="ml-auto">
        <Badge tone={responseTone[item.response]}>{responseTagLabels[item.response]}</Badge>
      </span>
    </li>
  );
}

function Documents({ data }: { data: CoordinatorStudentDetail }) {
  const docs = historyItems(data).filter((i) => i.applicationId && i.applicationStatus);
  return (
    <Panel label="진행 중인 지원서류">
      <PanelHead title="진행 중인 지원서류">
        <Badge>{docs.length}건</Badge>
      </PanelHead>
      {docs.length === 0 ? (
        <p className="p-[35px] text-center text-[13px] text-(--coord-muted)">지원한 서류가 없어요.</p>
      ) : (
        <ul>
          {docs.map((d) => (
            <li key={d.itemId} className="flex items-center gap-[13px] border-b border-(--coord-line-soft) px-[22px] py-[17px] last:border-b-0">
              <span className="grid size-[34px] shrink-0 place-items-center rounded-[9px] bg-(--coord-green-soft) text-(--coord-green-ink)">
                <Icon name="doc" />
              </span>
              <div className="min-w-0 flex-1">
                <h3 className="truncate text-[13px] font-bold">
                  {d.companyName} · {d.title}
                </h3>
              </div>
              {d.applicationStatus && <Badge tone={docTone[d.applicationStatus]}>{docLabels[d.applicationStatus]}</Badge>}
              <Link href={`/coordinator/applications/${d.applicationId}`} className={`${btn.base} ${btn.outline} ${btn.sm}`}>
                서류 보기
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}

function HelpList({ help }: { help: DashboardHelpRequest[] }) {
  return (
    <Panel label="도움 요청">
      <PanelHead title="도움 요청">
        <span className="text-xs text-(--coord-muted)">접수된 요청</span>
      </PanelHead>
      {help.length === 0 ? (
        <p className="p-[35px] text-center text-[13px] text-(--coord-muted)">접수된 도움 요청이 없어요.</p>
      ) : (
        <ul>
          {help.map((h) => (
            <li key={h.helpRequestId} className="flex items-center gap-[13px] border-b border-(--coord-line-soft) px-[22px] py-[17px] last:border-b-0">
              <div className="min-w-0 flex-1">
                <h3 className="truncate text-[13px] font-bold">{h.jobTitle ?? "추천 공고 도움 요청"}</h3>
                <p className="mt-[3px] text-[11px] text-(--coord-muted)">
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
      )}
    </Panel>
  );
}

function BasicInfo({ data, help }: { data: CoordinatorStudentDetail; help: DashboardHelpRequest[] }) {
  const s = data.student;
  const pref = help.find((h) => h.contactMethod)?.contactMethod;
  const rows: [string, string | null][] = [
    ["이메일", s.email],
    ["선호 연락", pref ? contactLabels[pref] : null],
    ["희망 직무", s.desiredJob],
    ["희망 지역", s.preferredRegion],
    ["교육과정", courseName(s.courseLabel)],
    ["수료일", dateLabel(s.completedOn)],
    ["최근 응답일", s.lastResponseAt ? dateLabel(s.lastResponseAt) : null],
    ["상태 근거", s.reason],
  ];
  return (
    <Panel label="기본 정보">
      <PanelHead title="기본 정보" />
      <dl className="grid grid-cols-[100px_1fr] gap-3 p-[22px] text-xs">
        {rows.map(([label, value]) => (
          <div key={label} className="contents">
            <dt className="text-(--coord-muted)">{label}</dt>
            <dd className="break-words">{value || "기록 없음"}</dd>
          </div>
        ))}
      </dl>
    </Panel>
  );
}

function Competency({ data }: { data: CoordinatorStudentDetail }) {
  const cp = data.competencyProfile;
  return (
    <Panel label="역량 프로필">
      <PanelHead title="역량 프로필">
        <Tag source="AI" />
      </PanelHead>
      <div className="space-y-3 p-[22px] text-[13px]">
        {cp ? (
          <>
            <div>
              <h3 className="mb-1 text-xs font-bold text-(--coord-muted)">역할</h3>
              <p>{cp.roles.join(", ") || "추출된 역할이 없어요."}</p>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {cp.skills.map((skill) => (
                <span key={skill.name} className="rounded-md border border-(--coord-line) px-2 py-1 text-xs">
                  {skill.name}
                </span>
              ))}
            </div>
            <div>
              <h3 className="mb-1 text-xs font-bold text-(--coord-muted)">성과</h3>
              {cp.achievements.length ? (
                <ul className="space-y-2">
                  {cp.achievements.map((a) => (
                    <li key={a.text}>{a.text}</li>
                  ))}
                </ul>
              ) : (
                <p className="text-(--coord-muted)">추출된 성과가 없어요.</p>
              )}
            </div>
          </>
        ) : (
          <p className="text-(--coord-muted)">아직 생성된 역량 프로필이 없어요.</p>
        )}
      </div>
    </Panel>
  );
}

function Registered({ data }: { data: CoordinatorStudentDetail }) {
  const r = data.registered;
  return (
    <Panel label="등록 자료">
      <PanelHead title="등록 자료">
        <Tag source="REGISTERED" />
      </PanelHead>
      <ul className="list-disc space-y-2 py-[22px] pr-[22px] pl-10 text-[13px]">
        <li>학력 {r.education}건</li>
        <li>자격증 {r.certificates}건</li>
        <li>프로젝트 · 포트폴리오 {r.projects}건</li>
        <li>자기소개서 원본: {r.hasCoverLetter ? "등록됨" : "미등록"}</li>
      </ul>
    </Panel>
  );
}

function ContactLogs({ data, onAdd }: { data: CoordinatorStudentDetail; onAdd: () => void }) {
  return (
    <Panel label="비고 · 상담 기록">
      <PanelHead title="비고 · 상담 기록">
        <button type="button" onClick={onAdd} className={`${btn.base} ${btn.outline} ${btn.sm}`}>
          기록 추가
        </button>
      </PanelHead>
      <div className="p-[22px]">
        {data.contactLogs.length === 0 ? (
          <p className="text-[13px] text-(--coord-muted)">아직 남긴 비고나 상담 기록이 없어요.</p>
        ) : (
          <ul className="max-h-[360px] divide-y divide-(--coord-line) overflow-auto">
            {data.contactLogs.map((log, index) => (
              <li key={`${log.contactLogId}-${index}`} className="space-y-2 py-3 first:pt-0">
                <p className="text-xs text-(--coord-muted)">
                  {dateLabel(log.createdAt)} · {contactTypeLabels[log.contactType]}
                </p>
                <p className="text-[13px] leading-relaxed whitespace-pre-wrap">{log.note}</p>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Panel>
  );
}
