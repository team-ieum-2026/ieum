"use client";
// 주인: FE-B — C4 서류 검토 · 승인 (FE-B-08, 11/1 개편 목업 documentPage()). API: docs/API.md 2-3.
// 승인 SUBMITTED → APPROVED, 보완 필요(=반려) { note } → REJECTED, 전달 완료 APPROVED → DELIVERED.
// REJECTED 는 이번 버전에서 끝 상태다 (수료생 수정 · 재제출 · 승인 불가, 코디네이터가 이음 밖에서 연락).
// 상태가 맞지 않으면 서버가 409 → 최신 상태를 다시 불러온다.
// 화면 (11/3 리뉴얼 design-reference documentPage + 1차 피드백): 위(목록으로 · 제목 + 상태 · 5단계 막대) / 왼쪽 '자기소개서 전체'(페이지와 함께 스크롤)
//       · 오른쪽 '문장 근거 · 출처'(긴 서류를 스크롤해도 화면에 고정). 이력서 9개 항목은 왼쪽 '이력서 항목' 탭.
// 1차 피드백: 아래 검토 행동 막대를 없애고 버튼만 오른쪽 위로 옮김. '보완 필요로 처리' = 반려 API (예전 '첨삭 제안' · '수정 요청 기록'). 선택한 문장은 왼쪽 · 오른쪽 모두 하늘색 강조.
// 2차 피드백: 검토 버튼은 두 카드 위 한 줄(오른쪽)로 빼서 두 카드가 같은 선에서 시작 · '이 문서에서 확인할 것' 요약 삭제.
// 3차 피드백: 제목 줄에 수료생 정보 · 카드 제목 '자기소개서 | 이름 | 공고명' · 문장 번호 원과 상태 색 삭제(처음엔 선택 없음, 다시 누르면 해제)
//       · 작성 주체(AI / 수료생 작성 · 수정)는 고른 문장의 오른쪽 패널에만, 근거 검증 상태와 따로.
// 리뉴얼 HTML 의 문장별 '확인 후 유지 · 수정 요청에 포함', 마감일, 소제목 문단은 API 에 없어서 만들지 않는다.
// 목업의 AI 초안 원문 · 수료생 수정 이유 · 문장별 코디네이터 판단 저장도 API 에 없어서 만들지 않는다.
// document 가 null(GENERATING · FAILED · 본문 없음)이면 서류 · 근거 · 검증 요약 대신 안내를 보이고 승인 · 전달을 끈다.
import Link from "next/link";
import { useEffect, useId, useRef, useState } from "react";
import C4DocumentSummary from "@/components/coordinator/C4DocumentSummary";
import { notifyReviewQueueChanged } from "@/components/coordinator/CoordinatorSidebar";
import { Badge, Icon, backLink, btn } from "@/components/coordinator/CoordUi";
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
    button: "보완 필요로 처리",
    title: "보완 필요 처리",
    description: "보완이 필요한 내용을 기록해 주세요. 처리 후에는 수료생에게 별도로 연락하여 안내해야 합니다. 현재 시스템에서는 해당 서류의 수정·재제출을 지원하지 않습니다.",
    confirm: "보완 필요로 처리",
    done: "보완 필요로 처리했어요. 수료생에게 별도로 연락해 보완 사항을 안내해 주세요.",
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
const STEPS = ["AI 초안", "수료생 확인", "매니저 검토", "승인", "전달 완료"];
/**
 * 상태별 단계: done = 완료(✓)한 단계 수, current = 지금 단계(없으면 null).
 * REJECTED 는 ②로 되돌리지 않고 ③ 코디네이터 검토에서 '보완 필요'로 끝난 것으로 보인다 (④ · ⑤ 미완료).
 * APPROVED 는 ④까지 완료 · ⑤ 미완료, DELIVERED 는 모두 완료
 */
