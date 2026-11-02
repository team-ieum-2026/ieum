"use client";
// 주인: FE-B — C4 서류 검토 · 승인 (FE-B-08, 11/1 개편 목업 documentPage()). API: docs/API.md 2-3.
// 승인 SUBMITTED → APPROVED, 수정 요청(=반려) { note } → REJECTED, 전달 완료 APPROVED → DELIVERED.
// 상태가 맞지 않으면 서버가 409 → 최신 상태를 다시 불러온다.
// 화면: 위(목록으로 · 제목 · 상태 · 5단계) / 가운데 왼쪽 '자기소개서 전체'(문서 형태, 문장 단위 근거 상태) · 오른쪽 '문장 근거'(각자 안에서 스크롤)
//       / 아래 검토 행동 막대(항상 보임). 이력서 9개 항목은 왼쪽 '이력서 항목' 탭으로 옮겼다.
// 목업의 AI 초안 원문 · 수료생 수정 이유 · 문장별 코디네이터 판단 저장은 API 에 없어서 만들지 않는다.
// document 가 null(GENERATING · FAILED · 본문 없음)이면 서류 · 근거 · 검증 요약 대신 안내를 보이고 승인 · 전달을 끈다.
import Link from "next/link";
import { useEffect, useId, useRef, useState } from "react";
import C4DocumentSummary, { userEditBreakdown } from "@/components/coordinator/C4DocumentSummary";
import { notifyReviewQueueChanged } from "@/components/coordinator/CoordinatorSidebar";
import { Badge, Icon, btn } from "@/components/coordinator/CoordUi";
import { docLabels, docTone, type CoordTone } from "@/components/coordinator/coordLabels";
import { IS_MOCK, MOCK_SAVE_NOTE } from "@/components/coordinator/demoAccount";
import { toDate } from "@/components/resume/resumeLabels";
import { ApiError } from "@/lib/api/client";
import {
  approveApplication,
  deliverApplication,
  getCoordinatorApplication,
  rejectApplication,
} from "@/lib/api/coordinatorReview";
import type { CoordinatorApplication } from "@/types/coordinator";
import type { ApplicationDocument, Sentence } from "@/types/resume";
import type { ApplicationStatus, CompanyInfoStatus } from "@/types/student";

type Action = "APPROVE" | "REJECT" | "DELIVER";

// 버튼 · 확인 창 문구는 실제로 하는 일만 말한다 (기업 전달 · 수료생 연락은 이음 밖에서 한다)
const ACTION: Record<Action, { button: string; title: string; description: string; confirm: string; done: string }> = {
  APPROVE: {
    button: "검토 승인",
    title: "이 서류를 승인할까요?",
    description: "검토 결과를 '승인'으로 기록해요. 기업 전달은 이음 밖에서 하고, 전달한 뒤 '전달 완료로 표시'를 눌러 주세요.",
    confirm: "검토 승인",
    done: "승인했어요. 기업에 전달한 뒤 '전달 완료로 표시'를 눌러 주세요.",
  },
  REJECT: {
    button: "수정 요청 기록",
    title: "수정 요청을 기록할까요?",
    description: "서류 상태가 '수정 요청'으로 바뀌어요. 이음이 수료생에게 자동으로 알리지 않으니 보완할 점을 직접 연락해 주세요. 아래 메모는 상담 기록에도 남아요.",
    confirm: "수정 요청 기록",
    done: "수정 요청을 기록했어요. 수료생에게 직접 연락해 보완할 점을 알려 주세요.",
  },
  DELIVER: {
    button: "전달 완료로 표시",
    title: "전달 완료로 표시할까요?",
    description: "기관 추천으로 기업에 전달했을 때 눌러 주세요. 이음에서 실제로 보내지는 않아요.",
    confirm: "전달 완료로 표시",
    done: "전달 완료로 표시했어요. 수료생 지원 현황에도 반영돼요.",
  },
};

/** 버튼이 켜지는 상태 (docs/API.md 2-3 상태 전이) */
const ALLOWED: Record<Action, ApplicationStatus> = { APPROVE: "SUBMITTED", REJECT: "SUBMITTED", DELIVER: "APPROVED" };

/** 진행 단계 (목업): AI 초안 → 수료생 확인 → 코디네이터 검토 → 승인 → 전달 완료 */
const STEPS = ["AI 초안", "수료생 확인", "코디네이터 검토", "승인", "전달 완료"];
const STAGE: Record<ApplicationStatus, number> = { GENERATING: 0, FAILED: 0, DRAFT: 1, REJECTED: 1, SUBMITTED: 2, APPROVED: 3, DELIVERED: 4 };

