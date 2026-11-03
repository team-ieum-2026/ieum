"use client";
// 주인: FE-B — C3 수료생 상세 (11/1 역할 조정으로 FE-B, 11/3 리뉴얼 design-reference renderStudentDetailView).
// 리뉴얼 배치: 목록으로 → 프로필 머리 카드(이름 · 상태 · 기록 추가 + NEXT ACTION) → 2열 1.4 : 1
// 왼쪽: 응답이력(추천 공고와 수료생 응답) · 진행 중인 지원서류 · 도움 요청 이력(접수)
// 오른쪽: 기본 정보 · 통화 · 상담 기록(타임라인 + 기록 추가) · 역량 프로필 · 등록 자료 (기존 C3 기능은 모두 유지)
// 도움 요청은 대시보드 API 의 접수(OPEN) 목록에서 이 수료생 것만 거른다. ?log=new 로 들어오면 기록 추가 창을 바로 연다.
// 취업 확인 경로 · 마지막 확인일 · 회사/직무 · 후속 연락일 · 상태 변경 이력 · '취업 상태 변경'은 저장하는 곳(API)이 없어 숨김.
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState, type ReactNode } from "react";
import { Badge, Icon, Panel, PanelHead, backLink, btn } from "@/components/coordinator/CoordUi";
import { IS_MOCK, MOCK_SAVE_NOTE } from "@/components/coordinator/demoAccount";
import {
  contactLabels,
  courseName,
  docLabels,
  docTone,
  helpTitle,
  helpTypeLabel,
  manageLabels,
  manageTone,
  monthDay,
  monthDayTime,
  responseTagLabels,
  responseTone,
  studentExtra,
} from "@/components/coordinator/coordLabels";
import { notifyReviewQueueChanged } from "@/components/coordinator/CoordinatorSidebar";
import ContactLogDialog from "@/components/coordinator/ContactLogDialog";
import { ApiState } from "@/components/student/ScreenParts";
import { dateLabel } from "@/components/student/navigation";
import { passLabels, subStatusLabels } from "@/components/student/labels";
import { useApi } from "@/components/student/useApi";
import { getDashboard } from "@/lib/api/coordinatorReview";
import { getCoordinatorStudent } from "@/lib/api/coordinatorStudents";
import { STUDENTS_QUERY_KEY } from "../StudentsScreen";
import type { DashboardHelpRequest } from "@/types/coordinator";
import type { ContactType, CoordinatorStudentDetail } from "@/types/student";

const contactTypeLabels: Record<ContactType, string> = { PHONE: "통화", MEETING: "상담", MESSAGE: "메시지" };