const STAGE: Record<ApplicationStatus, { done: number; current: number | null }> = {
  GENERATING: { done: 0, current: 0 },
  FAILED: { done: 0, current: 0 },
  DRAFT: { done: 1, current: 1 },
  SUBMITTED: { done: 2, current: 2 },
  REJECTED: { done: 2, current: 2 },
  APPROVED: { done: 4, current: null },
  DELIVERED: { done: 5, current: null },
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
/**
 * 작성 주체 (3차 피드백, 근거 검증 상태와 별개). API 의 문장 source 만 본다: AI = AI 작성, USER = 수료생이 쓰거나 고친 문장.
 * 수료생이 AI 문장을 고치면 서버가 source 를 USER 로 바꾸고 원래 문장을 남기지 않아(docs/API.md 2-2) '직접 작성'과 '수정'은 나눌 수 없다.
 */
const WRITER: Partial<Record<Sentence["source"], [string, CoordTone]>> = {
  AI: ["AI 작성", "green"],
  USER: ["수료생 작성 · 수정", "blue"],
};
const STATE_CHECK: Record<SentenceState, string> = {
  linked:
    "AI가 위 원본 기록을 바탕으로 쓴 문장이에요. 원본에 있는 사실과, AI가 덧붙인 해석 · 표현(성과 수치 · 역할 범위 · 평가)이 같은 범위인지 비교해 확인해 주세요. 근거 연결은 사실 전체가 검증됐다는 뜻이 아니에요.",
  user: "수료생이 직접 쓰거나 고친 문장이라 연결된 근거 자료가 없어요. 필요하면 원본 자료와 함께 확인해 주세요.",
  weak: "연결된 근거 자료가 없는 AI 문장이에요. 사실이 아니라는 뜻은 아니니, 원본 자료와 함께 직접 확인해 주세요.",
};
/** 평소엔 모든 문장이 투명하고, 선택한 문장만 하늘색 배경 + 1px 연파랑 실선 (근거 확인 필요 등 경고 빨강과 구분) */
const SELECTED_LOOK = "bg-(--coord-select-bg) outline outline-(--coord-select-line)";
/** 진행 단계 번호 */
const CIRCLED = ["①", "②", "③", "④", "⑤"];

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
  // 3차 피드백: 처음엔 아무 문장도 고르지 않는다 (-1). 같은 문장을 다시 누르면 선택 해제
  const selectedIndex = sentences.findIndex((s) => s.sentenceId === selectedId);
  const selected = sentences[selectedIndex] ?? null;
  const toggleSentence = (id: string) => setSelectedId((cur) => (cur === id ? null : id));
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

  return (
    // 리뉴얼 documentPage: 목록으로 · 제목 · 5단계 막대 · 검토 버튼 / 왼쪽 자기소개서 전체(페이지와 함께 스크롤) · 오른쪽 문장 근거(화면에 고정)
    <div className="flex flex-col">
      {/* 2차 피드백: 배경 · 테두리 없이 아이콘과 글씨만 */}
      <div className="mb-3">
        <Link href="/coordinator/applications" className={backLink}>
          <Icon name="back" className="size-4" /> 서류 검토 목록으로
        </Link>
      </div>
      {/* 3차 피드백: 제목 · 상태 배지 · 수료생 정보를 한 줄에. 기업 · 직무는 자기소개서 카드 제목으로 옮김
          (C4 상세 API 에 제출 시각 · 마감일이 없어서 HTML 의 '마감'은 표시하지 않는다) */}
      <header className="mb-4 flex items-center justify-between gap-4">
        <h1 className="flex min-w-0 flex-wrap items-center gap-2.5 text-[26px] font-extrabold tracking-[-0.4px]">
          {student.name} 님의 지원서류 <Badge tone={docTone[status]}>{docLabels[status]}</Badge>
        </h1>
        <Link href={`/coordinator/students/${student.studentId}`} className={`${btn.base} ${btn.outline} shrink-0`}>
          <Icon name="user" className="size-4" /> 수료생 정보
        </Link>
      </header>

      {/* 5단계 막대 (리뉴얼 .stepped-progress-bar) */}
      <ol className="mb-5 flex overflow-hidden rounded-[6px] border border-(--coord-line) bg-(--coord-surface)" aria-label="서류 진행 단계">
        {STEPS.map((label, i) => {
          const { done, current } = STAGE[status];
          const rejectedHere = status === "REJECTED" && i === current;
          return (
            <li
              key={label}
              aria-current={i === current || (current === null && i === done - 1) ? "step" : undefined}
              className={`flex flex-1 items-center justify-center gap-2 border-r border-(--coord-line) px-3.5 py-3.5 text-center text-[16px] font-bold last:border-r-0 ${
                rejectedHere
                  ? "bg-(--coord-red-soft) text-(--coord-red)"
                  : i === current
                    ? "bg-(--coord-forest) text-(--coord-surface)"
                    : i < done
                      ? "bg-(--coord-tint) text-(--coord-green-ink)"
                      : "bg-(--coord-head) text-(--coord-muted)"
              }`}
            >
              {i < done && <span aria-label="완료">✓</span>} {CIRCLED[i]} {label}
              {rejectedHere && <span>· {docLabels.REJECTED}</span>}
            </li>
          );
        })}
      </ol>
      {/* 보완 필요는 다음 단계로 이어지지 않는 끝 상태라 따로 알린다 (자동 연락 없음) */}
      {status === "REJECTED" && (
        <p role="note" className="-mt-2 mb-5 rounded-[6px] border border-(--coord-red-line) bg-(--coord-red-soft) px-4 py-3 text-[14px] font-semibold text-(--coord-red)">
          보완이 필요한 서류입니다. 매니저가 수료생에게 별도로 연락하여 보완 사항을 안내합니다.
        </p>
      )}

      {/* 1·2차 피드백: 아래 검토 막대 대신 단계 막대 아래 오른쪽에 버튼만 (두 카드가 같은 선에서 시작하도록 카드 위 한 줄에 둔다).
          현재 서버 상태에 맞는 버튼만 보인다 (보완 필요 · 전달 완료 상태는 제목 옆 배지로 확인) */}
      {(status === ALLOWED.APPROVE || status === ALLOWED.DELIVER || doneMessage) && (
        <div className="mb-4 flex items-center justify-end gap-3">
          {doneMessage && (
            <div role="status" className="mr-auto min-w-0 rounded-[6px] border border-(--coord-green-line) bg-(--coord-tint) px-4 py-2.5 text-[14px]">
              <b className="text-(--coord-green-ink)">{doneMessage}</b>
              {IS_MOCK && <span className="ml-2 text-[13px] font-semibold text-(--coord-attention)">{MOCK_SAVE_NOTE}</span>}
              <Link href="/coordinator/applications" className="ml-2 text-[13.5px] text-(--coord-muted) underline underline-offset-2">
                서류 검토 목록으로 돌아가기
              </Link>
            </div>
          )}
          <div className="flex shrink-0 gap-2.5">
            {status === ALLOWED.APPROVE && (
              // 서버는 상태만 보지만, 빈 서류를 승인하지 않도록 서류가 있을 때만 켠다
              <button
                type="button"
                disabled={!document}
                title={document ? undefined : "서류 본문이 없어 승인할 수 없어요"}
                onClick={() => setConfirming("APPROVE")}
                className={`${btn.base} ${btn.primary} px-5! py-2.5! text-[15.5px]!`}
              >
                {ACTION.APPROVE.button}
              </button>
            )}
            {status === ALLOWED.REJECT && (
              <button type="button" onClick={() => setConfirming("REJECT")} className={`${btn.base} ${btn.danger} px-5! py-2.5! text-[15.5px]!`}>
                <Icon name="pencil" className="size-4" />
                {ACTION.REJECT.button}
              </button>
            )}
            {status === ALLOWED.DELIVER && (
              <button
                type="button"
                disabled={!document}
                onClick={() => setConfirming("DELIVER")}
                className={`${btn.base} ${btn.primary} px-5! py-2.5! text-[15.5px]!`}
              >
                {ACTION.DELIVER.button}
              </button>
            )}
          </div>
        </div>
      )}

      <div className="grid grid-cols-[minmax(0,1.35fr)_minmax(0,1fr)] items-start gap-5">
        {/* 왼쪽: 자기소개서 전체 (이력서 항목은 탭) — 리뉴얼 .doc-viewer-card */}
        <section aria-label="지원서류" className="min-w-0 rounded-[8px] border border-(--coord-line) bg-(--coord-surface) px-7 py-6">
          {/* 3차 피드백: '자기소개서 | 수료생 이름 | 지원 공고명'을 한 줄 전체로. 이름은 제목과 같은 student.name, 공고명 · 회사명은 job (상세 API) */}
          <div className="mb-3">
            <p className="mb-0.5 text-[12.5px] font-bold text-(--coord-faint)">{job.companyName}</p>
            <h2 className="text-[19px] leading-[1.4] font-extrabold break-keep">
              {tab === "cover" ? "자기소개서" : "이력서 항목"}
              <span className="mx-2 font-normal text-(--coord-line-strong)">|</span>
              {student.name}
              <span className="mx-2 font-normal text-(--coord-line-strong)">|</span>
              {job.title}
            </h2>
          </div>
          {/* 근거 요약 이름표(자기소개서 탭) · 탭 전환 한 줄 */}
          <div className="mb-4 flex items-center justify-between gap-3 border-b border-(--coord-line) pb-3.5">
            {document && tab === "cover" && (
              <div className="flex flex-wrap items-center gap-2">
                <Badge tone="green">근거 자료 연결 {counts.linked}</Badge>
                <Badge tone="blue">수료생 직접 작성 {counts.user}</Badge>
                {counts.weak > 0 && <Badge tone="red">근거 확인 필요 {counts.weak}</Badge>}
              </div>
            )}
            {document && (
              <div role="tablist" aria-label="서류 보기" className="ml-auto flex shrink-0 gap-2">
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
                    className={`${btn.base} ${tab === key ? btn.primary : btn.outline} px-5! py-2.5! text-[15.5px]!`}
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
            <div className="pt-1">
              <C4DocumentSummary document={document} selectedSentenceId={null} onSelectSentence={() => {}} showCover={false} />
            </div>
          ) : (
            <>
              {document.companyInfoStatus !== "FOUND" && (
                <p className="mb-[18px] rounded-[4px] border border-(--coord-attention-line) bg-(--coord-orange-soft) px-3.5 py-3 text-[13.5px] text-(--coord-attention)">
                  <b>{COMPANY_INFO_LABEL[document.companyInfoStatus]}</b>
                  {document.companyInfoNote && `: ${document.companyInfoNote}`}
                </p>
              )}
              <article aria-label={`${job.companyName} 자기소개서`}>
                {sentences.length === 0 ? (
                  <p className="text-[14.5px] text-(--coord-muted)">이 서류에는 자기소개서 문장이 없어요. 이력서 항목 탭에서 나머지 내용을 확인해 주세요.</p>
                ) : (
                  // 3차 피드백: 문장마다 줄을 바꾸고 문장 사이를 넉넉하게
                  <div className="flex flex-col gap-3 text-[15.5px] leading-[1.9] break-keep">
                    {sentences.map((s, i) => (
                      <CoverSentence key={s.sentenceId} sentence={s} index={i} selected={i === selectedIndex} onSelect={() => toggleSentence(s.sentenceId)} />
                    ))}
                  </div>
                )}
                {verification.excludedSentenceCount > 0 && (
                  <p className="mt-5 rounded-[4px] border border-(--coord-line) bg-(--coord-head) px-3.5 py-2.5 text-[13px] text-(--coord-muted)">
                    AI가 근거를 찾지 못해 초안에서 제외한 문장 {verification.excludedSentenceCount}개
                  </p>
                )}
              </article>
            </>
          )}
        </section>

        {/* 오른쪽: 선택한 문장의 근거 — 리뉴얼 .inspector-card (자기소개서를 스크롤해도 화면 위에 고정, 길면 안에서 스크롤) */}
        <aside aria-label="문장 근거" className="sticky top-[76px] flex max-h-[calc(100vh-96px)] min-w-0 flex-col overflow-y-auto [scrollbar-width:thin]">
          <section aria-label="문장 근거" className="shrink-0 rounded-[8px] border border-(--coord-line) bg-(--coord-surface)">
            <div className="flex items-center justify-between gap-3 border-b border-(--coord-line) px-5 py-4">
              <h2 className="text-[17px] font-extrabold">문장 근거 · 출처</h2>
              {/* 왼쪽 문장과 같은 번호 · 이전/다음으로 문장을 옮기면 왼쪽 표시도 함께 바뀐다 */}
              {sentences.length > 0 && (
                <div className="flex items-center gap-1.5">
                  <button
                    type="button"
                    aria-label="이전 문장"
                    disabled={selectedIndex <= 0}
                    onClick={() => setSelectedId(sentences[selectedIndex - 1].sentenceId)}
                    className={`${btn.base} ${btn.outline} ${btn.sm} px-2! py-0.5!`}
                  >
                    &lt;
                  </button>
                  <span className="text-[13px] font-bold whitespace-nowrap">
                    문장 {selected ? selectedIndex + 1 : "–"} / {sentences.length}
                  </span>
                  <button
                    type="button"
                    aria-label="다음 문장"
                    disabled={selectedIndex >= sentences.length - 1}
                    onClick={() => setSelectedId(sentences[selectedIndex + 1].sentenceId)}
                    className={`${btn.base} ${btn.outline} ${btn.sm} px-2! py-0.5!`}
                  >
                    &gt;
                  </button>
                </div>
              )}
            </div>
            <div className="flex flex-col gap-4 p-5" aria-live="polite">
              {!document ? (
                <p className="text-[14px] text-(--coord-muted)">서류가 없어 확인할 근거가 없어요.</p>
              ) : sentences.length === 0 ? (
                <p className="text-[14px] text-(--coord-muted)">자기소개서 문장이 없어 확인할 근거가 없어요.</p>
              ) : !selected ? (
                <p className="py-6 text-center text-[14.5px] leading-[1.6] text-(--coord-muted)">
                  자기소개서에서 문장을 선택하면 근거와 출처를 확인할 수 있습니다.
                </p>
              ) : (
                <SentenceEvidence sentence={selected} />
              )}
            </div>
          </section>

        </aside>
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
            <label className="flex flex-col gap-1.5 text-[13px] font-bold">
              보완 사항 (필수)
              <textarea
                value={note}
                onChange={(e) => setNote(e.target.value)}
                rows={4}
                placeholder="예: 프로젝트 경험에서 본인이 담당한 역할과 성과를 구체적으로 작성하도록 안내 필요"
                className="resize-none rounded-[4px] border border-(--coord-line-strong) px-3 py-2.5 text-[14.5px] font-normal text-(--coord-ink) focus-visible:outline-2 focus-visible:outline-(--coord-green)"
              />
            </label>
          )}
        </ConfirmDialog>
      )}
    </div>
  );
}