/** 아래 행동 막대 왼쪽 설명 (현재 서버 상태 기준) */
const STATUS_GUIDE: Record<ApplicationStatus, [string, string]> = {
  SUBMITTED: ["코디네이터 검토 단계예요", "자기소개서 전체와 문장 근거를 확인한 뒤 승인하거나 수정을 요청하세요."],
  APPROVED: ["승인 완료 · 기관 추천 전달 대기", "기업 전달은 이음 밖에서 하고, 전달한 뒤 상태만 기록해요."],
  REJECTED: ["수정 요청을 기록했어요", "반려 처리 후 서류 상태가 변경됩니다. 현재 수정·재제출 기능은 지원하지 않습니다."],
  DELIVERED: ["기관 추천 전달 완료", "이 서류의 검토 · 전달이 끝났어요."],
  DRAFT: ["수료생이 초안을 확인하고 있어요", "수료생이 검토를 요청하면 여기서 승인할 수 있어요."],
  GENERATING: ["서류를 만들고 있어요", "만들어지면 수료생이 확인한 뒤 검토를 요청해요."],
  FAILED: ["서류를 만들지 못했어요", "수료생이 서류를 다시 만들면 여기서 검토할 수 있어요."],
};

const COMPANY_INFO_LABEL: Record<CompanyInfoStatus, string> = {
  FOUND: "기업 정보 확인",
  PARTIAL: "기업 정보 일부만 확인",
  NOT_FOUND: "공고 외 정보 확인 불가",
};

/** 문장 근거 상태: AI+근거 = 근거 연결 · USER = 수료생 직접 작성 · AI+근거 없음 = 근거 확인 필요 */
type SentenceState = "linked" | "user" | "weak";
const sentenceState = (s: Sentence): SentenceState => (s.source === "USER" ? "user" : s.evidence.length > 0 ? "linked" : "weak");
// '근거 자료 연결'은 문장이 참고한 원본 기록이 있다는 뜻이지, 문장의 표현 · 해석까지 모두 검증됐다는 뜻이 아니다
const STATE_BADGE: Record<SentenceState, [string, CoordTone]> = {
  linked: ["근거 자료 연결", "green"],
  user: ["수료생 직접 작성", "blue"],
  weak: ["근거 확인 필요", "red"],
};
/** 선택한 문장 위 제목: 누가 쓴 문장인지 */
const STATE_WRITER: Record<SentenceState, string> = { linked: "AI가 작성한 문장", user: "수료생이 쓰거나 고친 문장", weak: "AI가 작성한 문장" };
const STATE_CHECK: Record<SentenceState, string> = {
  linked:
    "AI가 위 원본 기록을 바탕으로 쓴 문장이에요. 원본에 있는 사실과, AI가 덧붙인 해석 · 표현(성과 수치 · 역할 범위 · 평가)이 같은 범위인지 비교해 확인해 주세요. 근거 연결은 사실 전체가 검증됐다는 뜻이 아니에요.",
  user: "수료생이 직접 쓰거나 고친 문장이라 AI 근거 점검 대상이 아니에요. 필요하면 원본 자료와 함께 확인해 주세요.",
  weak: "연결할 자료를 찾지 못했어요. 거짓으로 확정하지 않고 담당자 확인 대상으로 남겼어요.",
};
/** 선택한 문장을 왼쪽 · 오른쪽에서 같은 상태 색으로 잇는 표시 (번호 원 · 왼쪽 테두리 · 외곽선) */
const MARK_BG: Record<SentenceState, string> = { linked: "bg-(--coord-green)", user: "bg-(--coord-blue)", weak: "bg-(--coord-red)" };
const QUOTE_LOOK: Record<SentenceState, string> = {
  linked: "border-(--coord-green) bg-(--coord-green-soft)",
  user: "border-(--coord-blue) bg-(--coord-blue-soft)",
  weak: "border-(--coord-red) bg-(--coord-red-soft)",
};
const SELECTED_LOOK: Record<SentenceState, string> = {
  linked: "outline-(--coord-green) bg-(--coord-green-soft)",
  user: "outline-(--coord-blue)",
  weak: "outline-(--coord-red)",
};

/** 자기소개서 문장 (없으면 빈 배열) */
const coverSentences = (document: ApplicationDocument | null): Sentence[] => {
  const cover = document?.sections.find((s) => s.section === "COVER_LETTER");
  return cover?.section === "COVER_LETTER" ? cover.sentences : [];
};