export default function StudentDetailScreen({ studentId }: { studentId: number }) {
  const searchParams = useSearchParams();
  const router = useRouter();
  const load = useCallback(() => getCoordinatorStudent(studentId), [studentId]);
  const { data, loading, error, reload, setData } = useApi(load);
  const [help, setHelp] = useState<DashboardHelpRequest[]>([]);
  const [open, setOpen] = useState(searchParams.get("log") === "new");
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
    setSaved(false);
  };

  // C2 에서 쓰던 검색 조건 · 페이지로 돌아간다 (누를 때 읽음, 없으면 기본 목록)
  const backHref = "/coordinator/students";
  const goBack = (e: React.MouseEvent<HTMLAnchorElement>) => {
    try {
      const saved = sessionStorage.getItem(STUDENTS_QUERY_KEY);
      if (saved) {
        e.preventDefault();
        router.push(`${backHref}?${saved}`);
      }
    } catch {
      // 저장소를 못 쓰면 기본 목록 (링크 그대로)
    }
  };

  return (
    <div>
      {/* 2차 피드백: 배경 · 테두리 없이 아이콘과 글씨만 */}
      <div className="mb-3">
        <Link href={backHref} onClick={goBack} className={backLink}>
          <Icon name="back" className="size-4" /> 수료생 목록으로
        </Link>
      </div>
      <ApiState loading={loading} error={error} retry={reload} notFoundHref={backHref} notFoundLabel="수료생 목록으로" />
      {data && (
        <>
          <Head data={data} help={help} onAddLog={openLog} />
          {saved && (
            <p role="status" className="mb-4 rounded-md border border-(--coord-green-line) bg-(--coord-green-soft) px-4 py-3 text-sm text-(--coord-green-ink)">
              상담 기록을 저장했어요.
              {IS_MOCK && <b className="ml-2 text-(--coord-attention)">{MOCK_SAVE_NOTE}</b>}
            </p>
          )}
          {/* 리뉴얼 .crm-grid-split: 1.4fr : 1fr, 간격 20px, 위쪽 정렬 */}
          <div className="grid grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)] items-start gap-5">
            <div className="flex min-w-0 flex-col gap-5">
              <Recommendations data={data} />
              <Documents data={data} />
              <HelpList help={help} />
            </div>
            <div className="flex min-w-0 flex-col gap-5">
              <BasicInfo data={data} help={help} />
              <ContactLogs data={data} onAdd={openLog} />
              <Competency data={data} />
              <Registered data={data} />
            </div>
          </div>
          {/* 기록 추가: C3 · C5 공용 가운데 창 (내용이 길면 창 안에서 스크롤) */}
          {open && (
            <ContactLogDialog
              studentId={studentId}
              onClose={() => setOpen(false)}
              onSaved={(log) => {
                setData({ ...data, contactLogs: [log, ...data.contactLogs] });
                // 연락 기록을 남기면 서버가 연속 패스 연락 대상에서 뺀다 → 상세를 다시 받고(화면 유지), 사이드바 · 상단 알림 · 대시보드도 다시 조회
                reload();
                notifyReviewQueueChanged();
                setOpen(false);
                setSaved(true);
              }}
            />
          )}
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
  // 11/2 추가 값: 도움 요청 건수는 서버 openHelpRequestCount 우선 · 연속 패스 연락 대상은 서버 판정(needsPassFollowUp) 그대로
  const ext = studentExtra(s);
  const helpCount = ext.openHelpRequestCount ?? help.length;
  // 최근 패스 1건 (저장된 응답 중 가장 최근, 사유가 있으면 함께)
  const lastPass = historyItems(data)
    .filter((i) => i.response === "PASS" && i.respondedAt)
    .sort((a, b) => (b.respondedAt as string).localeCompare(a.respondedAt as string))[0];
  // 다음 행동: 접수된 도움 요청 → 검토 대기 서류 → 연속 패스 연락 순 (있는 데이터로만 정하고, 없으면 버튼을 만들지 않는다)
  const next: { text: string; title: string; href?: string } | null = help[0]
    ? { text: "요청 확인 바로가기", title: "접수된 도움 요청을 확인해 주세요", href: `/coordinator/help-requests?id=${help[0].helpRequestId}` }
    : reviewDoc
      ? { text: "서류 검토 바로가기", title: "검토 대기 서류를 확인해 주세요", href: `/coordinator/applications/${reviewDoc.applicationId}` }
      : ext.needsPassFollowUp
        ? { text: "통화 · 상담 기록", title: `추천 공고를 연속 ${ext.consecutivePassCount ?? "여러"}회 패스했어요. 연락해 주세요` }
        : null;
  return (
    // 리뉴얼 .profile-header-card: 8px 모서리 · 22px 24px 여백 · 세로 간격 16px
    <section aria-label="수료생 요약" className="mb-5 flex flex-col gap-4 rounded-lg border border-(--coord-line) bg-(--coord-surface) px-6 py-[22px]">
      <div className="flex items-center justify-between gap-4">
        {/* 1차 피드백: 이름 아바타 없음 */}
        <div className="flex min-w-0 items-center">
          <div className="min-w-0">
            <h1 className="flex flex-wrap items-center gap-2.5 text-[22px] leading-[1.35] font-extrabold">
              {s.name}
              <Badge tone={manageTone[s.status]}>{manageLabels[s.status]}</Badge>
              {helpCount > 0 && <Badge tone="red">도움 요청 {helpCount}건</Badge>}
              {ext.needsPassFollowUp && <Badge tone="blue">연락 필요 · 연속 패스 {ext.consecutivePassCount ?? "—"}회</Badge>}
            </h1>
            <p className="mt-[3px] text-sm break-keep text-(--coord-muted)">
              {[courseName(s.courseLabel), s.desiredJob ?? "희망직무 미지정", s.subStatus && subStatusLabels[s.subStatus]].filter(Boolean).join(" · ")}
            </p>
          </div>
        </div>
        <button type="button" onClick={onAddLog} className={`${btn.base} ${btn.primary} shrink-0`}>
          통화·상담 기록 추가
        </button>
      </div>
      {/* 리뉴얼 .next-action-callout. 실제로 이동할 행동이 있을 때만 '다음 행동', 없으면 서버 상태 근거를 '현재 진행 상태'로 보여 준다 */}
      <div className="flex items-center justify-between gap-4 rounded-md border border-(--coord-green-line) bg-(--coord-tint) px-[18px] py-3.5">
        <div className="min-w-0">
          <b className="text-xs font-extrabold tracking-[0.5px] text-(--coord-green)">{next ? "NEXT ACTION · 다음 행동" : "현재 진행 상태"}</b>
          <h3 className="my-0.5 text-[16.5px] font-extrabold text-(--coord-forest)">{next ? next.title : (s.reason ?? manageLabels[s.status])}</h3>
          <p className="text-[13.5px] text-(--coord-green-ink)">
            {next && s.reason ? `${s.reason} · ` : ""}
            {s.lastResponseAt ? `${monthDay(s.lastResponseAt)} 앱 응답` : "앱 응답 기록 없음"}
            {ext.consecutivePassCount !== null && ext.consecutivePassCount > 0 && ` · 연속 패스 ${ext.consecutivePassCount}회`}
            {lastPass &&
              ` · 최근 패스 ${dateLabel(lastPass.respondedAt as string)}${lastPass.passReason ? ` (${passLabels[lastPass.passReason]})` : ""}`}
          </p>
        </div>
        {next &&
          (next.href ? (
            <Link href={next.href} className={`${btn.base} ${btn.primary} ${btn.sm} shrink-0`}>
              {next.text}
            </Link>
          ) : (
            <button type="button" onClick={onAddLog} className={`${btn.base} ${btn.primary} ${btn.sm} shrink-0`}>
              {next.text}
            </button>
          ))}
      </div>
    </section>
  );
}

/** 리뉴얼 .app-card + .app-card-header. 데이터가 없으면 가운데 안내 한 줄 */
function Card({ title, aside, empty, children }: { title: string; aside?: ReactNode; empty?: string | false; children?: ReactNode }) {
  return (
    <Panel label={title}>
      <PanelHead title={title}>{aside}</PanelHead>
      {empty ? <p className="p-6 text-center text-[15px] text-(--coord-muted)">{empty}</p> : children}
    </Panel>
  );
}

function Recommendations({ data }: { data: CoordinatorStudentDetail }) {
  const items = historyItems(data);
  return (
    // 1차 피드백: 제목 '응답이력' · 오른쪽 설명과 회사 첫 글자 아이콘 없음
    <Card title="응답이력" empty={items.length === 0 && "추천 공고 내역이 없어요."}>
      {/* 리뉴얼 .task-item-row. 이력이 길면 카드 안에서 스크롤 (약 6건 높이) */}
      <ul className="max-h-[460px] overflow-y-auto">
        {items.map((item) => (
          <li key={item.itemId} className="flex items-center justify-between gap-4 border-b border-(--coord-line) px-5 py-3.5 transition last:border-b-0 hover:bg-(--coord-hover)">
            <div className="flex min-w-0 flex-1 items-center">
              <div className="min-w-0">
                <h3 className="truncate text-[15px] font-extrabold" title={`${item.companyName} · ${item.title}`}>
                  {item.companyName} · {item.title}
                </h3>
                <p className="mt-[3px] truncate text-[13.5px] text-(--coord-muted)">
                  {dateLabel(item.recommendedAt)} 추천 ·{" "}
                  {item.passReason || item.passNote ? (
                    <span className="text-(--coord-red)">
                      {[item.passReason && passLabels[item.passReason], item.passNote && `비고: ${item.passNote}`].filter(Boolean).join(" · ")}
                    </span>
                  ) : item.respondedAt ? (
                    `${dateLabel(item.respondedAt)} 응답 완료`
                  ) : (
                    <span className="text-(--coord-red)">아직 응답하지 않았어요</span>
                  )}
                </p>
              </div>
            </div>
            <Badge tone={responseTone[item.response]}>{responseTagLabels[item.response]}</Badge>
          </li>
        ))}
      </ul>
    </Card>
  );
}

function Documents({ data }: { data: CoordinatorStudentDetail }) {
  const docs = historyItems(data).filter((i) => i.applicationId && i.applicationStatus);
  return (
    <Card title="진행 중인 지원서류" aside={<Badge tone="blue">{docs.length}건</Badge>} empty={docs.length === 0 && "제출된 지원서류가 없어요."}>
      {/* 리뉴얼: 서류 한 건 = 회색 상자 (공고 · 지원일 / 상태 배지 + 서류 보기) */}
      <ul className="flex max-h-[420px] flex-col gap-2.5 overflow-y-auto p-5">
        {docs.map((d) => (
          <li key={d.itemId} className="flex items-center justify-between gap-3 rounded-[4px] border border-(--coord-line) bg-(--coord-head) p-3.5">
            <div className="min-w-0">
              <b className="block truncate text-[15.5px]" title={`${d.companyName} · ${d.title}`}>
                {d.companyName} · {d.title}
              </b>
              <p className="mt-0.5 text-[13px] text-(--coord-muted)">{d.respondedAt ? `지원일: ${dateLabel(d.respondedAt)}` : "지원일 기록 없음"}</p>
            </div>
            <div className="flex shrink-0 items-center gap-2">
              {d.applicationStatus && <Badge tone={docTone[d.applicationStatus]}>{docLabels[d.applicationStatus]}</Badge>}
              <Link href={`/coordinator/applications/${d.applicationId}`} className={`${btn.base} ${btn.primary} ${btn.sm}`}>
                서류 보기
              </Link>
            </div>
          </li>
        ))}
      </ul>
    </Card>
  );
}

function HelpList({ help }: { help: DashboardHelpRequest[] }) {
  return (
    <Card
      title="도움 요청 이력"
      aside={<Badge tone={help.length > 0 ? "red" : "gray"}>{help.length > 0 ? `접수 ${help.length}건` : "접수 없음"}</Badge>}
      empty={help.length === 0 && "접수된 도움 요청이 없어요."}
    >
      {/* 리뉴얼: 접수된 요청 = 빨간 상자 (유형 · 공고 / 요청 원문 / 요청 상세 열기). 접수(OPEN)만 있다 */}
      <ul className="flex max-h-[420px] flex-col gap-2.5 overflow-y-auto p-5">
        {help.map((h) => (
          <li key={h.helpRequestId} className="flex items-center justify-between gap-3 rounded-[4px] border border-(--coord-red-line) bg-(--coord-red-soft) p-3.5">
            <div className="min-w-0">
              <b className="block truncate text-[15px] text-(--coord-red)">
                {helpTypeLabel(h.helpType)} · {helpTitle(h)}
              </b>
              <p className="mt-[3px] text-[13.5px] break-words text-(--coord-ink)">{h.memo ? `"${h.memo}"` : "남긴 메모가 없어요."}</p>
              <p className="mt-0.5 text-xs text-(--coord-muted)">
                {h.contactMethod ? `${contactLabels[h.contactMethod]} 선호` : "선호 연락 없음"} · {monthDayTime(h.createdAt)} 접수
              </p>
            </div>
            <Link href={`/coordinator/help-requests?id=${h.helpRequestId}`} className={`${btn.base} ${btn.danger} ${btn.sm} shrink-0`}>
              요청 상세 열기
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
    ["선호 연락", pref ? <b className="text-(--coord-green)">{contactLabels[pref]} (도움 요청에서 고름)</b> : null],
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
      {/* 리뉴얼: 한 줄에 이름(왼쪽) · 값(오른쪽), 줄 사이 옅은 선 */}
      <dl className="flex flex-col gap-3 p-5 text-[14.5px]">
        {rows.map(([label, value]) => (
          <div key={label} className="flex items-center justify-between gap-4 border-b border-(--coord-line-soft) pb-2 last:border-b-0 last:pb-0">
            <dt className="shrink-0 text-(--coord-muted)">{label}</dt>
            <dd className="min-w-0 text-right font-semibold break-all">{value || <span className="font-normal text-(--coord-faint)">기록 없음</span>}</dd>
          </div>
        ))}
      </dl>
    </Card>
  );
}

function ContactLogs({ data, onAdd }: { data: CoordinatorStudentDetail; onAdd: () => void }) {
  const s = data.student;
  return (
    <Card
      title="통화 · 상담 기록"
      aside={
        <button type="button" onClick={onAdd} className={`${btn.base} ${btn.outline} ${btn.sm}`}>
          기록 추가
        </button>
      }
    >
      {/* 리뉴얼 .crm-timeline-list: 왼쪽 세로선 + 점(초록 · 민트 테두리). 맨 아래는 수료일(실제 값) */}
      <div className="max-h-[440px] overflow-y-auto p-5">
        <ol className="relative flex flex-col pl-5 before:absolute before:top-1.5 before:bottom-1.5 before:left-[7px] before:w-0.5 before:bg-(--coord-line)">
          {data.contactLogs.length === 0 && (
            <li className="relative pb-5">
              <span aria-hidden="true" className="absolute top-1 -left-5 size-3 rounded-full border-2 border-(--coord-surface) bg-(--coord-chart-resting)" />
              <p className="text-[14.5px] text-(--coord-muted)">아직 남긴 비고나 상담 기록이 없어요.</p>
            </li>
          )}
          {data.contactLogs.map((log, index) => (
            <li key={`${log.contactLogId}-${index}`} className="relative pb-5">
              <span
                aria-hidden="true"
                className="absolute top-1 -left-5 size-3 rounded-full border-2 border-(--coord-surface) bg-(--coord-green) shadow-[0_0_0_2px_var(--coord-green-soft)]"
              />
              <span className="block text-[12.5px] font-bold text-(--coord-faint)">
                {monthDayTime(log.createdAt)} · {contactTypeLabels[log.contactType]}
              </span>
              <p className="mt-[3px] text-[14.5px] leading-[1.55] break-words whitespace-pre-wrap">{log.note}</p>
            </li>
          ))}
          <li className="relative">
            <span aria-hidden="true" className="absolute top-1 -left-5 size-3 rounded-full border-2 border-(--coord-surface) bg-(--coord-chart-resting)" />
            <span className="block text-[12.5px] font-bold text-(--coord-faint)">{dateLabel(s.completedOn)} · 수료</span>
            <p className="mt-[3px] text-[14.5px]">{courseName(s.courseLabel)} 수료</p>
          </li>
        </ol>
      </div>
    </Card>
  );
}

function Competency({ data }: { data: CoordinatorStudentDetail }) {
  const cp = data.competencyProfile;
  return (
    <Card title="역량 프로필" aside={<Badge tone="blue">AI 정리</Badge>} empty={!cp && "아직 생성된 역량 프로필이 없어요."}>
      {cp && (
        <div className="p-5">
          <p className="mb-2 text-[13.5px] text-(--coord-muted)">역할: {cp.roles.join(", ") || "추출된 역할이 없어요."}</p>
          {cp.skills.length > 0 && (
            <ul className="flex max-h-[120px] flex-wrap gap-1.5 overflow-y-auto" aria-label="역량 기술">
              {cp.skills.map((skill) => (
                <li key={skill.name}>
                  <Badge small>{skill.name}</Badge>
                </li>
              ))}
            </ul>
          )}
          {cp.achievements.length > 0 && (
            <details className="mt-3">
              <summary className="cursor-pointer text-[13.5px] font-bold text-(--coord-green)">성과 {cp.achievements.length}개 보기</summary>
              <ul className="mt-2 list-disc space-y-1.5 pl-5 text-sm">
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
      <dl className="grid grid-cols-2 gap-x-6 gap-y-2.5 p-5 text-[14.5px]">
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