/**
 * 자기소개서 한 문장 (3차 피드백): 번호 원 · 상태 색 없이 글만. 문장마다 한 줄씩, 고른 문장만 강조.
 * <button> 은 줄 단위로 감싸지지 않아(한 덩어리 상자) span 으로 만들고 키보드(Enter · Space)도 받는다.
 * box-decoration-clone 으로 여러 줄로 넘어가도 줄마다 배경 · 테두리가 이어진다. 문장 안 줄바꿈은 그대로(whitespace-pre-line)
 */
function CoverSentence({ sentence, index, selected, onSelect }: { sentence: Sentence; index: number; selected: boolean; onSelect: () => void }) {
  return (
    <p>
      <span
        role="button"
        tabIndex={0}
        onClick={onSelect}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            onSelect();
          }
        }}
        aria-pressed={selected}
        className={`-mx-1 cursor-pointer rounded-[4px] box-decoration-clone px-1 py-0.5 whitespace-pre-line transition-colors focus-visible:outline-3 focus-visible:outline-(--coord-green) ${
          selected ? SELECTED_LOOK : "hover:bg-(--coord-head)"
        }`}
      >
        <span className="sr-only">문장 {index + 1}: </span>
        {sentence.text}
      </span>
    </p>
  );
}

/** 오른쪽 '문장 근거 · 출처' 본문 (리뉴얼 .quote-box → 01 원본 기록 → 02 근거 확인 안내: 문장 상태별 고정 안내문, AI 검증 결과 아님) */
function SentenceEvidence({ sentence }: { sentence: Sentence }) {
  const state = sentenceState(sentence);
  const writer = WRITER[sentence.source] ?? (["작성 주체 확인 불가", "gray"] as [string, CoordTone]);
  return (
    <>
      {/* 선택한 문장: 왼쪽 선택 문장과 같은 하늘색 강조. 작성 주체 라벨과 근거 검증 상태 라벨은 따로 보인다 */}
      <div className="rounded-[4px] border border-(--coord-select-line) bg-(--coord-select-bg) px-3.5 py-3 text-[14.5px] leading-[1.55]">
        <p className="mb-1.5 flex flex-wrap items-center gap-1.5">
          <Badge tone={writer[1]} small>
            {writer[0]}
          </Badge>
          {state !== "user" && (
            <Badge tone={STATE_BADGE[state][1]} small>
              {STATE_BADGE[state][0]}
            </Badge>
          )}
        </p>
        <b className="text-(--coord-green)">&ldquo;{sentence.text}&rdquo;</b>
      </div>
      <div>
        <h3 className="mb-1.5 text-[13px] font-extrabold text-(--coord-muted)">01 원본 기록 · 출처 (등록 자료 · 기업 정보 원문)</h3>
        {sentence.evidence.length === 0 ? (
          <p className="rounded-[4px] border border-(--coord-line) bg-(--coord-head) px-3 py-2.5 text-[13.5px] text-(--coord-muted)">
            {state === "user" ? "직접 입력한 문장이라 연결된 근거가 없어요." : "연결된 근거가 없어요."}
          </p>
        ) : (
          <ol className="flex flex-col gap-2">
            {sentence.evidence.map((ev, i) => (
              <li key={`${ev.refType}-${ev.refId}-${i}`} className="rounded-[4px] border border-(--coord-line) bg-(--coord-head) px-3 py-2.5 text-[13.5px]">
                <div className="mb-0.5 flex justify-between gap-3 text-[12px] text-(--coord-faint)">
                  <b className="min-w-0">{ev.label}</b>
                  {(ev.url || ev.collectedAt) && (
                    <span className="flex shrink-0 gap-2">
                      {ev.url && (
                        <a href={ev.url} target="_blank" rel="noreferrer" className="underline underline-offset-2 hover:text-(--coord-green)">
                          출처 열기
                        </a>
                      )}
                      {ev.collectedAt && <span>{toDate(ev.collectedAt)} 수집</span>}
                    </span>
                  )}
                </div>
                <blockquote className="font-semibold break-keep text-(--coord-ink)">&ldquo;{ev.quote}&rdquo;</blockquote>
              </li>
            ))}
          </ol>
        )}
      </div>
      <div className="rounded-[4px] border border-(--coord-red-line) bg-(--coord-red-soft) px-3.5 py-3">
        <h3 className="mb-1 text-[13.5px] font-bold text-(--coord-red)">02 근거 확인 안내</h3>
        <p className="text-[13px] leading-[1.5] text-(--coord-ink)">{STATE_CHECK[state]}</p>
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
    <div className="flex flex-col items-center justify-center gap-2 border-t border-(--coord-line) px-7 py-14 text-center">
      <p className="text-[16px] font-bold">{title}</p>
      <p className="text-[14px] text-(--coord-muted)">{description}</p>
      {status === "GENERATING" && (
        <button type="button" onClick={onReload} className={`${btn.base} ${btn.outline} ${btn.sm} mt-2`}>
          다시 불러오기
        </button>
      )}
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
    // 리뉴얼 .modal-backdrop-wrap · .modal-dialog-box (머리 · 본문 · 아래 버튼 줄)
    <div className="fixed inset-0 z-30 flex items-center justify-center bg-(--coord-forest)/50 p-5 backdrop-blur-[3px]" onClick={onClose}>
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-[520px] overflow-hidden rounded-[8px] bg-(--coord-surface) shadow-[0_20px_40px_rgb(0_0_0/0.16)] outline-none"
      >
        <div className="flex items-center justify-between border-b border-(--coord-line) px-5 py-4">
          <h2 id={titleId} className="text-[18px] font-extrabold">
            {title}
          </h2>
          <button type="button" aria-label="닫기" onClick={onClose} disabled={busy} className="text-(--coord-muted) hover:text-(--coord-ink) disabled:opacity-50">
            <Icon name="close" className="size-[18px]" />
          </button>
        </div>
        <div className="flex max-h-[75vh] flex-col gap-3 overflow-y-auto p-5">
          <p className="text-[14.5px] leading-[1.6] text-(--coord-muted)">{description}</p>
          {children}
          {error && (
            <p role="alert" className="text-[13.5px] text-(--coord-red)">
              {error}
            </p>
          )}
        </div>
        <div className="flex items-center justify-end gap-2.5 border-t border-(--coord-line) bg-(--coord-head) px-5 py-3.5">
          <button type="button" onClick={onClose} disabled={busy} className={`${btn.base} ${btn.outline} ${btn.sm}`}>
            취소
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={busy || confirmDisabled}
            aria-busy={busy}
            className={`${btn.base} ${danger ? btn.danger : btn.primary} ${btn.sm}`}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