export default function ApplicationReviewScreen({ applicationId }: { applicationId: number }) {
  const [detail, setDetail] = useState<CoordinatorApplication | null>(null);
  // notFound(404)면 다시 불러와도 같으므로 "다시 시도"를 숨긴다
  const [loadError, setLoadError] = useState<{ message: string; notFound: boolean } | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [tab, setTab] = useState<"cover" | "resume">("cover");
  const [confirming, setConfirming] = useState<Action | null>(null);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [doneMessage, setDoneMessage] = useState<string | null>(null);
  // 화면이 다시 그려지기 전 두 번 눌러도 요청이 한 번만 가도록
  const busyRef = useRef(false);

  useEffect(() => {
    let ignore = false;
    getCoordinatorApplication(applicationId)
      .then((data) => {
        if (!ignore) setDetail(data);
      })
      .catch((e: unknown) => {
        if (ignore) return;
        setLoadError(
          e instanceof ApiError
            ? { message: e.message, notFound: e.status === 404 }
            : { message: "서류를 불러오지 못했어요.", notFound: false },
        );
      });
    return () => {
      ignore = true;
    };
  }, [applicationId, reloadKey]);

  if (!detail) {
    return (
      <div className="flex min-h-[50vh] flex-col items-center justify-center gap-3 text-sm text-(--coord-muted)">
        <p role={loadError ? "alert" : "status"}>{loadError?.message ?? "서류를 불러오는 중이에요…"}</p>
        {loadError?.notFound && <p className="text-xs">주소의 지원서 번호를 확인하거나 서류 검토 목록에서 다시 골라 주세요.</p>}
        {loadError && (
          <div className="flex gap-4">
            {!loadError.notFound && (
              <button
                type="button"
                onClick={() => {
                  setLoadError(null);
                  setReloadKey((k) => k + 1);
                }}
                className="font-bold text-(--coord-green)"
              >
                다시 시도
              </button>
            )}
            <Link href="/coordinator/applications" className="font-bold text-(--coord-green)">
              서류 검토 목록으로
            </Link>
          </div>
        )}
      </div>
    );
  }

  const { status, student, job, verification } = detail;
  // GENERATING · FAILED 이거나 본문이 없으면 null (docs/API.md 2-2). 형식이 깨진 서류도 빈 서류로 보고 멈추지 않게 (M6 와 같은 방식)
  const document = detail.document && Array.isArray(detail.document.sections) ? detail.document : null;
  const sentences = coverSentences(document);
  // 처음엔 첫 문장의 근거를 보여 준다 (목업처럼 오른쪽 패널이 비어 있지 않게)
  const selectedIndex = Math.max(0, sentences.findIndex((s) => s.sentenceId === selectedId));
  const selected = sentences[selectedIndex] ?? null;
  const counts = { linked: 0, user: 0, weak: 0 };
  sentences.forEach((s) => (counts[sentenceState(s)] += 1));

  const closeConfirm = () => {
    if (busy) return;
    setConfirming(null);
    setActionError(null);
  };

  const run = async (action: Action) => {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setActionError(null);
    try {
      const result =
        action === "APPROVE"
          ? await approveApplication(applicationId)
          : action === "REJECT"
            ? await rejectApplication(applicationId, { note: note.trim() })
            : await deliverApplication(applicationId);
      setDetail((prev) => (prev ? { ...prev, status: result.status } : prev));
      // 사이드바가 대시보드 API 로 대기 건수를 다시 센다
      notifyReviewQueueChanged();
      setDoneMessage(ACTION[action].done);
      setConfirming(null);
      setNote("");
    } catch (e: unknown) {
      // 서버 message 는 화면에 그대로 띄워도 되는 문장 (docs/API.md 1장)
      setActionError(e instanceof ApiError ? e.message : "처리하지 못했어요. 다시 시도해 주세요.");
      // 그사이 다른 처리로 상태가 바뀌었으면(409) 최신 상태로 버튼을 맞춘다
      if (e instanceof ApiError && e.status === 409) {
        setReloadKey((k) => k + 1);
        notifyReviewQueueChanged();
      }
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };

  const hasAi = verification.aiSentenceCount > 0;
  const allAiLinked = verification.aiSentenceWithEvidence === verification.aiSentenceCount;
  // AI 문장이 0개면 "모두 근거 연결"이 아니라 확인할 문장이 없다고 알린다
  const aiValue = !hasAi
    ? "없음"
    : `${verification.aiSentenceCount}개 · ${allAiLinked ? "모두 근거 연결" : `근거 연결 ${verification.aiSentenceWithEvidence}개`}`;
  // 종류별 합이 서버의 userEditedCount 와 같을 때만 나눠 보여 준다 (다르면 서버 총합만)
  const edits = document ? userEditBreakdown(document) : [];
  const editsTotal = edits.reduce((sum, e) => sum + e.count, 0);
  const editedValue =
    edits.length > 0 && editsTotal === verification.userEditedCount
      ? edits.map((e) => `${e.label} ${e.count}`).join(" · ")
      : `${verification.userEditedCount}개`;
  const [guideTitle, guideText] = STATUS_GUIDE[status];

  return (
    // 위 머리 · 가운데 두 패널(내용만큼 높이, 길면 화면 높이 안에서 각자 스크롤) · 아래 행동 막대(화면 아래에 붙어 늘 보임)
    // 서류가 짧아도 큰 빈 카드가 생기지 않게 두 패널은 늘이지 않는다
    <div className="flex flex-col">
      <header className="shrink-0">
        <Link href="/coordinator/applications" className="mb-[18px] inline-flex w-fit items-center gap-[7px] text-xs text-(--coord-muted) hover:text-(--coord-green)">
          <Icon name="back" className="size-4" /> 서류 검토 목록
        </Link>
        <div className="flex items-start justify-between gap-4">
          <div>
            <h1 className="text-[27px] leading-[1.3] font-bold tracking-[-1px]">{student.name} 님의 지원서류</h1>
            {/* C4 API 에 제출 시각 · 버전 · 마감일이 없어서 날짜는 표시하지 않는다 */}
            <p className="mt-2 text-[13px] text-(--coord-muted)">
              {job.companyName} · {job.title}
            </p>
          </div>
          <div className="flex items-center gap-3">
            <Badge tone={docTone[status]}>{docLabels[status]}</Badge>
            <Link href={`/coordinator/students/${student.studentId}`} className={`${btn.base} ${btn.outline}`}>
              수료생 정보
            </Link>
          </div>
        </div>
        <ol className="my-5 flex items-center gap-[5px]" aria-label="서류 진행 단계">
          {STEPS.map((label, i) => {
            const stage = STAGE[status];
            return (
              <li
                key={label}
                aria-current={i === stage ? "step" : undefined}
                className={`flex flex-1 items-center gap-[7px] rounded-[7px] px-[11px] py-[9px] text-[11px] ${
                  i === stage
                    ? "bg-(--coord-forest) text-(--coord-surface)"
                    : i < stage
                      ? "bg-(--coord-green-soft) text-(--coord-green)"
                      : "bg-(--coord-gray-soft) text-(--coord-muted)"
                }`}
              >
                <span className="grid size-[18px] place-items-center rounded-full border border-current text-[10px]">{i < stage ? "✓" : i + 1}</span>
                {label}
              </li>
            );
          })}
        </ol>
      </header>

      <div className="grid grid-cols-[minmax(0,1.45fr)_minmax(300px,1fr)] items-start gap-5">
        {/* 왼쪽: 자기소개서 전체 (이력서 항목은 탭) */}
        <section
          aria-label="지원서류"
          className="flex max-h-[calc(100vh-395px)] min-h-[300px] flex-col overflow-hidden rounded-xl border border-(--coord-line) bg-(--coord-surface)"
        >
          <div className="flex shrink-0 items-center justify-between gap-3 border-b border-(--coord-line-soft) px-[22px] py-4">
            <div>
              <h2 className="text-[15px] font-bold">{tab === "cover" ? "자기소개서 전체" : "이력서 항목"}</h2>
              <p className="mt-1 text-[11px] text-(--coord-muted)">
                {tab === "cover" ? "문장을 누르면 오른쪽에서 근거와 출처를 확인해요." : "수료생 등록 자료와 AI가 정리한 이력서 항목이에요."}
              </p>
            </div>
            {document && (
              <div role="tablist" aria-label="서류 보기" className="flex gap-1">
                {(
                  [
                    ["cover", "자기소개서"],
                    ["resume", "이력서 항목"],
                  ] as const
                ).map(([key, label]) => (
                  <button
                    key={key}
                    type="button"
                    role="tab"
                    aria-selected={tab === key}
                    onClick={() => setTab(key)}
                    className={`rounded-[7px] px-3.5 py-2 text-xs focus-visible:outline-3 focus-visible:outline-(--coord-green) ${
                      tab === key ? "bg-(--coord-green-soft) font-semibold text-(--coord-forest)" : "text-(--coord-muted) hover:bg-(--coord-hover)"
                    }`}
                  >
                    {label}
                  </button>
                ))}
              </div>
            )}
          </div>
          {!document ? (
            <NoDocument status={status} onReload={() => setReloadKey((k) => k + 1)} />
          ) : tab === "resume" ? (
            <div className="min-h-0 flex-1 overflow-y-auto px-7 py-6">
              <C4DocumentSummary document={document} selectedSentenceId={null} onSelectSentence={() => {}} showCover={false} />
            </div>
          ) : (
            <>
              <div className="flex shrink-0 flex-wrap items-center gap-[9px] border-b border-(--coord-line) px-5 py-3">
                <Badge tone="green">근거 자료 연결 {counts.linked}</Badge>
                <Badge tone="blue">수료생 직접 작성 {counts.user}</Badge>
                {counts.weak > 0 && <Badge tone="red">근거 확인 필요 {counts.weak}</Badge>}
                <span className="text-xs text-(--coord-muted)">문장 색상 + 상태로 구분</span>
              </div>
              <article className="min-h-0 flex-1 overflow-y-auto px-[34px] py-[30px]">
                <span className="text-[10px] tracking-[2px] text-(--coord-muted)">APPLICATION LETTER · {job.companyName}</span>
                <h2 className="mt-1.5 mb-2 text-2xl font-bold">자기소개서</h2>
                <p className="text-xs text-(--coord-muted)">
                  {student.name}　|　{job.title} 지원
                </p>
                <div className="my-[22px] border-t-2 border-(--coord-forest)" />
                {document.companyInfoStatus !== "FOUND" && (
                  <p className="mb-5 rounded-lg border border-(--coord-attention-line) bg-(--coord-attention-soft) px-3.5 py-2.5 text-xs leading-[1.7] text-(--coord-attention)">
                    <b>{COMPANY_INFO_LABEL[document.companyInfoStatus]}</b>
                    {document.companyInfoNote && ` · ${document.companyInfoNote}`}
                  </p>
                )}
                {sentences.length === 0 ? (
                  <p className="text-[13px] text-(--coord-muted)">이 서류에는 자기소개서 문장이 없어요. 이력서 항목 탭에서 나머지 내용을 확인해 주세요.</p>
                ) : (
                  <p className="text-sm leading-[2.1] break-keep">
                    {sentences.map((s, i) => (
                      <CoverSentence key={s.sentenceId} sentence={s} index={i} selected={i === selectedIndex} onSelect={() => setSelectedId(s.sentenceId)} />
                    ))}
                  </p>
                )}
                {verification.excludedSentenceCount > 0 && (
                  <p className="mt-6 rounded-lg bg-(--coord-head) px-3.5 py-2.5 text-xs text-(--coord-muted)">
                    AI가 근거를 찾지 못해 초안에서 제외한 문장 {verification.excludedSentenceCount}개
                  </p>
                )}
              </article>
            </>
          )}
        </section>

        {/* 오른쪽: 선택한 문장의 근거 + 문서 전체 검증 요약 */}
        <aside aria-label="문장 근거와 검증 요약" className="sticky top-[76px] flex max-h-[calc(100vh-395px)] flex-col gap-3 overflow-y-auto pr-1">
          <section aria-label="문장 근거" className="shrink-0 overflow-hidden rounded-xl border border-(--coord-line) bg-(--coord-surface)">
            <div className="flex items-center justify-between gap-3 border-b border-(--coord-line-soft) px-[22px] py-4">
              <h2 className="text-[15px] font-bold">문장 근거 · 출처</h2>
              {/* 왼쪽 문장과 같은 번호 · 이전/다음으로 문장을 옮기면 왼쪽 표시도 함께 바뀐다 */}
              {selected && (
                <div className="flex items-center gap-1 text-xs">
                  <button
                    type="button"
                    aria-label="이전 문장"
                    disabled={selectedIndex <= 0}
                    onClick={() => setSelectedId(sentences[selectedIndex - 1].sentenceId)}
                    className="grid size-7 place-items-center rounded-md border border-(--coord-line) hover:bg-(--coord-hover) disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    ‹
                  </button>
                  <span className="min-w-[64px] text-center font-semibold">
                    문장 {selectedIndex + 1} / {sentences.length}
                  </span>
                  <button
                    type="button"
                    aria-label="다음 문장"
                    disabled={selectedIndex >= sentences.length - 1}
                    onClick={() => setSelectedId(sentences[selectedIndex + 1].sentenceId)}
                    className="grid size-7 place-items-center rounded-md border border-(--coord-line) hover:bg-(--coord-hover) disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    ›
                  </button>
                </div>
              )}
            </div>
            <div className="p-[22px]" aria-live="polite">
              {!document ? (
                <p className="text-[13px] text-(--coord-muted)">서류가 없어 확인할 근거가 없어요.</p>
              ) : !selected ? (
                <p className="text-[13px] text-(--coord-muted)">자기소개서 문장이 없어 확인할 근거가 없어요.</p>
              ) : (
                <SentenceEvidence sentence={selected} index={selectedIndex} />
              )}
            </div>
          </section>

          <section aria-label="문서 전체 검증 요약" className="shrink-0 rounded-lg border border-(--coord-tint-line) bg-(--coord-tint) px-4 py-3.5">
            <h2 className="text-[13px] font-bold">이 문서에서 확인할 것</h2>
            {/* 서류가 없으면 서버 요약이 0개 · NOT_FOUND 로 오므로 "확인 불가"처럼 보이지 않게 숫자 대신 안내만 */}
            {!document ? (
              <p className="mt-2 text-xs text-(--coord-muted)">서류가 없어 검증할 내용이 없어요.</p>
            ) : (
              <dl className="mt-2 flex flex-col gap-1.5 text-xs">
                <SummaryRow label="AI 문장" value={aiValue} note={hasAi ? null : "AI가 쓴 문장이 없어 확인할 근거가 없어요."} warn={hasAi && !allAiLinked} />
                <SummaryRow label="생성 단계에서 제외된 문장" value={`${verification.excludedSentenceCount}개`} />
                <SummaryRow label="수료생 직접 수정" value={editedValue} warn={verification.userEditedCount > 0} />
                <SummaryRow
                  label="기업 정보"
                  value={COMPANY_INFO_LABEL[verification.companyInfoStatus]}
                  note={verification.companyInfoStatus !== "FOUND" ? document.companyInfoNote : null}
                  warn={verification.companyInfoStatus === "NOT_FOUND"}
                />
              </dl>
            )}
            <p className="mt-2.5 text-[11px] text-(--coord-muted)">AI 점검은 원본 자료와의 연결 여부예요. 사실의 진위와 추천 여부는 코디네이터가 최종 판단해요.</p>
          </section>
        </aside>
      </div>

      {/* 아래 검토 행동 막대: 현재 서버 상태에 맞는 버튼만 */}
      <div className="sticky bottom-3 z-[2] mt-4 flex items-center justify-between gap-3 rounded-[10px] border border-(--coord-line) bg-(--coord-surface) px-[18px] py-3.5 shadow-[0_-5px_20px_rgb(0_0_0/0.06)]">
        {doneMessage ? (
          <div role="status">
            <b className="text-[13px] text-(--coord-green-ink)">{doneMessage}</b>
            {IS_MOCK && <span className="ml-2 text-xs font-semibold text-(--coord-attention)">{MOCK_SAVE_NOTE}</span>}
            <Link href="/coordinator/applications" className="mt-0.5 block text-[11px] text-(--coord-muted) underline underline-offset-2">
              서류 검토 목록으로 돌아가기
            </Link>
          </div>
        ) : (
          <div>
            <b className="text-[13px]">
              {!document && status === "SUBMITTED" ? "서류 본문이 없어 승인할 수 없어요" : counts.weak > 0 && status === "SUBMITTED" ? `근거 확인이 필요한 문장 ${counts.weak}개` : guideTitle}
            </b>
            <p className="mt-0.5 text-[11px] text-(--coord-muted)">
              {!document && status === "SUBMITTED" ? "수정 요청을 기록하고 수료생에게 연락해 주세요." : guideText}
            </p>
          </div>
        )}
        <div className="flex items-center gap-2">
          {status === ALLOWED.REJECT && (
            <button type="button" onClick={() => setConfirming("REJECT")} className={`${btn.base} ${btn.danger} px-4 py-2.5 text-sm`}>
              {ACTION.REJECT.button}
            </button>
          )}
          {status === ALLOWED.APPROVE && (
            // 서버는 상태만 보지만, 빈 서류를 승인하지 않도록 서류가 있을 때만 켠다
            <button type="button" disabled={!document} onClick={() => setConfirming("APPROVE")} className={`${btn.base} ${btn.primary} px-4 py-2.5 text-sm`}>
              {ACTION.APPROVE.button}
            </button>
          )}
          {status === ALLOWED.DELIVER && (
            <button type="button" disabled={!document} onClick={() => setConfirming("DELIVER")} className={`${btn.base} ${btn.primary} px-4 py-2.5 text-sm`}>
              {ACTION.DELIVER.button}
            </button>
          )}
          {status !== "SUBMITTED" && status !== "APPROVED" && <Badge tone={docTone[status]}>{docLabels[status]}</Badge>}
        </div>
      </div>

      {confirming && (
        <ConfirmDialog
          title={ACTION[confirming].title}
          description={ACTION[confirming].description}
          confirmLabel={busy ? "처리 중…" : ACTION[confirming].confirm}
          danger={confirming === "REJECT"}
          busy={busy}
          confirmDisabled={confirming === "REJECT" && note.trim() === ""}
          error={actionError}
          onConfirm={() => run(confirming)}
          onClose={closeConfirm}
        >
          {confirming === "REJECT" && (
            <label className="flex flex-col gap-1 text-xs text-(--coord-muted)">
              보완할 점 (필수)
              <textarea
                value={note}
                onChange={(e) => setNote(e.target.value)}
                rows={4}
                placeholder="예: 자기소개서 2문단의 수치 근거를 README와 맞춰 주세요"
                className="resize-none rounded-[10px] border-[1.5px] border-(--coord-line) p-3 text-sm text-(--coord-ink) focus-visible:outline-2 focus-visible:outline-(--coord-green)"
              />
            </label>
          )}
        </ConfirmDialog>
      )}
    </div>
  );
}

/** 자기소개서 한 문장 (목업 .sentence): 근거 연결 · 수료생 직접 작성(파랑) · 근거 확인 필요(빨강 점선) */
function CoverSentence({ sentence, index, selected, onSelect }: { sentence: Sentence; index: number; selected: boolean; onSelect: () => void }) {
  const state = sentenceState(sentence);
  const look =
    state === "user"
      ? "bg-(--coord-blue-soft) text-(--coord-blue) border-b border-(--coord-blue)"
      : state === "weak"
        ? "bg-(--coord-red-soft) text-(--coord-red) border-b border-dashed border-(--coord-red)"
        : "hover:bg-(--coord-tint) hover:shadow-[0_0_0_1px_var(--coord-tint-line)]";
  return (
    <>
      <button
        type="button"
        onClick={onSelect}
        aria-pressed={selected}
        aria-label={`문장 ${index + 1}: ${STATE_BADGE[state][0]}`}
        className={`inline rounded-[3px] px-[3px] py-0.5 text-left leading-[inherit] whitespace-normal ${look} ${
          selected ? `outline-2 outline-offset-2 ${SELECTED_LOOK[state]}` : ""
        }`}
      >
        {/* 선택한 문장 앞에 오른쪽 패널과 같은 번호를 붙여 어느 문장의 근거인지 바로 보이게 */}
        {selected && (
          <span aria-hidden className={`mr-1 inline-grid size-[18px] place-items-center rounded-full align-[1px] text-[10px] font-bold text-white ${MARK_BG[state]}`}>
            {index + 1}
          </span>
        )}
        {sentence.text}
      </button>{" "}
    </>
  );
}

/** 오른쪽 '문장 근거 · 출처' 본문 (목업 .evidence-block). 원본 근거 · 출처 → AI 점검 결과 */
function SentenceEvidence({ sentence, index }: { sentence: Sentence; index: number }) {
  const state = sentenceState(sentence);
  const [label, tone] = STATE_BADGE[state];
  return (
    <>
      {/* 선택한 문장: 왼쪽 문장과 같은 번호 · 같은 상태 색 띠로 연결 */}
      <div className={`rounded-r-lg border-l-4 px-3.5 py-3 ${QUOTE_LOOK[state]}`}>
        <div className="flex items-center gap-2">
          <span aria-hidden className={`grid size-[18px] place-items-center rounded-full text-[10px] font-bold text-white ${MARK_BG[state]}`}>
            {index + 1}
          </span>
          <Badge tone={tone}>{label}</Badge>
          <span className="text-[11px] text-(--coord-muted)">{STATE_WRITER[state]}</span>
        </div>
        <p className="mt-2 text-[13px] leading-[1.8] font-semibold text-(--coord-ink)">{sentence.text}</p>
      </div>
      <div className="border-b border-(--coord-line) py-[15px]">
        <h3 className="mb-[7px] text-[11px] text-(--coord-muted)">01　원본 기록 · 출처 (등록 자료 · 기업 정보 원문)</h3>
        {sentence.evidence.length === 0 ? (
          <p className="text-xs leading-[1.8] text-(--coord-muted)">
            {state === "user" ? "직접 입력한 문장이라 연결된 근거가 없어요." : "연결된 근거가 없어요."}
          </p>
        ) : (
          <ol className="flex flex-col gap-3">
            {sentence.evidence.map((ev, i) => (
              <li key={`${ev.refType}-${ev.refId}-${i}`}>
                <p className="text-xs font-semibold">{ev.label}</p>
                <blockquote className="mt-2 border-l-3 border-(--coord-chart-total) bg-(--coord-tint) px-3 py-2.5 text-xs leading-[1.8]">{ev.quote}</blockquote>
                {(ev.url || ev.collectedAt) && (
                  <p className="mt-1 flex flex-wrap gap-x-2 text-[11px] text-(--coord-muted)">
                    {ev.url && (
                      <a href={ev.url} target="_blank" rel="noreferrer" className="underline underline-offset-2">
                        출처 열기
                      </a>
                    )}
                    {ev.collectedAt && <span>{toDate(ev.collectedAt)} 수집</span>}
                  </p>
                )}
              </li>
            ))}
          </ol>
        )}
      </div>
      <div className="pt-[15px]">
        <h3 className="mb-[7px] text-[11px] text-(--coord-muted)">02　AI 점검 결과 · 확인할 점</h3>
        <p className="text-xs leading-[1.8]">{STATE_CHECK[state]}</p>
      </div>
    </>
  );
}

/** 서류가 없을 때 왼쪽 안내. GENERATING · FAILED 는 상태대로, 그 밖은 본문이 저장되지 않은 경우 */
function NoDocument({ status, onReload }: { status: ApplicationStatus; onReload: () => void }) {
  const [title, description] =
    status === "GENERATING"
      ? ["서류를 만들고 있어요.", "AI가 서류를 만드는 데 20~40초쯤 걸려요. 잠시 뒤 다시 불러와 주세요."]
      : status === "FAILED"
        ? ["서류를 만들지 못했어요.", "수료생이 서류를 다시 만들면 여기서 검토할 수 있어요."]
        : ["서류 본문이 없어요.", "지원서는 있지만 저장된 서류가 없어요. 필요하면 수료생에게 연락해 주세요."];
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-2 px-7 py-10 text-center">
      <p className="text-[15px] font-bold">{title}</p>
      <p className="text-[13px] text-(--coord-muted)">{description}</p>
      {status === "GENERATING" && (
        <button type="button" onClick={onReload} className="mt-2 text-sm font-bold text-(--coord-green)">
          다시 불러오기
        </button>
      )}
    </div>
  );
}

function SummaryRow({ label, value, note = null, warn = false }: { label: string; value: string; note?: string | null; warn?: boolean }) {
  return (
    <div className="flex flex-col gap-0.5 border-b border-(--coord-tint-line) pb-1.5 last:border-b-0 last:pb-0">
      <div className="flex justify-between gap-2">
        <dt className="text-(--coord-muted)">{label}</dt>
        <dd className={`text-right font-bold ${warn ? "text-(--coord-orange)" : ""}`}>{value}</dd>
      </div>
      {note && <dd className="text-[11px] text-(--coord-muted)">{note}</dd>}
    </div>
  );
}

/** 가운데 확인 창 (데스크톱). 배경 · Esc 로 닫힌다 */
function ConfirmDialog({
  title,
  description,
  confirmLabel,
  danger,
  busy,
  confirmDisabled,
  error,
  onConfirm,
  onClose,
  children,
}: {
  title: string;
  description: string;
  confirmLabel: string;
  danger: boolean;
  busy: boolean;
  confirmDisabled: boolean;
  error: string | null;
  onConfirm: () => void;
  onClose: () => void;
  children?: React.ReactNode;
}) {
  const titleId = useId();
  const panelRef = useRef<HTMLDivElement>(null);
  // 처음 열릴 때만 창으로 포커스 (입력 중에 다시 옮기지 않도록 onClose 와 분리)
  useEffect(() => {
    panelRef.current?.focus();
  }, []);
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-30 flex items-center justify-center bg-(--coord-forest)/40 backdrop-blur-[3px]" onClick={onClose}>
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onClick={(e) => e.stopPropagation()}
        className="flex w-[480px] flex-col gap-3 rounded-[14px] border border-(--coord-line) bg-(--coord-surface) p-6 outline-none"
      >
        <h2 id={titleId} className="text-[19px] font-bold">
          {title}
        </h2>
        <p className="text-[13px] leading-[1.8] text-(--coord-muted)">{description}</p>
        {children}
        {error && (
          <p role="alert" className="text-[13px] text-(--coord-red)">
            {error}
          </p>
        )}
        <div className="mt-2 flex gap-2">
          <button type="button" onClick={onClose} disabled={busy} className={`${btn.base} ${btn.outline} flex-1 py-3 text-sm`}>
            취소
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={busy || confirmDisabled}
            aria-busy={busy}
            className={`${btn.base} ${danger ? "border-(--coord-red) bg-(--coord-red) text-(--coord-surface)" : btn.primary} flex-1 py-3 text-sm`}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
